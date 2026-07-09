package com.orsconsulting.orssuitepdf.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

/**
 * Lectura y escritura del índice de marcadores (outline) de un PDF, con
 * conversión entre el modelo PDFBox y el modelo {@link Bookmark} de la app.
 */
public final class OutlineService {

    private OutlineService() {
    }

    /** Lee el árbol de marcadores del documento. Lista vacía si no tiene. */
    public static List<Bookmark> read(PDDocument document) {
        List<Bookmark> roots = new ArrayList<>();
        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        if (outline == null) {
            return roots;
        }
        for (PDOutlineItem item : outline.children()) {
            roots.add(toBookmark(item, document));
        }
        return roots;
    }

    private static Bookmark toBookmark(PDOutlineItem item, PDDocument document) {
        int pageIndex = resolvePageIndex(item, document);
        String title = item.getTitle() != null ? item.getTitle() : "(sin título)";
        Bookmark bookmark = new Bookmark(title, pageIndex);
        for (PDOutlineItem child : item.children()) {
            bookmark.getChildren().add(toBookmark(child, document));
        }
        return bookmark;
    }

    private static int resolvePageIndex(PDOutlineItem item, PDDocument document) {
        try {
            PDPage page = item.findDestinationPage(document);
            if (page != null) {
                return document.getPages().indexOf(page);
            }
        } catch (IOException ignored) {
            // Destino no resoluble: se marca como desconocido.
        }
        return -1;
    }

    /**
     * Reescribe por completo el outline del documento a partir del árbol de
     * marcadores dado. Los destinos fuera del rango de páginas se omiten.
     */
    public static void write(PDDocument document, List<Bookmark> roots) {
        PDDocumentOutline outline = new PDDocumentOutline();
        document.getDocumentCatalog().setDocumentOutline(outline);
        for (Bookmark bookmark : roots) {
            outline.addLast(toItem(bookmark, document));
        }
        outline.openNode();
    }

    private static PDOutlineItem toItem(Bookmark bookmark, PDDocument document) {
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(bookmark.getTitle());
        int pageIndex = bookmark.getPageIndex();
        if (pageIndex >= 0 && pageIndex < document.getNumberOfPages()) {
            item.setDestination(document.getPage(pageIndex));
        }
        for (Bookmark child : bookmark.getChildren()) {
            item.addLast(toItem(child, document));
        }
        if (bookmark.hasChildren()) {
            item.openNode();
        }
        return item;
    }
}
