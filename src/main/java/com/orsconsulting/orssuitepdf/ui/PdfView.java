package com.orsconsulting.orssuitepdf.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.orsconsulting.orssuitepdf.core.PdfDocument;
import com.orsconsulting.orssuitepdf.core.SearchService;

import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.geometry.Pos;

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
    /** Mientras se reconstruye/reajusta, no se recalcula la página desde el scroll. */
    private boolean adjustingScroll;
    /** Nº de páginas para las que se construyó el visor (detecta cambios estructurales). */
    private int builtPageCount = -1;

    /** Callback activo mientras se selecciona una región (firma visible). */
    private Consumer<PageRegion> pendingSelection;
    /** Callback activo mientras se dibuja un trazo a mano alzada. */
    private Consumer<PagePath> pendingPath;
    /** Callback activo mientras se dibuja una flecha. */
    private Consumer<PageLine> pendingLine;

    /** Coincidencias de búsqueda a resaltar, por página (recuadros en puntos). */
    private final Map<Integer, List<double[]>> matchesByPage = new HashMap<>();

    public PdfView(AppState state) {
        this.state = state;

        pagesBox.setAlignment(Pos.TOP_CENTER);
        pagesBox.setFillWidth(false);
        pagesBox.setStyle("-fx-padding: 16;");

        scroll.setContent(pagesBox);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        scroll.getStyleClass().add("pdf-scroll");

        getStyleClass().add("pdf-view");
        placeholder.getStyleClass().add("text-muted");

        setAlignment(Pos.CENTER);
        getChildren().addAll(scroll, placeholder);
        showPlaceholder(true);

        state.documentProperty().addListener((obs, oldDoc, newDoc) -> rebuild());
        state.revisionProperty().addListener((obs, oldR, newR) -> onRevision());
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
        int pageToRestore = state.getCurrentPage();
        adjustingScroll = true;
        cells.clear();
        matchesByPage.clear();
        pagesBox.getChildren().clear();

        PdfDocument doc = state.getDocument();
        if (doc == null) {
            builtPageCount = -1;
            adjustingScroll = false;
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
        builtPageCount = doc.pageCount();
        Platform.runLater(() -> {
            scrollToPage(Math.min(pageToRestore, cells.size() - 1));
            adjustingScroll = false;
            onViewportChanged();
        });
    }

    /**
     * Reacción a una modificación del documento. Si no cambió el número de
     * páginas (anotar, resaltar, insertar contenido…), solo se re-renderizan
     * las páginas visibles, conservando el scroll y la página actual. Solo si
     * cambió la estructura (borrar/mover/insertar páginas) se reconstruye.
     */
    private void onRevision() {
        PdfDocument doc = state.getDocument();
        if (doc == null || doc.pageCount() != builtPageCount) {
            rebuild();
            return;
        }
        reRenderVisible();
    }

    /** Vuelve a renderizar solo las páginas visibles (tras un cambio de contenido). */
    private void reRenderVisible() {
        double viewportHeight = scroll.getViewportBounds().getHeight();
        double contentHeight = pagesBox.getHeight();
        if (viewportHeight <= 0 || contentHeight <= 0) {
            return;
        }
        double top = Math.max(0, (contentHeight - viewportHeight) * scroll.getVvalue());
        double bottom = top + viewportHeight;
        for (PageCell cell : cells) {
            double cellTop = cell.getBoundsInParent().getMinY();
            double cellBottom = cell.getBoundsInParent().getMaxY();
            if (cellBottom >= top - viewportHeight && cellTop <= bottom + viewportHeight) {
                cell.invalidate();
                cell.render();
                cell.refreshMatches();
            }
        }
    }

    private void applyZoom() {
        for (PageCell cell : cells) {
            cell.resizeToPage();
            cell.invalidate();
            cell.refreshMatches();
        }
        Platform.runLater(this::onViewportChanged);
    }

    /** Resalta las coincidencias de búsqueda (borra las anteriores). */
    public void setSearchMatches(List<SearchService.Match> matches) {
        matchesByPage.clear();
        for (SearchService.Match match : matches) {
            matchesByPage.computeIfAbsent(match.page(), k -> new ArrayList<>())
                    .add(new double[]{match.x(), match.y(), match.width(), match.height()});
        }
        for (PageCell cell : cells) {
            cell.refreshMatches();
        }
    }

    public void clearSearchMatches() {
        matchesByPage.clear();
        for (PageCell cell : cells) {
            cell.refreshMatches();
        }
    }

    /** Desplaza para mostrar la página indicada (para saltar a una coincidencia). */
    public void goToPage(int page) {
        scrollToPage(page);
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

        if (!adjustingScroll && centered != state.getCurrentPage()) {
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

        private final Rectangle selectionRect = new Rectangle();
        private final List<Rectangle> matchRects = new ArrayList<>();
        private final Polyline inkOverlay = new Polyline();
        private final Line lineOverlay = new Line();
        private final List<double[]> inkPoints = new ArrayList<>();
        private double startX;
        private double startY;

        PageCell(int index) {
            this.index = index;
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            setStyle("-fx-background-color: white; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 8, 0, 0, 2);");

            selectionRect.setManaged(false);
            selectionRect.setVisible(false);
            selectionRect.setFill(Color.web("#2f6fed", 0.20));
            selectionRect.setStroke(Color.web("#2f6fed"));
            selectionRect.setStrokeWidth(1.5);

            for (var overlay : new javafx.scene.shape.Shape[]{inkOverlay, lineOverlay}) {
                overlay.setManaged(false);
                overlay.setVisible(false);
                overlay.setStroke(Color.web("#d32f2f"));
                overlay.setStrokeWidth(2);
                overlay.setFill(null);
            }
            getChildren().addAll(imageView, selectionRect, inkOverlay, lineOverlay);

            setOnMousePressed(e -> {
                double x = clampToPage(e.getX(), getWidth());
                double y = clampToPage(e.getY(), getHeight());
                if (pendingSelection != null) {
                    startX = x;
                    startY = y;
                    selectionRect.setX(x);
                    selectionRect.setY(y);
                    selectionRect.setWidth(0);
                    selectionRect.setHeight(0);
                    selectionRect.setVisible(true);
                    e.consume();
                } else if (pendingPath != null) {
                    inkPoints.clear();
                    inkPoints.add(new double[]{x, y});
                    inkOverlay.getPoints().setAll(x, y);
                    inkOverlay.setVisible(true);
                    e.consume();
                } else if (pendingLine != null) {
                    startX = x;
                    startY = y;
                    lineOverlay.setStartX(x);
                    lineOverlay.setStartY(y);
                    lineOverlay.setEndX(x);
                    lineOverlay.setEndY(y);
                    lineOverlay.setVisible(true);
                    e.consume();
                }
            });
            setOnMouseDragged(e -> {
                double x = clampToPage(e.getX(), getWidth());
                double y = clampToPage(e.getY(), getHeight());
                if (pendingSelection != null && selectionRect.isVisible()) {
                    selectionRect.setX(Math.min(startX, x));
                    selectionRect.setY(Math.min(startY, y));
                    selectionRect.setWidth(Math.abs(x - startX));
                    selectionRect.setHeight(Math.abs(y - startY));
                    e.consume();
                } else if (pendingPath != null && inkOverlay.isVisible()) {
                    inkPoints.add(new double[]{x, y});
                    inkOverlay.getPoints().addAll(x, y);
                    e.consume();
                } else if (pendingLine != null && lineOverlay.isVisible()) {
                    lineOverlay.setEndX(x);
                    lineOverlay.setEndY(y);
                    e.consume();
                }
            });
            setOnMouseReleased(e -> {
                double scale = state.getZoom() * BASE_DPI / 72.0;
                if (pendingSelection != null && selectionRect.isVisible()) {
                    selectionRect.setVisible(false);
                    double w = selectionRect.getWidth();
                    double h = selectionRect.getHeight();
                    finishSelection(w < 8 || h < 8 ? null : new PageRegion(index,
                            selectionRect.getX() / scale, selectionRect.getY() / scale,
                            w / scale, h / scale));
                    e.consume();
                } else if (pendingPath != null && inkOverlay.isVisible()) {
                    inkOverlay.setVisible(false);
                    List<double[]> points = new ArrayList<>();
                    for (double[] p : inkPoints) {
                        points.add(new double[]{p[0] / scale, p[1] / scale});
                    }
                    inkPoints.clear();
                    finishPath(points.size() >= 2 ? new PagePath(index, points) : null);
                    e.consume();
                } else if (pendingLine != null && lineOverlay.isVisible()) {
                    lineOverlay.setVisible(false);
                    double x1 = lineOverlay.getStartX() / scale;
                    double y1 = lineOverlay.getStartY() / scale;
                    double x2 = lineOverlay.getEndX() / scale;
                    double y2 = lineOverlay.getEndY() / scale;
                    finishLine(Math.hypot(x2 - x1, y2 - y1) < 5 ? null
                            : new PageLine(index, x1, y1, x2, y2));
                    e.consume();
                }
            });
        }

        private double clampToPage(double value, double max) {
            return Math.max(0, Math.min(max, value));
        }

        /** Redibuja los recuadros de las coincidencias de búsqueda de esta página. */
        void refreshMatches() {
            getChildren().removeAll(matchRects);
            matchRects.clear();
            List<double[]> boxes = matchesByPage.get(index);
            if (boxes == null) {
                return;
            }
            double scale = state.getZoom() * BASE_DPI / 72.0;
            for (double[] box : boxes) {
                Rectangle rect = new Rectangle(box[0] * scale, box[1] * scale,
                        box[2] * scale, box[3] * scale);
                rect.setManaged(false);
                rect.setFill(Color.web("#ffd54f", 0.45));
                rect.setStroke(Color.web("#f9a825"));
                rect.setStrokeWidth(1);
                matchRects.add(rect);
                getChildren().add(rect);
            }
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

    // -------------------------------------------- selección de región (firma)

    /**
     * Activa el modo de selección: el usuario arrastra un rectángulo sobre una
     * página y se invoca {@code onSelected} con la región en puntos PDF (origen
     * superior izquierdo). Si la selección se cancela (rectángulo diminuto), se
     * invoca con {@code null}.
     */
    public void beginRegionSelection(Consumer<PageRegion> onSelected) {
        pendingSelection = onSelected;
        scroll.setPannable(false);
        scroll.setCursor(Cursor.CROSSHAIR);
    }

    private void finishSelection(PageRegion region) {
        Consumer<PageRegion> callback = pendingSelection;
        pendingSelection = null;
        scroll.setPannable(true);
        scroll.setCursor(Cursor.DEFAULT);
        if (callback != null) {
            callback.accept(region);
        }
    }

    /** Región seleccionada en una página, en puntos PDF (origen superior izq.). */
    public record PageRegion(int page, double x, double y, double width, double height) {
    }

    /** Activa el modo de dibujo libre; entrega el trazo capturado. */
    public void beginFreehand(Consumer<PagePath> onDone) {
        pendingPath = onDone;
        scroll.setPannable(false);
        scroll.setCursor(Cursor.CROSSHAIR);
    }

    /** Activa el modo flecha; entrega la línea trazada. */
    public void beginArrow(Consumer<PageLine> onDone) {
        pendingLine = onDone;
        scroll.setPannable(false);
        scroll.setCursor(Cursor.CROSSHAIR);
    }

    private void finishPath(PagePath path) {
        Consumer<PagePath> callback = pendingPath;
        pendingPath = null;
        scroll.setPannable(true);
        scroll.setCursor(Cursor.DEFAULT);
        if (callback != null) {
            callback.accept(path);
        }
    }

    private void finishLine(PageLine line) {
        Consumer<PageLine> callback = pendingLine;
        pendingLine = null;
        scroll.setPannable(true);
        scroll.setCursor(Cursor.DEFAULT);
        if (callback != null) {
            callback.accept(line);
        }
    }

    /** Trazo a mano alzada en una página: puntos en puntos PDF (origen sup. izq.). */
    public record PagePath(int page, List<double[]> points) {
    }

    /** Flecha en una página: de (x1,y1) a (x2,y2) en puntos PDF (origen sup. izq.). */
    public record PageLine(int page, double x1, double y1, double x2, double y2) {
    }
}
