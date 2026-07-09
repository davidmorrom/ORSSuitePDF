package com.orsconsulting.orssuitepdf.signing;

import java.awt.Font;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.pades.DSSJavaFont;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.SignatureFieldParameters;
import eu.europa.esig.dss.pades.SignatureImageParameters;
import eu.europa.esig.dss.pades.SignatureImageTextParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.SignatureTokenConnection;
import eu.europa.esig.dss.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;

/**
 * Firma documentos PDF en formato PAdES con DSS, aplicando la estrategia de
 * fallback online/offline del ADR-002:
 *
 * <ul>
 *   <li>Si se indica una TSA, intenta <strong>PAdES-B-T</strong> (con sello de
 *       tiempo) con un timeout corto.</li>
 *   <li>Si no hay conexión o el TSA falla, cae automáticamente a
 *       <strong>PAdES-B</strong> sin bloquear.</li>
 * </ul>
 *
 * <p>El origen del certificado (fichero, almacén de Windows o DNIe) es
 * indiferente: se recibe ya como {@link SignatureTokenConnection} y la clave
 * seleccionada. Este es el único punto de la aplicación que hace red.</p>
 */
public final class PAdESSigner {

    /** Timeout corto para el TSA (ms), según ADR-002 (3–5 s). */
    private static final int TSA_TIMEOUT_MS = 4000;

    /**
     * Firma con el token y la clave indicados. Nunca lanza por falta de red:
     * en ese caso degrada a PAdES-B. Solo lanza {@link SigningException} ante
     * errores no recuperables.
     */
    public SignResult sign(SignatureTokenConnection token, DSSPrivateKeyEntry key, SignSpec spec)
            throws SigningException {
        try {
            DSSDocument document = new FileDocument(spec.pdf().toFile());

            if (spec.requestsTimestamp()) {
                try {
                    DSSDocument signed = doSign(document, key, token,
                            SignatureLevel.PAdES_BASELINE_T, spec);
                    signed.save(spec.output().toString());
                    return new SignResult(spec.output(), true);
                } catch (Exception timestampFailure) {
                    // Sin conexión o TSA no disponible: se degrada a B (offline).
                }
            }

            DSSDocument signed = doSign(document, key, token,
                    SignatureLevel.PAdES_BASELINE_B, spec);
            signed.save(spec.output().toString());
            return new SignResult(spec.output(), false);

        } catch (Exception ex) {
            throw new SigningException("Error al firmar: " + ex.getMessage(), ex);
        }
    }

    private DSSDocument doSign(DSSDocument document, DSSPrivateKeyEntry key,
                               SignatureTokenConnection token, SignatureLevel level,
                               SignSpec spec) {
        PAdESSignatureParameters parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(level);
        parameters.setSigningCertificate(key.getCertificate());
        parameters.setCertificateChain(key.getCertificateChain());
        if (spec.reason() != null && !spec.reason().isBlank()) {
            parameters.setReason(spec.reason());
        }
        if (spec.location() != null && !spec.location().isBlank()) {
            parameters.setLocation(spec.location());
        }
        if (spec.visible() != null) {
            parameters.setImageParameters(buildImageParameters(spec.visible(), key));
        }

        CertificateVerifier verifier = new CommonCertificateVerifier();
        PAdESService service = new PAdESService(verifier);
        if (level == SignatureLevel.PAdES_BASELINE_T) {
            service.setTspSource(new OnlineTSPSource(spec.tsaUrl(), timeoutLoader()));
        }

        ToBeSigned dataToSign = service.getDataToSign(document, parameters);
        SignatureValue signatureValue = token.sign(dataToSign, parameters.getDigestAlgorithm(), key);
        return service.signDocument(document, parameters, signatureValue);
    }

    private SignatureImageParameters buildImageParameters(VisibleSignature visible,
                                                          DSSPrivateKeyEntry key) {
        SignatureImageParameters image = new SignatureImageParameters();

        SignatureFieldParameters field = new SignatureFieldParameters();
        field.setPage(visible.page() + 1); // DSS numera las páginas desde 1
        field.setOriginX(visible.x());
        field.setOriginY(visible.y());
        field.setWidth(visible.width());
        field.setHeight(visible.height());
        image.setFieldParameters(field);

        SignatureImageTextParameters text = new SignatureImageTextParameters();
        text.setText(visible.text() != null ? visible.text() : defaultText(key));
        text.setFont(new DSSJavaFont(Font.SANS_SERIF, Font.PLAIN, 8));
        image.setTextParameters(text);
        return image;
    }

    private String defaultText(DSSPrivateKeyEntry key) {
        String signer = commonName(key);
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        return "Firmado digitalmente por:\n" + signer + "\n" + date;
    }

    /** Nombre común (CN) del certificado, o el DN completo si no se encuentra. */
    public static String commonName(DSSPrivateKeyEntry key) {
        X509Certificate certificate = key.getCertificate().getCertificate();
        String dn = certificate.getSubjectX500Principal().getName();
        for (String part : dn.split(",")) {
            String token = part.trim();
            if (token.regionMatches(true, 0, "CN=", 0, 3)) {
                return token.substring(3);
            }
        }
        return dn;
    }

    private CommonsDataLoader timeoutLoader() {
        CommonsDataLoader loader = new CommonsDataLoader();
        loader.setTimeoutConnection(TSA_TIMEOUT_MS);
        loader.setTimeoutConnectionRequest(TSA_TIMEOUT_MS);
        loader.setTimeoutResponse(TSA_TIMEOUT_MS);
        loader.setTimeoutSocket(TSA_TIMEOUT_MS);
        return loader;
    }
}
