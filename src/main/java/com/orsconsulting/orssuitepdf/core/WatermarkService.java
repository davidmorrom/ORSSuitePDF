package com.orsconsulting.orssuitepdf.core;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

/**
 * Marca de agua de texto (diagonal, translúcida) y numeración de páginas,
 * aplicadas a todo el documento con PDFBox.
 */
public final class WatermarkService {

    private WatermarkService() {
    }

    /** Añade una marca de agua diagonal translúcida en el centro de cada página. */
    public static void watermark(PdfDocument document, String text) throws IOException {
        PDDocument doc = document.pdbox();
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        float fontSize = 60f;
        double angle = Math.toRadians(45);

        for (PDPage page : doc.getPages()) {
            PDRectangle box = page.getMediaBox();
            float cx = box.getLowerLeftX() + box.getWidth() / 2;
            float cy = box.getLowerLeftY() + box.getHeight() / 2;
            float textWidth = font.getStringWidth(text) / 1000f * fontSize;

            PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
            gs.setNonStrokingAlphaConstant(0.12f);

            try (PDPageContentStream content = new PDPageContentStream(
                    doc, page, AppendMode.APPEND, true, true)) {
                content.setGraphicsStateParameters(gs);
                content.setNonStrokingColor(0.4f, 0.4f, 0.4f);
                content.beginText();
                content.setFont(font, fontSize);
                float startX = cx - (float) (Math.cos(angle) * textWidth / 2);
                float startY = cy - (float) (Math.sin(angle) * textWidth / 2);
                content.setTextMatrix(Matrix.getRotateInstance(angle, startX, startY));
                content.showText(text);
                content.endText();
            }
        }
    }

    /** Numera las páginas al pie, centrado, con el formato "n / total". */
    public static void numberPages(PdfDocument document) throws IOException {
        PDDocument doc = document.pdbox();
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float fontSize = 10f;
        int total = doc.getNumberOfPages();

        for (int i = 0; i < total; i++) {
            PDPage page = doc.getPage(i);
            PDRectangle box = page.getMediaBox();
            String label = (i + 1) + " / " + total;
            float textWidth = font.getStringWidth(label) / 1000f * fontSize;
            float x = box.getLowerLeftX() + (box.getWidth() - textWidth) / 2;
            float y = box.getLowerLeftY() + 22;

            try (PDPageContentStream content = new PDPageContentStream(
                    doc, page, AppendMode.APPEND, true, true)) {
                content.beginText();
                content.setFont(font, fontSize);
                content.setNonStrokingColor(0.2f, 0.2f, 0.2f);
                content.newLineAtOffset(x, y);
                content.showText(label);
                content.endText();
            }
        }
    }
}
