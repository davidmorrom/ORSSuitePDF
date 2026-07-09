package com.orsconsulting.orssuitepdf.signing;

import java.awt.Font;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private static final int TSA_TIMEOUT_MS = 5000;

    /**
     * Autoridades de sellado de tiempo (RFC 3161) de reserva, gratuitas y
     * fiables, que se prueban en orden si la TSA indicada en el
     * {@link SignSpec} no responde. Así un TSA caído no hace que la firma
     * pierda el sello de tiempo silenciosamente.
     */
    private static final List<String> DEFAULT_FALLBACK_TSAS = List.of(
            "http://timestamp.digicert.com",
            "http://timestamp.sectigo.com",
            "http://rfc3161.ai.moda");

    private final List<String> fallbackTsas;

    /** Firmante con la lista de TSA de reserva por defecto. */
    public PAdESSigner() {
        this(DEFAULT_FALLBACK_TSAS);
    }

    /**
     * Permite fijar la lista de TSA de reserva (útil en pruebas para forzar de
     * forma determinista la degradación a PAdES-B sin depender de la red).
     */
    public PAdESSigner(List<String> fallbackTsas) {
        this.fallbackTsas = List.copyOf(fallbackTsas);
    }

    /**
     * Firma con el token y la clave indicados. Nunca lanza por falta de red:
     * en ese caso degrada a PAdES-B. Solo lanza {@link SigningException} ante
     * errores no recuperables.
     *
     * <p>La clave se usa <strong>una sola vez</strong> ({@code token.sign}),
     * de modo que con DNIe/tarjeta el PIN se pide una única vez aunque haya que
     * probar varias TSA: el valor de firma se reutiliza para ensamblar el
     * documento con cada TSA candidata y, si todas fallan, en nivel B.</p>
     */
    public SignResult sign(SignatureTokenConnection token, DSSPrivateKeyEntry key, SignSpec spec)
            throws SigningException {
        try {
            DSSDocument document = new FileDocument(spec.pdf().toFile());
            PAdESSignatureParameters parameters = buildParameters(key, spec);
            CertificateVerifier verifier = new CommonCertificateVerifier();
            PAdESService service = new PAdESService(verifier);

            // El valor de firma se calcula para un nivel concreto; DSS no acepta
            // reutilizar el de B para ensamblar en T. Por eso, si se pide sello,
            // se firma en T y solo si TODAS las TSA fallan se recalcula para B.
            if (spec.requestsTimestamp()) {
                parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_T);
                ToBeSigned dataToSign = service.getDataToSign(document, parameters);
                SignatureValue signatureValue =
                        token.sign(dataToSign, parameters.getDigestAlgorithm(), key);
                for (String tsa : candidateTsas(spec)) {
                    try {
                        service.setTspSource(new OnlineTSPSource(tsa, timeoutLoader()));
                        // Mismo nivel T y mismo valor de firma en cada intento:
                        // reutilizarlo entre TSA es válido (el sello es un
                        // atributo no firmado), así que el PIN se pidió una vez.
                        DSSDocument signed = service.signDocument(document, parameters, signatureValue);
                        signed.save(spec.output().toString());
                        return new SignResult(spec.output(), true);
                    } catch (Exception timestampFailure) {
                        // TSA caída o sin red: se prueba la siguiente.
                    }
                }
                // Ninguna TSA respondió: se degrada a B (nueva firma en nivel B).
            }

            parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_B);
            ToBeSigned dataToSign = service.getDataToSign(document, parameters);
            SignatureValue signatureValue =
                    token.sign(dataToSign, parameters.getDigestAlgorithm(), key);
            DSSDocument signed = service.signDocument(document, parameters, signatureValue);
            signed.save(spec.output().toString());
            return new SignResult(spec.output(), false);

        } catch (Exception ex) {
            throw new SigningException("Error al firmar: " + ex.getMessage(), ex);
        }
    }

    /** TSA a probar en orden: la indicada por el usuario primero, luego las de reserva. */
    private List<String> candidateTsas(SignSpec spec) {
        List<String> tsas = new ArrayList<>();
        if (spec.tsaUrl() != null && !spec.tsaUrl().isBlank()) {
            tsas.add(spec.tsaUrl().trim());
        }
        for (String tsa : fallbackTsas) {
            if (!tsas.contains(tsa)) {
                tsas.add(tsa);
            }
        }
        return tsas;
    }

    private PAdESSignatureParameters buildParameters(DSSPrivateKeyEntry key, SignSpec spec) {
        PAdESSignatureParameters parameters = new PAdESSignatureParameters();
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
        return parameters;
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
        text.setFont(new DSSJavaFont(Font.SANS_SERIF, Font.PLAIN, 9));
        text.setTextColor(new java.awt.Color(0x1C, 0x3C, 0x72)); // azul de marca
        text.setBackgroundColor(java.awt.Color.WHITE);
        text.setPadding(6f);
        text.setSignerTextHorizontalAlignment(
                eu.europa.esig.dss.enumerations.SignerTextHorizontalAlignment.LEFT);
        image.setTextParameters(text);
        image.setBackgroundColor(java.awt.Color.WHITE);
        return image;
    }

    private String defaultText(DSSPrivateKeyEntry key) {
        CertificateInfo info = CertificateInfo.from(key);
        return SignatureAppearance.defaults().buildText(info, null, null, LocalDateTime.now());
    }

    /** Nombre legible del firmante (para elegir en una lista de certificados). */
    public static String commonName(DSSPrivateKeyEntry key) {
        CertificateInfo info = CertificateInfo.from(key);
        String name = info.fullName(false);
        return name != null && !name.isBlank() ? name : info.commonName();
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
