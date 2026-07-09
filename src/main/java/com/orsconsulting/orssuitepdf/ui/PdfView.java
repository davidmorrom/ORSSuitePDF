package com.orsconsulting.orssuitepdf.ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import com.orsconsulting.orssuitepdf.core.PdfDocument;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

/**
 * Panel central que muestra la página actual del documento. Reacciona a los
 * cambios de {@link AppState} (documento, página, zoom) y renderiza en un
 * hilo de fondo para no bloquear la interfaz.
 *
 * <p>El renderizado se serializa en un único hilo y se descartan los
 * resultados obsoletos mediante un contador de generación, de modo que un
 * cambio rápido de página no deje una página anterior "ganando la carrera".</p>
 */
public final class PdfView extends StackPane {

    /** Resolución base de render, en DPI, a zoom 1.0. */
    private static final float BASE_DPI = 96f;

    private final AppState state;
    private final ImageView imageView = new ImageView();
    private final ScrollPane scroll = new ScrollPane();
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final Label placeholder = new Label("Abre un PDF para comenzar");

    private final ExecutorService renderPool = Executors.newSingleThreadExecutor(runnable -> {
        Thread t = new Thread(runnable, "pdf-render");
        t.setDaemon(true);
        return t;
    });
    private final AtomicLong generation = new AtomicLong();

    public PdfView(AppState state) {
        this.state = state;

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        StackPane canvas = new StackPane(imageView);
        canvas.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 16;");
        scroll.setContent(canvas);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setPannable(true);

        placeholder.getStyleClass().add("text-muted");
        spinner.setVisible(false);
        spinner.setMaxSize(48, 48);

        setAlignment(Pos.CENTER);
        getChildren().addAll(scroll, placeholder, spinner);
        showPlaceholder(true);

        state.documentProperty().addListener((obs, oldDoc, newDoc) -> requestRender());
        state.currentPageProperty().addListener((obs, oldP, newP) -> requestRender());
        state.zoomProperty().addListener((obs, oldZ, newZ) -> requestRender());
    }

    /** Fuerza un nuevo renderizado de la página actual. */
    public void requestRender() {
        PdfDocument doc = state.getDocument();
        if (doc == null) {
            showPlaceholder(true);
            imageView.setImage(null);
            return;
        }
        showPlaceholder(false);
        spinner.setVisible(true);

        final int page = state.getCurrentPage();
        final float dpi = (float) (BASE_DPI * state.getZoom());
        final long ticket = generation.incrementAndGet();

        renderPool.submit(() -> {
            try {
                Image image = doc.renderPage(page, dpi);
                Platform.runLater(() -> {
                    if (ticket == generation.get()) {
                        imageView.setImage(image);
                        spinner.setVisible(false);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (ticket == generation.get()) {
                        spinner.setVisible(false);
                        placeholder.setText("No se pudo renderizar la página: " + ex.getMessage());
                        showPlaceholder(true);
                    }
                });
            }
        });
    }

    private void showPlaceholder(boolean show) {
        placeholder.setVisible(show);
        scroll.setVisible(!show);
    }
}
