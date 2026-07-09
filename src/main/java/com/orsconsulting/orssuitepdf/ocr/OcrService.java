package com.orsconsulting.orssuitepdf.ocr;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.orsconsulting.orssuitepdf.core.PdfDocument;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

/**
 * Reconocimiento óptico de caracteres (OCR) sobre páginas de PDF, con
 * Tesseract vía Tess4J. Los binarios nativos de Tesseract viajan dentro de
 * Tess4J, por lo que el OCR funciona sin conexión ni instalaciones externas;
 * solo se necesitan los datos de idioma (tessdata).
 */
public final class OcrService {

    /** Resolución de render para OCR (300 DPI ofrece buena precisión). */
    private static final float OCR_DPI = 300f;

    /** Idiomas por defecto (español + inglés). */
    public static final String DEFAULT_LANGUAGES = "spa+eng";

    private OcrService() {
    }

    /**
     * Resuelve la carpeta de datos de idioma: la variable de entorno
     * {@code TESSDATA_PREFIX} si está definida, o la carpeta {@code tessdata}
     * del directorio de trabajo en caso contrario.
     */
    public static Path defaultDataPath() {
        // 1) Propiedad de sistema (la fija el empaquetado con -Dtessdata.dir).
        String property = System.getProperty("tessdata.dir");
        if (property != null && !property.isBlank()) {
            return Paths.get(property);
        }
        // 2) Variable de entorno estándar de Tesseract.
        String env = System.getenv("TESSDATA_PREFIX");
        if (env != null && !env.isBlank()) {
            return Paths.get(env);
        }
        // 3) Carpeta tessdata del directorio de trabajo (desarrollo).
        return Paths.get("tessdata");
    }

    /** {@code true} si la carpeta de datos contiene modelos de idioma. */
    public static boolean isDataAvailable(Path dataPath) {
        return dataPath != null && Files.isDirectory(dataPath)
                && dataPath.toFile().listFiles((dir, name) -> name.endsWith(".traineddata")) != null
                && dataPath.toFile().listFiles((dir, name) -> name.endsWith(".traineddata")).length > 0;
    }

    /**
     * Ejecuta OCR sobre una página y devuelve el texto reconocido.
     *
     * @param document  documento de origen
     * @param pageIndex índice de página en base 0
     * @param languages idiomas de Tesseract (p. ej. {@code "spa+eng"})
     * @param dataPath  carpeta con los {@code *.traineddata}
     */
    public static String ocrPage(PdfDocument document, int pageIndex, String languages, Path dataPath)
            throws IOException {
        if (!isDataAvailable(dataPath)) {
            throw new IOException("No se encontraron datos de idioma (tessdata) en: " + dataPath);
        }
        BufferedImage image = document.renderPageImage(pageIndex, OCR_DPI);
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(dataPath.toAbsolutePath().toString());
        tesseract.setLanguage(languages);
        try {
            return tesseract.doOCR(image).strip();
        } catch (TesseractException ex) {
            throw new IOException("Fallo de OCR: " + ex.getMessage(), ex);
        }
    }
}
