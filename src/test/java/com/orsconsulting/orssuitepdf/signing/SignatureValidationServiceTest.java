package com.orsconsulting.orssuitepdf.signing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.SignatureTokenConnection;

class SignatureValidationServiceTest {

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

    @Test
    void detectsSignatureAndSigner() throws Exception {
        List<SignatureValidationService.SignatureInfo> signatures =
                SignatureValidationService.validate(signedPdf());
        assertEquals(1, signatures.size());
        assertTrue(signatures.get(0).signedBy().contains("ORS Test"),
                "debe identificar al firmante; obtenido: " + signatures.get(0).signedBy());
    }

    @Test
    void reportsNoSignaturesForPlainPdf() throws Exception {
        Path plain = tempDir.resolve("plain.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            doc.save(plain.toFile());
        }
        assertTrue(SignatureValidationService.validate(plain).isEmpty());
    }
}
