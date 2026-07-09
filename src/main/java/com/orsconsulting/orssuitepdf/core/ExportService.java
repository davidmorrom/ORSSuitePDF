package com.orsconsulting.orssuitepdf.core;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Exportación de un PDF a otros formatos.
 *
 * <ul>
 *   <li><strong>Texto e imágenes</strong>: nativo, 100% offline (PDFBox).</li>
 *   <li><strong>Formatos ofimáticos</strong> (DOCX, PPTX, ODT, RTF): mediante
 *       LibreOffice en modo headless, si está instalado. Es software libre y
 *       se ejecuta localmente; ver ADR-004.</li>
 * </ul>
 */
public final class ExportService {

    private ExportService() {
    }

    /** Extrae el texto del documento a un fichero {@code .txt} (UTF-8). */
    public static void exportText(PdfDocument document, Path output) throws IOException {
        String text = new PDFTextStripper().getText(document.pdbox());
        try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write(text);
        }
    }

    /**
     * Renderiza cada página a una imagen ({@code png}/{@code jpg}) en la carpeta
     * indicada, con nombres {@code <base>-<n>.<fmt>}. Devuelve el nº de páginas.
     */
    public static int exportImages(PdfDocument document, Path outputDir, String format,
                                   float dpi, String baseName) throws IOException {
        Files.createDirectories(outputDir);
        int pages = document.pageCount();
        for (int i = 0; i < pages; i++) {
            var image = document.renderPageImage(i, dpi);
            Path file = outputDir.resolve(baseName + "-" + (i + 1) + "." + format);
            ImageIO.write(image, format, file.toFile());
        }
        return pages;
    }

    /** Localiza el ejecutable de LibreOffice ({@code soffice}), si está instalado. */
    public static Optional<Path> findLibreOffice() {
        String env = System.getenv("LIBREOFFICE_PATH");
        if (env != null && !env.isBlank() && Files.isExecutable(Paths.get(env))) {
            return Optional.of(Paths.get(env));
        }
        List<Path> candidates = List.of(
                Paths.get("C:\\Program Files\\LibreOffice\\program\\soffice.exe"),
                Paths.get("C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe"),
                Paths.get("/usr/bin/soffice"),
                Paths.get("/usr/local/bin/soffice"),
                Paths.get("/Applications/LibreOffice.app/Contents/MacOS/soffice"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** {@code true} si hay soporte de conversión ofimática (LibreOffice presente). */
    public static boolean isOfficeConversionAvailable() {
        return findLibreOffice().isPresent();
    }

    /**
     * Convierte el PDF a un formato ofimático con LibreOffice headless.
     *
     * @param inputPdf     PDF de origen (fichero en disco)
     * @param targetFormat extensión de destino: {@code docx}, {@code pptx},
     *                     {@code odt}, {@code rtf}…
     * @param outputDir    carpeta de salida
     * @return ruta del fichero generado
     */
    public static Path convertWithLibreOffice(Path inputPdf, String targetFormat, Path outputDir)
            throws IOException, InterruptedException {
        Path soffice = findLibreOffice().orElseThrow(() ->
                new IOException("LibreOffice no está instalado (no se encontró soffice)."));
        Files.createDirectories(outputDir);
        // Perfil temporal para no chocar con una instancia abierta de LibreOffice.
        Path profile = Files.createTempDirectory("ors-lo-");

        Process process = new ProcessBuilder(
                soffice.toString(),
                "-env:UserInstallation=file:///" + profile.toString().replace('\\', '/'),
                "--headless", "--norestore",
                "--convert-to", targetFormat,
                "--outdir", outputDir.toString(),
                inputPdf.toString())
                .redirectErrorStream(true)
                .start();
        process.getInputStream().readAllBytes();
        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("La conversión con LibreOffice excedió el tiempo de espera.");
        }

        String base = inputPdf.getFileName().toString().replaceFirst("(?i)\\.pdf$", "");
        Path result = outputDir.resolve(base + "." + targetFormat);
        if (!Files.exists(result)) {
            throw new IOException("LibreOffice no generó el fichero esperado: " + result.getFileName());
        }
        return result;
    }
}
