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
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;

/**
 * Validación de las firmas PAdES existentes en un PDF, con DSS. Sin listas de
 * confianza (TSL) configuradas la comprobación es local: informa del firmante,
 * el formato/nivel, la fecha y el resultado (indicación) sin cadena de
 * confianza cualificada.
 */
public final class SignatureValidationService {

    private SignatureValidationService() {
    }

    /** Información de una firma encontrada en el documento. */
    public record SignatureInfo(int index, String signedBy, String format,
                                String indication, String signingTime) {
    }

    public static List<SignatureInfo> validate(Path pdf) throws IOException {
        DSSDocument document = new FileDocument(pdf.toFile());
        SignedDocumentValidator validator = SignedDocumentValidator.fromDocument(document);
        validator.setCertificateVerifier(new CommonCertificateVerifier());
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
