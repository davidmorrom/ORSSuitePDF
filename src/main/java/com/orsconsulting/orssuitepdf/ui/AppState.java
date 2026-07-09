package com.orsconsulting.orssuitepdf.ui;

import com.orsconsulting.orssuitepdf.core.PdfDocument;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Estado observable de la aplicación: documento abierto, página actual y
 * nivel de zoom. La UI se enlaza a estas propiedades y reacciona a los
 * cambios, manteniendo una única fuente de verdad.
 */
public final class AppState {

    /** Zoom mínimo y máximo permitidos (factor sobre el tamaño natural). */
    public static final double MIN_ZOOM = 0.25;
    public static final double MAX_ZOOM = 5.0;

    private final ObjectProperty<PdfDocument> document = new SimpleObjectProperty<>(this, "document");
    private final IntegerProperty currentPage = new SimpleIntegerProperty(this, "currentPage", 0);
    /** Zoom inicial (algo reducido para que las páginas no se vean tan grandes). */
    public static final double DEFAULT_ZOOM = 0.7;

    private final DoubleProperty zoom = new SimpleDoubleProperty(this, "zoom", DEFAULT_ZOOM);

    /**
     * Contador que se incrementa cada vez que el documento se modifica "in
     * situ" (rotar, borrar, mover páginas). Como esas operaciones mutan el
     * mismo objeto {@link PdfDocument}, no disparan {@link #documentProperty},
     * así que la UI observa esta propiedad para re-renderizar y refrescarse.
     */
    private final IntegerProperty revision = new SimpleIntegerProperty(this, "revision", 0);

    /** Indica si hay cambios sin guardar en el documento abierto. */
    private final BooleanProperty dirty = new SimpleBooleanProperty(this, "dirty", false);

    public ObjectProperty<PdfDocument> documentProperty() {
        return document;
    }

    public PdfDocument getDocument() {
        return document.get();
    }

    /**
     * Sustituye el documento abierto, cerrando el anterior si lo hubiera, y
     * reinicia la página actual a la primera.
     */
    public void setDocument(PdfDocument doc) {
        PdfDocument previous = document.get();
        if (previous != null && previous != doc) {
            try {
                previous.close();
            } catch (Exception ignored) {
                // El cierre del documento anterior no debe impedir abrir el nuevo.
            }
        }
        currentPage.set(0);
        dirty.set(false);
        document.set(doc);
    }

    public IntegerProperty revisionProperty() {
        return revision;
    }

    /**
     * Señala que el documento se ha modificado en memoria: marca el estado
     * como "sucio", ajusta la página actual al nuevo rango y notifica a la UI.
     */
    public void markMutated() {
        dirty.set(true);
        PdfDocument doc = document.get();
        if (doc != null) {
            int last = Math.max(0, doc.pageCount() - 1);
            if (currentPage.get() > last) {
                currentPage.set(last);
            }
        }
        revision.set(revision.get() + 1);
    }

    public BooleanProperty dirtyProperty() {
        return dirty;
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public void setDirty(boolean value) {
        dirty.set(value);
    }

    public boolean hasDocument() {
        return document.get() != null;
    }

    public IntegerProperty currentPageProperty() {
        return currentPage;
    }

    public int getCurrentPage() {
        return currentPage.get();
    }

    public void setCurrentPage(int page) {
        currentPage.set(page);
    }

    public DoubleProperty zoomProperty() {
        return zoom;
    }

    public double getZoom() {
        return zoom.get();
    }

    public void setZoom(double value) {
        zoom.set(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value)));
    }
}
