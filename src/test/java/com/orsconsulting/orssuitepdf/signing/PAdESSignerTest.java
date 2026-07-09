package com.orsconsulting.orssuitepdf.signing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.SignatureTokenConnection;

/**
 * Verifica la tubería de firma con un certificado autofirmado de PRUEBA
 * generado al vuelo. Nunca usa el certificado personal real.
 */
class PAdESSignerTest {

    @TempDir
    Path tempDir;

    private static final char[] PASSWORD = "test1234".toCharArray();

    private Path testKeystore() throws IOException, InterruptedException {
        Path p12 = tempDir.resolve("test.p12");
        String javaHome = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path keytool = Paths.get(javaHome, "bin", windows ? "keytool.exe" : "keytool");
        Process process = new ProcessBuilder(
                keytool.toString(),
                "-genkeypair", "-alias", "ors-test",
                "-keyalg", "RSA", "-keysize", "2048", "-sigalg", "SHA256withRSA",
                "-dname", "CN=ORS Test, O=ORS Consulting, C=ES",
                "-validity", "365",
                "-keystore", p12.toString(), "-storetype", "PKCS12",
                "-storepass", new String(PASSWORD), "-keypass", new String(PASSWORD))
                .redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        if (process.waitFor() != 0 || !Files.exists(p12)) {
            throw new IOException("keytool no pudo generar el keystore de prueba");
        }
        return p12;
    }

    private Path samplePdf() throws IOException {
        Path out = tempDir.resolve("doc.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            doc.save(out.toFile());
        }
        return out;
    }

    private int signatureCount(Path pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            return doc.getSignatureDictionaries().size();
        }
    }

    @Test
    void signsOfflineWithBaselineBWhenNoTsa() throws Exception {
        Path keystore = testKeystore();
        Path output = tempDir.resolve("signed.pdf");
        try (SignatureTokenConnection token = SigningTokens.pkcs12(keystore, PASSWORD)) {
            DSSPrivateKeyEntry key = token.getKeys().get(0);
            SignSpec spec = new SignSpec(samplePdf(), output, null, "Prueba", "Madrid", null);
            SignResult result = new PAdESSigner().sign(token, key, spec);
            assertFalse(result.timestamped(), "sin TSA debe caer a PAdES-B");
        }
        assertTrue(signatureCount(output) >= 1, "debe haber una firma");
    }

    @Test
    void fallsBackToBaselineBWhenAllTsasUnreachable() throws Exception {
        Path keystore = testKeystore();
        Path output = tempDir.resolve("fallback.pdf");
        try (SignatureTokenConnection token = SigningTokens.pkcs12(keystore, PASSWORD)) {
            DSSPrivateKeyEntry key = token.getKeys().get(0);
            SignSpec spec = new SignSpec(samplePdf(), output,
                    "http://tsa.invalid.local/tsr", "Prueba", "Madrid", null);
            // Sin TSA de reserva alcanzable, la firma debe degradar a PAdES-B.
            SignResult result = new PAdESSigner(java.util.List.of()).sign(token, key, spec);
            assertFalse(result.timestamped(),
                    "si ninguna TSA responde, debe degradar a PAdES-B");
        }
        assertTrue(Files.exists(output), "aun sin sello, debe generarse el PDF firmado");
    }

    @Test
    void signsWithVisibleSignature() throws Exception {
        Path keystore = testKeystore();
        Path output = tempDir.resolve("visible.pdf");
        try (SignatureTokenConnection token = SigningTokens.pkcs12(keystore, PASSWORD)) {
            DSSPrivateKeyEntry key = token.getKeys().get(0);
            VisibleSignature visible = new VisibleSignature(0, 50, 50, 220, 70, "Firma de prueba");
            SignSpec spec = new SignSpec(samplePdf(), output, null, "Prueba", "Madrid", visible);
            new PAdESSigner().sign(token, key, spec);
        }
        assertTrue(signatureCount(output) >= 1, "la firma visible también debe embeber la firma");
    }
}
