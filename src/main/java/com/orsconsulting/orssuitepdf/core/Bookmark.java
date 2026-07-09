package com.orsconsulting.orssuitepdf.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Nodo de marcador (entrada del índice/outline del PDF), independiente de
 * PDFBox para poder mostrarse y editarse en la interfaz. Un marcador tiene un
 * título, la página de destino (índice en base 0, o {@code -1} si no se pudo
 * resolver) e hijos anidados.
 */
public final class Bookmark {

    private String title;
    private int pageIndex;
    private final List<Bookmark> children = new ArrayList<>();

    public Bookmark(String title, int pageIndex) {
        this.title = title;
        this.pageIndex = pageIndex;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    /** Índice de página de destino en base 0, o {@code -1} si es desconocido. */
    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public List<Bookmark> getChildren() {
        return children;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    @Override
    public String toString() {
        return title;
    }
}
