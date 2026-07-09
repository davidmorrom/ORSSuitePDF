package com.orsconsulting.orssuitepdf.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import com.orsconsulting.orssuitepdf.core.PdfDocument;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Visor central con desplazamiento vertical continuo: todas las páginas se
 * apilan una debajo de otra, de modo que se puede ver el final de una página y
 * el principio de la siguiente a la vez.
 *
 * <p>El renderizado es perezoso —solo se dibujan las páginas visibles (más un
 * margen)— y se serializa en un único hilo para no bloquear la interfaz ni
 * saturar la memoria. La página actual de {@link AppState} se sincroniza en
 * ambos sentidos: al desplazar se actualiza el indicador, y al navegar o pulsar
 * un marcador se hace scroll hasta la página.</p>
 */
public final class PdfView extends StackPane {

    /** Resolución base de render, en DPI, a zoom 1.0. */
    private static final float BASE_DPI = 96f;
    /** Separación vertical entre páginas, en píxeles. */
    private static final double PAGE_GAP = 14;

    private final AppState state;
    private final ScrollPane scroll = new ScrollPane();
    private final VBox pagesBox = new VBox(PAGE_GAP);
    private final Label placeholder = new Label("Abre un PDF para comenzar");

    private final List<PageCell> cells = new ArrayList<>();

