package com.orsconsulting.orssuitepdf.signing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.orsconsulting.orssuitepdf.core.AnnotationService;
import com.orsconsulting.orssuitepdf.core.PdfDocument;
import com.orsconsulting.orssuitepdf.core.PdfOperations;

import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.SignatureTokenConnection;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;

/**
 * Regresión de la firma: comprueba que el guardado incremental preserva la
 * integridad criptográfica de una firma existente (el defecto que provocaba
 * {@code TOTAL_FAILED / HASH_FAILURE} al reescribir el PDF entero), y que un
 * guardado normal, en cambio, la rompe.
 */
class SignaturePreservationTest {

    @TempDir
    Path tempDir;

    private static final char[] PASSWORD = "test1234".toCharArray();

    private Path testKeystore() throws IOException, InterruptedException {
        Path p12 = tempDir.resolve("test.p12");
        String javaHome = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path keytool = Paths.get(javaHome, "bin", windows ? "keytool.exe" : "keytool");
        Process process = new ProcessBuilder(keytool.toString(),
                "-genkeypair", "-alias", "ors-test", "-keyalg", "RSA", "-keysize", "2048",
                "-sigalg", "SHA256withRSA", "-dname", "CN=ORS Test, O=ORS, C=ES",
                "-validity", "365", "-keystore", p12.toString(), "-storetype", "PKCS12",
                "-storepass", new String(PASSWORD), "-keypass", new String(PASSWORD))
                .redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        if (process.waitFor() != 0 || !Files.exists(p12)) {
            throw new IOException("keytool falló");
        }
        return p12;
    }

    private Path signedPdf() throws Exception {
        Path pdf = tempDir.resolve("doc.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            doc.save(pdf.toFile());
        }
        Path signed = tempDir.resolve("signed.pdf");
        try (SignatureTokenConnection token = SigningTokens.pkcs12(testKeystore(), PASSWORD)) {
            DSSPrivateKeyEntry key = token.getKeys().get(0);
            new PAdESSigner().sign(token, key,
                    new SignSpec(pdf, signed, null, "Prueba", "ES", null));
        }
        return signed;
    }

    private boolean signatureIntact(Path pdf) {
        DSSDocument document = new FileDocument(pdf.toFile());
        SignedDocumentValidator validator = SignedDocumentValidator.fromDocument(document);
        validator.setCertificateVerifier(new CommonCertificateVerifier());
        Reports reports = validator.validateDocument();
        DiagnosticData diagnostic = reports.getDiagnosticData();
        String id = diagnostic.getFirstSignatureId();
        return id != null && diagnostic.getSignatureById(id).isSignatureIntact();
    }

    @Test
    void detectsExistingSignatures() throws Exception {
        try (PdfDocument doc = PdfDocument.open(signedPdf())) {
            assertTrue(doc.hasSignatures(), "debe detectar la firma existente");
        }
    }

    @Test
    void incrementalSaveWithoutChangesKeepsSignatureValid() throws Exception {
        Path signed = signedPdf();
        Path out = tempDir.resolve("resaved-incremental.pdf");
        try (PdfDocument doc = PdfDocument.open(signed)) {
            PdfOperations.saveIncremental(doc.pdbox(), out);
        }
        assertTrue(signatureIntact(out),
                "el guardado incremental sin cambios debe conservar la firma íntegra");
    }

    @Test
    void incrementalSaveWithAnnotationKeepsSignatureValid() throws Exception {
        Path signed = signedPdf();
        Path out = tempDir.resolve("annotated-incremental.pdf");
        try (PdfDocument doc = PdfDocument.open(signed)) {
            AnnotationService.highlight(doc, 0, 40, 40, 120, 20);
            PdfOperations.saveIncremental(doc.pdbox(), out);
        }
        assertTrue(signatureIntact(out),
                "una anotación aditiva guardada incrementalmente no debe romper la firma");
    }

    @Test
    void fullRewriteBreaksSignature() throws Exception {
        Path signed = signedPdf();
        Path out = tempDir.resolve("resaved-full.pdf");
        try (PdfDocument doc = PdfDocument.open(signed)) {
            PdfOperations.save(doc.pdbox(), out);
        }
        assertFalse(signatureIntact(out),
                "un guardado normal (reescritura completa) rompe la firma; "
                        + "si esto falla, el guardado incremental ya no sería necesario");
    }
}
