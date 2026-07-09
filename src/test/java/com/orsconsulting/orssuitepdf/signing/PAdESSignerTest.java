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

/**
 * Verifica la tubería de firma con un certificado autofirmado de PRUEBA,
 * generado al vuelo. Nunca usa el certificado personal real.
 */
class PAdESSignerTest {

    @TempDir
    Path tempDir;

    private static final char[] PASSWORD = "test1234".toCharArray();

    /** Genera un PKCS#12 autofirmado de prueba con keytool. */
    private Path testKeystore() throws IOException, InterruptedException {
        Path p12 = tempDir.resolve("test.p12");
        String javaHome = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path keytool = Paths.get(javaHome, "bin", windows ? "keytool.exe" : "keytool");

        Process process = new ProcessBuilder(
                keytool.toString(),
                "-genkeypair", "-alias", "ors-test",
                "-keyalg", "RSA", "-keysize", "2048",
                "-sigalg", "SHA256withRSA",
                "-dname", "CN=ORS Test, O=ORS Consulting, C=ES",
                "-validity", "365",
                "-keystore", p12.toString(),
                "-storetype", "PKCS12",
                "-storepass", new String(PASSWORD),
                "-keypass", new String(PASSWORD))
                .redirectErrorStream(true)
                .start();
        process.getInputStream().readAllBytes();
        int exit = process.waitFor();
        if (exit != 0 || !Files.exists(p12)) {
            throw new IOException("keytool no pudo generar el keystore de prueba (exit " + exit + ")");
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

    @Test
    void signsOfflineWithBaselineBWhenNoTsa() throws Exception {
        Path keystore = testKeystore();
        Path pdf = samplePdf();
        Path output = tempDir.resolve("signed.pdf");

        SignRequest request = new SignRequest(
                pdf, output, keystore, PASSWORD, null, "Prueba de firma", "Madrid");
        SignResult result = new PAdESSigner().sign(request);

        assertFalse(result.timestamped(), "sin TSA debe caer a PAdES-B");
        assertTrue(Files.exists(output));

        try (PDDocument signed = Loader.loadPDF(output.toFile())) {
            assertFalse(signed.getSignatureDictionaries().isEmpty(),
                    "el PDF firmado debe contener al menos una firma");
        }
    }

    @Test
    void fallsBackToBaselineBWhenTsaUnreachable() throws Exception {
        Path keystore = testKeystore();
        Path pdf = samplePdf();
        Path output = tempDir.resolve("signed-fallback.pdf");

        // TSA inexistente: el intento B-T debe fallar y degradar a B sin lanzar.
        SignRequest request = new SignRequest(
                pdf, output, keystore, PASSWORD,
                "http://tsa.invalid.local/tsr", "Prueba", "Madrid");
        SignResult result = new PAdESSigner().sign(request);

        assertFalse(result.timestamped(), "un TSA inalcanzable debe degradar a PAdES-B");
        assertTrue(Files.exists(output));
    }
}
