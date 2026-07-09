package com.orsconsulting.orssuitepdf.signing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.SignatureTokenConnection;

class CertificateInfoTest {

    @TempDir
    Path tempDir;

    /** Genera un PKCS#12 con un DN al estilo DNIe (GIVENNAME, SURNAME, SERIALNUMBER). */
    private Path dnieLikeKeystore() throws Exception {
        Path p12 = tempDir.resolve("dnie.p12");
        String javaHome = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path keytool = Paths.get(javaHome, "bin", windows ? "keytool.exe" : "keytool");
        Process process = new ProcessBuilder(keytool.toString(),
                "-genkeypair", "-alias", "dnie", "-keyalg", "RSA", "-keysize", "2048",
                "-sigalg", "SHA256withRSA",
                "-dname", "SERIALNUMBER=IDCES-12345678Z, GIVENNAME=DAVID, "
                        + "SURNAME=MORENO ROMERO, CN=MORENO ROMERO DAVID (FIRMA), C=ES",
                "-validity", "365", "-keystore", p12.toString(), "-storetype", "PKCS12",
                "-storepass", "test1234", "-keypass", "test1234")
                .redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        if (process.waitFor() != 0 || !Files.exists(p12)) {
            throw new IOException("keytool falló");
        }
        return p12;
    }

    @Test
    void extractsNameSurnameAndNif() throws Exception {
        try (SignatureTokenConnection token =
                     SigningTokens.pkcs12(dnieLikeKeystore(), "test1234".toCharArray())) {
            DSSPrivateKeyEntry key = token.getKeys().get(0);
            CertificateInfo info = CertificateInfo.from(key);

            assertEquals("DAVID", info.givenName());
            assertEquals("MORENO ROMERO", info.surname());
            assertEquals("12345678Z", info.nif(), "el NIF debe quedar sin el prefijo IDCES-");
            assertEquals("DAVID MORENO ROMERO", info.fullName(false));
            assertEquals("MORENO ROMERO, DAVID", info.fullName(true));
        }
    }

    @Test
    void appearanceBuildsRequestedLines() {
        CertificateInfo info = new CertificateInfo("DAVID", "MORENO ROMERO", "12345678Z", "", "CN");
        SignatureAppearance app = new SignatureAppearance(
                true, "Firmado por", true, false, true, false, false, true, false);
        List<String> lines = app.buildLines(info, "Aprobación", "Madrid",
                LocalDateTime.of(2026, 7, 9, 20, 0));

        assertEquals("Firmado por", lines.get(0));
        assertEquals("DAVID MORENO ROMERO", lines.get(1));
        assertEquals("NIF: 12345678Z", lines.get(2));
        assertTrue(lines.get(3).startsWith("Fecha: 2026.07.09"));
        assertTrue(lines.stream().noneMatch(l -> l.contains("Motivo")),
                "el motivo estaba desactivado");
    }
}
