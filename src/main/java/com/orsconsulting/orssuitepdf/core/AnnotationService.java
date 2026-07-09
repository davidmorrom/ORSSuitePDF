package com.orsconsulting.orssuitepdf.core;

import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquare;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;

/**
 * Anotaciones sobre el PDF, guardadas como anotaciones reales del documento:
 * resaltado, recuadro y nota adhesiva. Las coordenadas de entrada están en
 * puntos con origen en la esquina superior izquierda de la página (como las
 * produce el visor) y se convierten a la convención del PDF (origen inferior
 * izquierdo).
 */
public final class AnnotationService {

    private AnnotationService() {
    }

    private static PDColor rgb(float r, float g, float b) {
        return new PDColor(new float[]{r, g, b}, PDDeviceRGB.INSTANCE);
    }

    private static PDRectangle toPdfRect(PDPage page, double x, double y, double w, double h) {
        float pageHeight = page.getMediaBox().getHeight();
        return new PDRectangle((float) x, (float) (pageHeight - y - h), (float) w, (float) h);
    }

    /** Resaltado translúcido amarillo sobre una zona. */
    public static void highlight(PdfDocument document, int page, double x, double y, double w, double h)
            throws IOException {
        PDDocument doc = document.pdbox();
        PDPage pdPage = doc.getPage(page);
        PDAnnotationSquare square = new PDAnnotationSquare();
        square.setRectangle(toPdfRect(pdPage, x, y, w, h));
        square.setInteriorColor(rgb(1f, 0.94f, 0.30f));
        square.setColor(rgb(1f, 0.94f, 0.30f));
        // Opacidad constante (/CA) para que el resaltado sea translúcido.
        square.getCOSObject().setFloat(org.apache.pdfbox.cos.COSName.CA, 0.35f);
        square.constructAppearances(doc);
        pdPage.getAnnotations().add(square);
    }

    /** Recuadro con borde de color (sin relleno) sobre una zona. */
    public static void rectangle(PdfDocument document, int page, double x, double y, double w, double h)
            throws IOException {
        PDDocument doc = document.pdbox();
        PDPage pdPage = doc.getPage(page);
        PDAnnotationSquare square = new PDAnnotationSquare();
        square.setRectangle(toPdfRect(pdPage, x, y, w, h));
        square.setColor(rgb(0.85f, 0.15f, 0.15f));
        PDBorderStyleDictionary border = new PDBorderStyleDictionary();
        border.setWidth(2);
        square.setBorderStyle(border);
        square.constructAppearances(doc);
        pdPage.getAnnotations().add(square);
    }

    /** Nota adhesiva con texto en la esquina superior izquierda de la zona. */
    public static void note(PdfDocument document, int page, double x, double y, String text)
            throws IOException {
        PDPage pdPage = document.pdbox().getPage(page);
        float pageHeight = pdPage.getMediaBox().getHeight();
        PDAnnotationText note = new PDAnnotationText();
        note.setContents(text);
        note.setName(PDAnnotationText.NAME_NOTE);
        note.setColor(rgb(1f, 0.85f, 0.20f));
        note.setOpen(false);
        note.setRectangle(new PDRectangle((float) x, (float) (pageHeight - y - 18), 18, 18));
        pdPage.getAnnotations().add(note);
    }

    // Los trazos libres y las flechas se dibujan directamente en el contenido
    // de la página para que se rendericen de forma fiable en cualquier visor.

    /**
     * Dibuja un trazo a mano alzada uniendo los puntos dados (en puntos PDF con
     * origen superior izquierdo).
     */
    public static void freehand(PdfDocument document, int page, List<double[]> points,
                                float[] rgb, float width) throws IOException {
        if (points == null || points.size() < 2) {
            return;
        }
        PDDocument doc = document.pdbox();
        PDPage pdPage = doc.getPage(page);
        float ph = pdPage.getMediaBox().getHeight();
        try (PDPageContentStream cs = new PDPageContentStream(doc, pdPage, AppendMode.APPEND, true, true)) {
            cs.setStrokingColor(rgb[0], rgb[1], rgb[2]);
            cs.setLineWidth(width);
            cs.setLineCapStyle(1);
            cs.setLineJoinStyle(1);
            double[] first = points.get(0);
            cs.moveTo((float) first[0], (float) (ph - first[1]));
            for (int i = 1; i < points.size(); i++) {
                double[] p = points.get(i);
                cs.lineTo((float) p[0], (float) (ph - p[1]));
            }
            cs.stroke();
        }
    }

    /** Dibuja una flecha de {@code (x1,y1)} a {@code (x2,y2)} (origen superior izq.). */
    public static void arrow(PdfDocument document, int page, double x1, double y1,
                             double x2, double y2, float[] rgb, float width) throws IOException {
        PDDocument doc = document.pdbox();
        PDPage pdPage = doc.getPage(page);
        float ph = pdPage.getMediaBox().getHeight();
        float ax1 = (float) x1;
        float ay1 = (float) (ph - y1);
        float ax2 = (float) x2;
        float ay2 = (float) (ph - y2);
        double angle = Math.atan2(ay2 - ay1, ax2 - ax1);
        double head = 10 + width * 2;
        try (PDPageContentStream cs = new PDPageContentStream(doc, pdPage, AppendMode.APPEND, true, true)) {
            cs.setStrokingColor(rgb[0], rgb[1], rgb[2]);
            cs.setLineWidth(width);
            cs.setLineCapStyle(1);
            cs.moveTo(ax1, ay1);
            cs.lineTo(ax2, ay2);
            cs.stroke();
            double left = angle + Math.toRadians(150);
            double right = angle - Math.toRadians(150);
            cs.moveTo(ax2, ay2);
            cs.lineTo((float) (ax2 + head * Math.cos(left)), (float) (ay2 + head * Math.sin(left)));
            cs.stroke();
            cs.moveTo(ax2, ay2);
            cs.lineTo((float) (ax2 + head * Math.cos(right)), (float) (ay2 + head * Math.sin(right)));
            cs.stroke();
        }
    }
}
