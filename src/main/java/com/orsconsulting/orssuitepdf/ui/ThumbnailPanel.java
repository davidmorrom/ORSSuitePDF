package com.orsconsulting.orssuitepdf.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.orsconsulting.orssuitepdf.core.PdfDocument;
import com.orsconsulting.orssuitepdf.core.PdfOperations;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * Panel de miniaturas de las páginas del documento. Permite navegar (clic),
 * <strong>reordenar arrastrando</strong> una miniatura sobre otra, y rotar o
 * eliminar la página mediante el menú contextual. Las miniaturas se generan en
 * segundo plano.
 */
public final class ThumbnailPanel extends BorderPane {

    /** Resolución de las miniaturas (pequeña). */
    private static final float THUMB_DPI = 24f;
    private static final double THUMB_WIDTH = 130;

    private final AppState state;
    private final ListView<Integer> list = new ListView<>();
    private final Map<Integer, Image> cache = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pdf-thumbs");
        t.setDaemon(true);
        return t;
    });

    private boolean syncing;

    public ThumbnailPanel(AppState state) {
        this.state = state;

        list.setCellFactory(lv -> new ThumbCell());
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (!syncing && newV != null) {
                state.setCurrentPage(newV);
            }
        });
        setCenter(list);

        state.documentProperty().addListener((o, a, b) -> {
            cache.clear();
            rebuild();
        });
        state.revisionProperty().addListener((o, a, b) -> onRevision());
        state.currentPageProperty().addListener((o, a, b) -> selectQuietly(b.intValue()));
        rebuild();
    }

    /**
     * Ante una modificación: si cambió el nº de páginas, reconstruye la lista;
     * si solo cambió el contenido, refresca la miniatura de la página actual
     * sin resetear la lista ni la selección.
     */
    private void onRevision() {
        PdfDocument document = state.getDocument();
        int count = document != null ? document.pageCount() : 0;
        if (count != list.getItems().size()) {
            cache.clear();
            rebuild();
            return;
        }
        cache.remove(state.getCurrentPage());
        list.refresh();
    }

    private void rebuild() {
        PdfDocument document = state.getDocument();
        List<Integer> indices = new ArrayList<>();
        if (document != null) {
            for (int i = 0; i < document.pageCount(); i++) {
                indices.add(i);
            }
        }
        list.getItems().setAll(indices);
        selectQuietly(state.getCurrentPage());
    }

    private void selectQuietly(int index) {
        if (index < 0 || index >= list.getItems().size()) {
            return;
        }
        syncing = true;
        try {
            list.getSelectionModel().select(index);
        } finally {
            syncing = false;
        }
    }

    private void requestThumbnail(int page) {
        PdfDocument document = state.getDocument();
        if (document == null || cache.containsKey(page)) {
            return;
        }
        pool.submit(() -> {
            try {
                Image image = document.renderPage(page, THUMB_DPI);
                cache.put(page, image);
                Platform.runLater(list::refresh);
            } catch (Exception ignored) {
                // Una miniatura que falle no debe romper el panel.
            }
        });
    }

    private void reorder(int from, int to) {
        PdfDocument document = state.getDocument();
        if (document == null || from == to) {
            return;
        }
        try {
            PdfOperations.movePage(document.pdbox(), from, to);
            state.setCurrentPage(to);
            state.markMutated();
        } catch (Exception ignored) {
            // Movimiento inválido: se ignora.
        }
    }

    /** Celda con la miniatura y el número de página; soporta arrastrar y soltar. */
    private final class ThumbCell extends ListCell<Integer> {

        private final ImageView imageView = new ImageView();
        private final Label label = new Label();
        private final VBox box = new VBox(4, imageView, label);

        ThumbCell() {
            box.setAlignment(Pos.CENTER);
            imageView.setFitWidth(THUMB_WIDTH);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            // Nº de página en monoespaciada, como en la especificación.
            label.getStyleClass().add("mono");
            label.setStyle("-fx-font-size: 10px;");

            setOnDragDetected(e -> {
                if (isEmpty() || getItem() == null) {
                    return;
                }
                Dragboard db = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(String.valueOf(getItem()));
                db.setContent(content);
                if (imageView.getImage() != null) {
                    db.setDragView(imageView.getImage());
                }
                e.consume();
            });
            setOnDragOver(e -> {
                if (e.getDragboard().hasString() && !isEmpty()) {
                    e.acceptTransferModes(TransferMode.MOVE);
                }
                e.consume();
            });
            setOnDragDropped(e -> {
                Dragboard db = e.getDragboard();
                boolean done = false;
                if (db.hasString() && !isEmpty() && getItem() != null) {
                    reorder(Integer.parseInt(db.getString()), getItem());
                    done = true;
                }
                e.setDropCompleted(done);
                e.consume();
            });

            MenuItem rotate = new MenuItem("Rotar 90°");
            rotate.setOnAction(e -> pageOp(page -> PdfOperations.rotatePage(
                    state.getDocument().pdbox(), page, 90)));
            MenuItem delete = new MenuItem("Eliminar página");
            delete.setOnAction(e -> pageOp(page -> PdfOperations.deletePage(
                    state.getDocument().pdbox(), page)));
            setContextMenu(new ContextMenu(rotate, delete));
        }

        private void pageOp(java.util.function.IntConsumer op) {
            Integer page = getItem();
            if (page == null || state.getDocument() == null) {
                return;
            }
            try {
                op.accept(page);
                state.markMutated();
            } catch (Exception ignored) {
                // Operación inválida (p. ej. borrar la única página): se ignora.
            }
        }

        @Override
        protected void updateItem(Integer page, boolean empty) {
            super.updateItem(page, empty);
            if (empty || page == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            label.setText(String.valueOf(page + 1));
            Image thumb = cache.get(page);
            imageView.setImage(thumb);
            if (thumb == null) {
                requestThumbnail(page);
            }
            setGraphic(box);
        }
    }
}
