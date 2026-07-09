package com.orsconsulting.orssuitepdf.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javafx.scene.image.Image;

/**
 * Documento PDF abierto en memoria. Envuelve el {@link PDDocument} de PDFBox
 * y ofrece el renderizado de páginas a imágenes de JavaFX.
 *
 * <p>No es seguro para uso concurrente: el renderizado de PDFBox no es
 * reentrante sobre el mismo documento, por lo que las peticiones de render
 * deben serializarse (ver {@code ui.PdfView}).</p>
 */
public final class PdfDocument implements Closeable {

    private final PDDocument document;
    private final PDFRenderer renderer;
    private final Path source;

    private PdfDocument(PDDocument document, Path source) {
        this.document = document;
        this.renderer = new PDFRenderer(document);
        this.source = source;
    }

    /** Abre un PDF desde disco. */
    public static PdfDocument open(Path file) throws IOException {
        PDDocument doc = Loader.loadPDF(file.toFile());
        return new PdfDocument(doc, file);
    }

    /** Abre un PDF protegido con contraseña. */
    public static PdfDocument open(Path file, String password) throws IOException {
        PDDocument doc = Loader.loadPDF(file.toFile(), password);
        return new PdfDocument(doc, file);
    }

    /** Abre un PDF desde disco, envolviendo el {@link File}. */
    public static PdfDocument open(File file) throws IOException {
        return open(file.toPath());
    }

    /** Número de páginas del documento. */
    public int pageCount() {
        return document.getNumberOfPages();
    }

    /** Ruta de la que se cargó el documento (puede ser {@code null}). */
    public Path source() {
        return source;
    }

    /** Acceso al modelo PDFBox subyacente para operaciones estructurales. */
    public PDDocument pdbox() {
        return document;
    }

    /**
     * {@code true} si el documento contiene al menos una firma digital. En ese
     * caso el guardado debe ser incremental (append-only) para no romper el
     * {@code ByteRange} firmado — ver {@link PdfOperations#saveIncremental}.
     */
    public boolean hasSignatures() {
        return !document.getSignatureDictionaries().isEmpty();
    }

    /** Anchura de la página (cropBox) en puntos. */
    public float pageWidth(int pageIndex) {
        return document.getPage(pageIndex).getCropBox().getWidth();
    }

    /** Altura de la página (cropBox) en puntos. */
    public float pageHeight(int pageIndex) {
        return document.getPage(pageIndex).getCropBox().getHeight();
    }

    /**
     * Renderiza una página a una imagen de JavaFX.
     *
     * @param pageIndex índice de página en base 0
     * @param dpi       resolución de render (96 ≈ tamaño natural en pantalla)
     */
    public Image renderPage(int pageIndex, float dpi) throws IOException {
        var buffered = renderPageImage(pageIndex, dpi);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(buffered, "png", buffer);
        return new Image(new ByteArrayInputStream(buffer.toByteArray()));
    }

    /**
     * Renderiza una página a un {@link java.awt.image.BufferedImage}, útil para
     * procesos que trabajan con imágenes AWT (p. ej. OCR).
     */
    public synchronized java.awt.image.BufferedImage renderPageImage(int pageIndex, float dpi)
            throws IOException {
        if (pageIndex < 0 || pageIndex >= pageCount()) {
            throw new IndexOutOfBoundsException("Página fuera de rango: " + pageIndex);
        }
        return renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
    }

    @Override
    public void close() throws IOException {
        document.close();
    }
}