    private final ExecutorService renderPool = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pdf-render");
        thread.setDaemon(true);
        return thread;
    });

    /** Evita el bucle de realimentación entre scroll y página actual. */
    private boolean syncingFromScroll;

    public PdfView(AppState state) {
        this.state = state;

        pagesBox.setAlignment(Pos.TOP_CENTER);
        pagesBox.setFillWidth(false);
        pagesBox.setStyle("-fx-padding: 16;");

        scroll.setContent(pagesBox);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        scroll.setStyle("-fx-background-color: -color-bg-subtle;");

        placeholder.getStyleClass().add("text-muted");

        setAlignment(Pos.CENTER);
        getChildren().addAll(scroll, placeholder);
        showPlaceholder(true);

        state.documentProperty().addListener((obs, oldDoc, newDoc) -> rebuild());
        state.revisionProperty().addListener((obs, oldR, newR) -> rebuild());
        state.zoomProperty().addListener((obs, oldZ, newZ) -> applyZoom());
        state.currentPageProperty().addListener((obs, oldP, newP) -> {
            if (!syncingFromScroll) {
                scrollToPage(newP.intValue());
            }
        });

        scroll.vvalueProperty().addListener((obs, o, n) -> onViewportChanged());
        scroll.viewportBoundsProperty().addListener((obs, o, n) -> onViewportChanged());
        pagesBox.heightProperty().addListener((obs, o, n) -> onViewportChanged());
    }

    // --------------------------------------------------- construcción/zoom

    private void rebuild() {
        cells.clear();
        pagesBox.getChildren().clear();

        PdfDocument doc = state.getDocument();
        if (doc == null) {
            showPlaceholder(true);
            return;
        }
        showPlaceholder(false);

        for (int i = 0; i < doc.pageCount(); i++) {
            PageCell cell = new PageCell(i);
            cell.resizeToPage();
            cells.add(cell);
            pagesBox.getChildren().add(cell);
        }
        Platform.runLater(() -> {
            scrollToPage(state.getCurrentPage());
            onViewportChanged();
        });
    }

    private void applyZoom() {
        for (PageCell cell : cells) {
            cell.resizeToPage();
            cell.invalidate();
        }
        Platform.runLater(this::onViewportChanged);
    }

    // ------------------------------------------------------------ scroll

    private void onViewportChanged() {
        if (cells.isEmpty()) {
            return;
        }
        double viewportHeight = scroll.getViewportBounds().getHeight();
        double contentHeight = pagesBox.getHeight();
        if (viewportHeight <= 0 || contentHeight <= 0) {
            return;
        }
        double top = Math.max(0, (contentHeight - viewportHeight) * scroll.getVvalue());
        double bottom = top + viewportHeight;

        // Renderiza las páginas visibles más un margen de una pantalla; libera
        // la imagen de las que quedan lejos para acotar la memoria.
        double renderFrom = top - viewportHeight;
        double renderTo = bottom + viewportHeight;
        double keepFrom = top - 2 * viewportHeight;
        double keepTo = bottom + 2 * viewportHeight;

        double centerY = (top + bottom) / 2;
        int centered = state.getCurrentPage();
        double bestDistance = Double.MAX_VALUE;

        for (PageCell cell : cells) {
            double cellTop = cell.getBoundsInParent().getMinY();
            double cellBottom = cell.getBoundsInParent().getMaxY();
            if (cellBottom >= renderFrom && cellTop <= renderTo) {
                cell.render();
            } else if (cellBottom < keepFrom || cellTop > keepTo) {
                cell.release();
            }
            double cellCenter = (cellTop + cellBottom) / 2;
            double distance = Math.abs(cellCenter - centerY);
            if (distance < bestDistance) {
                bestDistance = distance;
                centered = cell.index;
            }
        }

        if (centered != state.getCurrentPage()) {
            syncingFromScroll = true;
            try {
                state.setCurrentPage(centered);
            } finally {
                syncingFromScroll = false;
            }
        }
    }

    private void scrollToPage(int index) {
        if (index < 0 || index >= cells.size()) {
            return;
        }
        double viewportHeight = scroll.getViewportBounds().getHeight();
        double contentHeight = pagesBox.getHeight();
        double denominator = contentHeight - viewportHeight;
        if (denominator <= 0) {
            scroll.setVvalue(0);
            return;
        }
        double cellTop = cells.get(index).getBoundsInParent().getMinY() - PAGE_GAP;
        scroll.setVvalue(Math.max(0, Math.min(1, cellTop / denominator)));
    }

    private void showPlaceholder(boolean show) {
        placeholder.setVisible(show);
        scroll.setVisible(!show);
    }

    // ---------------------------------------------------------- celda página

    /** Una página del documento dentro del scroll, con render perezoso. */
    private final class PageCell extends StackPane {

        private final int index;
        private final ImageView imageView = new ImageView();
        private final AtomicLong ticket = new AtomicLong();
        private double renderedZoom = -1;

        PageCell(int index) {
            this.index = index;
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            setStyle("-fx-background-color: white; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 8, 0, 0, 2);");
            getChildren().add(imageView);
        }

        void resizeToPage() {
            PdfDocument doc = state.getDocument();
            if (doc == null) {
                return;
            }
            double scale = state.getZoom() * BASE_DPI / 72.0;
            double width = doc.pageWidth(index) * scale;
            double height = doc.pageHeight(index) * scale;
            setMinSize(width, height);
            setPrefSize(width, height);
            setMaxSize(width, height);
            imageView.setFitWidth(width);
        }

        /** Marca la imagen como obsoleta (p. ej. tras cambiar el zoom). */
        void invalidate() {
            renderedZoom = -1;
            imageView.setImage(null);
        }

        /** Libera la imagen para acotar memoria cuando la página está lejos. */
        void release() {
            if (imageView.getImage() != null) {
                imageView.setImage(null);
                renderedZoom = -1;
            }
        }

        void render() {
            PdfDocument doc = state.getDocument();
            double zoom = state.getZoom();
            if (doc == null || renderedZoom == zoom) {
                return;
            }
            renderedZoom = zoom;
            float dpi = (float) (BASE_DPI * zoom);
            long myTicket = ticket.incrementAndGet();
            renderPool.submit(() -> {
                try {
                    Image image = doc.renderPage(index, dpi);
                    Platform.runLater(() -> {
                        if (myTicket == ticket.get()) {
                            imageView.setImage(image);
                        }
                    });
                } catch (Exception ex) {
                    renderedZoom = -1;
                }
            });
        }
    }

    /** Bounds de una página en coordenadas de la escena (para selección). */
    Bounds pageBoundsInScene(int index) {
        if (index < 0 || index >= cells.size()) {
            return null;
        }
        return cells.get(index).localToScene(cells.get(index).getBoundsInLocal());
    }
}
