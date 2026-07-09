package com.orsconsulting.orssuitepdf.core;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Operaciones estructurales sobre documentos PDF, implementadas con PDFBox.
 *
 * <p>Las operaciones "in situ" ({@code rotatePage}, {@code deletePage},
 * {@code movePage}) mutan el {@link PDDocument} recibido, que sigue siendo el
 * documento abierto en el visor; los cambios se persisten con {@link #save}.
 * Las operaciones de composición ({@code merge}, {@code extractRange})
 * producen ficheros nuevos sin tocar el documento de origen.</p>
 */
public final class PdfOperations {

    private PdfOperations() {
    }

    /** Une varios PDF, en el orden dado, en un único fichero de salida. */
    public static void merge(List<Path> inputs, Path output) throws IOException {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos un documento para unir");
        }
        PDFMergerUtility merger = new PDFMergerUtility();
        merger.setDestinationFileName(output.toString());
        for (Path input : inputs) {
            merger.addSource(input.toFile());
        }
        merger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache());
    }

    /**
     * Extrae un rango de páginas (índices en base 0, ambos inclusive) a un
     * documento nuevo. Las páginas se clonan en profundidad, por lo que el
     * documento de origen no se ve afectado.
     */
    public static void extractRange(PDDocument source, int fromIndex, int toIndex, Path output)
            throws IOException {
        int last = source.getNumberOfPages() - 1;
        if (fromIndex < 0 || toIndex > last || fromIndex > toIndex) {
            throw new IllegalArgumentException(
                    "Rango de páginas inválido: " + (fromIndex + 1) + "–" + (toIndex + 1));
        }
        try (PDDocument target = new PDDocument()) {
            for (int i = fromIndex; i <= toIndex; i++) {
                target.importPage(source.getPage(i));
            }
            target.save(output.toFile());
        }
    }

    /** Rota una página sumando {@code deltaDegrees} (múltiplo de 90) a su giro. */
    public static void rotatePage(PDDocument document, int pageIndex, int deltaDegrees) {
        PDPage page = requirePage(document, pageIndex);
        int rotation = (page.getRotation() + deltaDegrees) % 360;
        if (rotation < 0) {
            rotation += 360;
        }
        page.setRotation(rotation);
    }

    /** Elimina una página. No permite dejar el documento sin páginas. */
    public static void deletePage(PDDocument document, int pageIndex) {
        requirePage(document, pageIndex);
        if (document.getNumberOfPages() <= 1) {
            throw new IllegalStateException("El documento debe conservar al menos una página");
        }
        document.removePage(pageIndex);
    }

    /**
     * Mueve una página desde {@code fromIndex} hasta {@code toIndex}
     * (ambos en base 0), reconstruyendo el árbol de páginas.
     */
    public static void movePage(PDDocument document, int fromIndex, int toIndex) {
        int count = document.getNumberOfPages();
        if (fromIndex < 0 || fromIndex >= count || toIndex < 0 || toIndex >= count) {
            throw new IndexOutOfBoundsException("Índice de página fuera de rango");
        }
        if (fromIndex == toIndex) {
            return;
        }
        List<PDPage> pages = new ArrayList<>(count);
        for (PDPage page : document.getPages()) {
            pages.add(page);
        }
        PDPage moved = pages.remove(fromIndex);
        pages.add(toIndex, moved);

        while (document.getNumberOfPages() > 0) {
            document.removePage(0);
        }
        for (PDPage page : pages) {
            document.addPage(page);
        }
    }

    /** Guarda el documento en la ruta indicada. */
    public static void save(PDDocument document, Path output) throws IOException {
        document.save(output.toFile());
    }

    /**
     * Guarda el documento de forma <strong>incremental</strong> (append-only):
     * escribe los bytes originales tal cual y añade al final una actualización
     * con los cambios. Esto preserva el {@code ByteRange} de las firmas
     * digitales existentes, que un guardado normal ({@link #save}) rompería al
     * reescribir el fichero entero.
     *
     * <p>Requiere que el documento se cargara desde una fuente re-leíble (un
     * fichero), condición que cumple {@link PdfDocument#open(Path)}. Los cambios
     * puramente aditivos (anotaciones, campos de formulario, una firma nueva)
     * mantienen válidas las firmas previas; los cambios estructurales (rotar,
     * borrar o mover páginas) las invalidan igualmente por naturaleza del
     * formato, aunque el guardado incremental deje el fichero bien formado.</p>
     */
    public static void saveIncremental(PDDocument document, Path output) throws IOException {
        try (OutputStream out = Files.newOutputStream(output)) {
            document.saveIncremental(out);
        }
    }

    private static PDPage requirePage(PDDocument document, int pageIndex) {
        if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
            throw new IndexOutOfBoundsException("Página fuera de rango: " + pageIndex);
        }
        return document.getPage(pageIndex);
    }
}
