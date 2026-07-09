package com.orsconsulting.orssuitepdf.core;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Inserta imágenes (sellos, firmas manuscritas escaneadas, logotipos) sobre
 * una página del PDF. Es la base de la apariencia visible que reutilizará la
 * futura firma PAdES (fase 4); aquí no hay ninguna operación criptográfica.
 */
public final class StampService {

    private StampService() {
    }

    /**
     * Dibuja una imagen sobre una página, en coordenadas de usuario del PDF
     * (origen en la esquina inferior izquierda), en puntos.
     *
     * @param document  documento destino (se modifica en memoria)
     * @param pageIndex índice de página en base 0
     * @param image     ruta de la imagen (PNG/JPG)
     * @param x         posición X de la esquina inferior izquierda
     * @param y         posición Y de la esquina inferior izquierda
     * @param width     anchura en puntos
     * @param height    altura en puntos
     */
    public static void stampImage(PDDocument document, int pageIndex, Path image,
                                  float x, float y, float width, float height) throws IOException {
        if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
            throw new IndexOutOfBoundsException("Página fuera de rango: " + pageIndex);
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("El tamaño del sello debe ser positivo");
        }
        PDPage page = document.getPage(pageIndex);
        PDImageXObject xObject = PDImageXObject.createFromFileByExtension(image.toFile(), document);

        // El desplazamiento del cropBox permite colocar correctamente el sello
        // en documentos cuyo origen no está en (0,0).
        PDRectangle box = page.getCropBox();
        float originX = box.getLowerLeftX();
        float originY = box.getLowerLeftY();

        try (PDPageContentStream content = new PDPageContentStream(
                document, page, AppendMode.APPEND, true, true)) {
            content.drawImage(xObject, originX + x, originY + y, width, height);
        }
    }
}
