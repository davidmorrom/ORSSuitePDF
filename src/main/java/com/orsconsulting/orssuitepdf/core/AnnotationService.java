package com.orsconsulting.orssuitepdf.core;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
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
}
