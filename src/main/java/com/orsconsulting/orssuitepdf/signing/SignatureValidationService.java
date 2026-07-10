package com.orsconsulting.orssuitepdf.signing;

import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.service.crl.OnlineCRLSource;
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.spi.x509.aia.DefaultAIASource;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;

/**
 * Validación de las firmas PAdES existentes en un PDF, con DSS. Usa las listas
 * de confianza europeas ({@link TrustService}) como anclas de confianza y
 * fuentes de revocación online (OCSP/CRL) con descubrimiento de intermedios por
 * AIA, de modo que las firmas de certificados cualificados (FNMT, DNIe…) se
 * validan con cadena de confianza real. Sin caché de confianza ni conexión, la
 * comprobación sigue funcionando pero sin anclar la cadena.
 */
public final class SignatureValidationService {

    private SignatureValidationService() {
    }

    private static CommonCertificateVerifier buildVerifier() {
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        try {
            verifier.setTrustedCertSources(TrustService.trustedSource());
            verifier.setAIASource(new DefaultAIASource());
            verifier.setOcspSource(new OnlineOCSPSource());
            OnlineCRLSource crlSource = new OnlineCRLSource();
            crlSource.setDataLoader(new CommonsDataLoader());
            verifier.setCrlSource(crlSource);
        } catch (Exception ex) {
            // Ante cualquier problema al configurar confianza/revocación, se
            // valida de forma local (sin cadena de confianza) en vez de fallar.
        }
        return verifier;
    }

    /** Información de una firma encontrada en el documento. */
    public record SignatureInfo(int index, String signedBy, String format,
                                String indication, String signingTime) {
    }

    public static List<SignatureInfo> validate(Path pdf) throws IOException {
        DSSDocument document = new FileDocument(pdf.toFile());
        SignedDocumentValidator validator = SignedDocumentValidator.fromDocument(document);
        validator.setCertificateVerifier(buildVerifier());
        Reports reports = validator.validateDocument();
        SimpleReport report = reports.getSimpleReport();

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        List<SignatureInfo> signatures = new ArrayList<>();
        int index = 1;
        for (String id : report.getSignatureIdList()) {
            String signedBy = report.getSignedBy(id);
            SignatureLevel format = report.getSignatureFormat(id);
            Indication indication = report.getIndication(id);
            Date signingTime = report.getSigningTime(id);
            signatures.add(new SignatureInfo(index++,
                    signedBy != null ? signedBy : "(desconocido)",
                    format != null ? format.toString() : "—",
                    indication != null ? indication.toString() : "—",
                    signingTime != null ? dateFormat.format(signingTime) : "—"));
        }
        return signatures;
    }
}
