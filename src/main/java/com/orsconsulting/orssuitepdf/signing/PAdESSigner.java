package com.orsconsulting.orssuitepdf.signing;

import java.io.IOException;
import java.security.KeyStore;
import java.util.List;

import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;

/**
 * Firma documentos PDF en formato PAdES con DSS, aplicando la estrategia de
 * fallback online/offline descrita en el ADR-002:
 *
 * <ul>
 *   <li>Si se indica una TSA, intenta <strong>PAdES-B-T</strong> (firma con
 *       sello de tiempo) con un timeout corto.</li>
 *   <li>Si no hay conexión o el TSA falla, cae automáticamente a
 *       <strong>PAdES-B</strong> (firma electrónica avanzada sin sello), sin
 *       bloquear ni propagar la excepción de red.</li>
 * </ul>
 *
 * <p>Este es el único punto de la aplicación que realiza llamadas de red, y
 * toda excepción de red se resuelve aquí mediante el fallback.</p>
 */
public final class PAdESSigner {

    /** Timeout corto para el TSA (ms), según ADR-002 (3–5 s). */
    private static final int TSA_TIMEOUT_MS = 4000;

    /**
     * Firma el documento indicado. Nunca lanza excepción por falta de red: en
     * ese caso degrada a PAdES-B. Solo lanza {@link SigningException} ante
     * errores no recuperables (certificado inválido, contraseña incorrecta,
     * E/S del documento…).
     */
    public SignResult sign(SignRequest request) throws SigningException {
        try (Pkcs12SignatureToken token = new Pkcs12SignatureToken(
                request.keystore().toFile(),
                new KeyStore.PasswordProtection(request.password()))) {

            DSSPrivateKeyEntry key = firstKey(token);
            DSSDocument document = new FileDocument(request.pdf().toFile());

            if (request.requestsTimestamp()) {
                try {
                    DSSDocument signed = doSign(document, key, token,
                            SignatureLevel.PAdES_BASELINE_T, request);
                    signed.save(request.output().toString());
                    return new SignResult(request.output(), true);
                } catch (Exception timestampFailure) {
                    // Sin conexión o TSA no disponible: se degrada a B (offline).
                }
            }

            DSSDocument signed = doSign(document, key, token,
                    SignatureLevel.PAdES_BASELINE_B, request);
            signed.save(request.output().toString());
            return new SignResult(request.output(), false);

        } catch (SigningException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new SigningException("No se pudo leer el certificado o el documento: "
                    + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new SigningException("Error al firmar: " + ex.getMessage(), ex);
        }
    }

    private DSSDocument doSign(DSSDocument document, DSSPrivateKeyEntry key,
                               Pkcs12SignatureToken token, SignatureLevel level,
                               SignRequest request) {
        PAdESSignatureParameters parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(level);
        parameters.setSigningCertificate(key.getCertificate());
        parameters.setCertificateChain(key.getCertificateChain());
        if (request.reason() != null && !request.reason().isBlank()) {
            parameters.setReason(request.reason());
        }
        if (request.location() != null && !request.location().isBlank()) {
            parameters.setLocation(request.location());
        }

        CertificateVerifier verifier = new CommonCertificateVerifier();
        PAdESService service = new PAdESService(verifier);
        if (level == SignatureLevel.PAdES_BASELINE_T) {
            service.setTspSource(new OnlineTSPSource(request.tsaUrl(), timeoutLoader()));
        }

        ToBeSigned dataToSign = service.getDataToSign(document, parameters);
        SignatureValue signatureValue = token.sign(dataToSign, parameters.getDigestAlgorithm(), key);
        return service.signDocument(document, parameters, signatureValue);
    }

    private CommonsDataLoader timeoutLoader() {
        CommonsDataLoader loader = new CommonsDataLoader();
        loader.setTimeoutConnection(TSA_TIMEOUT_MS);
        loader.setTimeoutConnectionRequest(TSA_TIMEOUT_MS);
        loader.setTimeoutResponse(TSA_TIMEOUT_MS);
        loader.setTimeoutSocket(TSA_TIMEOUT_MS);
        return loader;
    }

    private DSSPrivateKeyEntry firstKey(Pkcs12SignatureToken token) throws SigningException {
        List<DSSPrivateKeyEntry> keys = token.getKeys();
        if (keys.isEmpty()) {
            throw new SigningException("El certificado no contiene ninguna clave privada utilizable");
        }
        return keys.get(0);
    }
}
