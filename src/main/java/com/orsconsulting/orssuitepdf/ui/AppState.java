package com.orsconsulting.orssuitepdf.ui;

import com.orsconsulting.orssuitepdf.core.PdfDocument;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
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
    private final DoubleProperty zoom = new SimpleDoubleProperty(this, "zoom", 1.0);

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
        document.set(doc);
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
