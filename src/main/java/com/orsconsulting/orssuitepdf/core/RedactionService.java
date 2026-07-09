package com.orsconsulting.orssuitepdf.core;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Redacción segura: elimina de forma irrecuperable el contenido de unas zonas
 * de una página.
 *
 * <p>Para garantizar que el contenido bajo la zona redactada no se pueda
 * recuperar, la página se <strong>rasteriza</strong> (se convierte en imagen)
 * con los rectángulos pintados en negro y se reemplaza la página original: el
 * texto y los objetos vectoriales originales dejan de existir en el PDF. Como
 * contrapartida, esa página deja de tener texto seleccionable.</p>
 */
public final class RedactionService {

    /** Resolución de rasterización de la página redactada. */
    private static final float DPI = 150f;

    private RedactionService() {
    }

    /**
     * Redacta una página. Los rectángulos vienen en puntos PDF con origen en la
     * esquina superior izquierda de la página (como los produce el visor).
     *
     * @param rects lista de {@code [x, y, width, height]} en puntos
     */
    public static void redact(PdfDocument document, int pageIndex, List<double[]> rects)
            throws IOException {
        PDDocument doc = document.pdbox();
        if (pageIndex < 0 || pageIndex >= doc.getNumberOfPages()) {
            throw new IndexOutOfBoundsException("Página fuera de rango: " + pageIndex);
        }
        if (rects == null || rects.isEmpty()) {
            return;
        }

        PDPage oldPage = doc.getPage(pageIndex);
        int rotation = oldPage.getRotation();
        PDRectangle box = oldPage.getMediaBox();
        boolean swap = rotation == 90 || rotation == 270;
        float displayWidth = swap ? box.getHeight() : box.getWidth();
        float displayHeight = swap ? box.getWidth() : box.getHeight();

        BufferedImage image = document.renderPageImage(pageIndex, DPI);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        float scale = DPI / 72f;
        for (double[] rect : rects) {
            g.fillRect(Math.round((float) rect[0] * scale), Math.round((float) rect[1] * scale),
                    Math.round((float) rect[2] * scale), Math.round((float) rect[3] * scale));
        }
        g.dispose();

        PDImageXObject xObject = LosslessFactory.createFromImage(doc, image);
        PDPage newPage = new PDPage(new PDRectangle(displayWidth, displayHeight));
        try (PDPageContentStream content = new PDPageContentStream(doc, newPage)) {
            content.drawImage(xObject, 0, 0, displayWidth, displayHeight);
        }
        doc.getPages().insertBefore(newPage, oldPage);
        doc.removePage(oldPage);
    }
}
