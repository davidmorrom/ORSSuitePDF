package com.orsconsulting.orssuitepdf.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.orsconsulting.orssuitepdf.core.Bookmark;
import com.orsconsulting.orssuitepdf.core.OutlineService;
import com.orsconsulting.orssuitepdf.core.PdfDocument;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Panel lateral con el índice de marcadores del documento. Permite navegar a
 * la página de un marcador, así como añadir, renombrar y eliminar marcadores.
 * Los cambios se escriben en el documento en memoria y se marcan como
 * pendientes de guardar a través de {@link AppState}.
 */
public final class BookmarkPanel extends BorderPane {

    private final AppState state;
    private final TreeView<Bookmark> tree = new TreeView<>();

    private List<Bookmark> roots = new ArrayList<>();
    private boolean navigating;

    private Button addButton;
    private Button renameButton;
    private Button deleteButton;

    public BookmarkPanel(AppState state) {
        this.state = state;
        setPrefWidth(280);

        tree.setShowRoot(false);
        tree.setCellFactory(tv -> new BookmarkCell());
        tree.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            updateButtons();
            navigateTo(newItem);
        });

        setTop(buildToolBar());
        setCenter(tree);

        state.documentProperty().addListener((obs, oldDoc, newDoc) -> reload());
        reload();
    }

    private ToolBar buildToolBar() {
        addButton = new Button("＋");
        addButton.setTooltip(new Tooltip("Añadir marcador a la página actual"));
        addButton.setOnAction(e -> addBookmark());

        renameButton = new Button("✎");
        renameButton.setTooltip(new Tooltip("Renombrar marcador"));
        renameButton.setOnAction(e -> renameSelected());

        deleteButton = new Button("✕");
        deleteButton.setTooltip(new Tooltip("Eliminar marcador"));
        deleteButton.setOnAction(e -> deleteSelected());

        Label title = new Label("Marcadores");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToolBar bar = new ToolBar(title, spacer, addButton, renameButton, deleteButton);
        bar.setPadding(new Insets(4, 8, 4, 8));
        return bar;
    }

    // ------------------------------------------------------------- acciones

    private void addBookmark() {
        if (!state.hasDocument()) {
            return;
        }
        int page = state.getCurrentPage();
        TextInputDialog dialog = new TextInputDialog("Página " + (page + 1));
        dialog.setTitle("Nuevo marcador");
        dialog.setHeaderText("Marcador que apunta a la página " + (page + 1));
        dialog.setContentText("Título:");
        dialog.initOwner(getScene().getWindow());
        Optional<String> title = dialog.showAndWait();
        if (title.isEmpty() || title.get().isBlank()) {
            return;
        }
        roots.add(new Bookmark(title.get().trim(), page));
        persistAndRefresh();
    }

    private void renameSelected() {
        Bookmark selected = selectedBookmark();
        if (selected == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog(selected.getTitle());
        dialog.setTitle("Renombrar marcador");
        dialog.setContentText("Título:");
        dialog.initOwner(getScene().getWindow());
        Optional<String> title = dialog.showAndWait();
        if (title.isEmpty() || title.get().isBlank()) {
            return;
        }
        selected.setTitle(title.get().trim());
        persistAndRefresh();
    }

    private void deleteSelected() {
        Bookmark selected = selectedBookmark();
        if (selected == null) {
            return;
        }
        if (removeFrom(roots, selected)) {
            persistAndRefresh();
        }
    }

    private boolean removeFrom(List<Bookmark> list, Bookmark target) {
        if (list.remove(target)) {
            return true;
        }
        for (Bookmark bookmark : list) {
            if (removeFrom(bookmark.getChildren(), target)) {
                return true;
            }
        }
        return false;
    }

    private void navigateTo(TreeItem<Bookmark> item) {
        if (navigating || item == null || item.getValue() == null) {
            return;
        }
        int page = item.getValue().getPageIndex();
        if (page >= 0) {
            state.setCurrentPage(page);
        }
    }

    // -------------------------------------------------------- sincronización

    /** Escribe el árbol actual en el documento, marca cambios y refresca. */
    private void persistAndRefresh() {
        PdfDocument document = state.getDocument();
        if (document != null) {
            OutlineService.write(document.pdbox(), roots);
            state.markMutated();
        }
        buildTree();
    }

    /** Recarga los marcadores desde el documento abierto. */
    private void reload() {
        PdfDocument document = state.getDocument();
        roots = document != null ? OutlineService.read(document.pdbox()) : new ArrayList<>();
        buildTree();
    }

    private void buildTree() {
        navigating = true;
        try {
            TreeItem<Bookmark> hiddenRoot = new TreeItem<>(new Bookmark("", -1));
            for (Bookmark bookmark : roots) {
                hiddenRoot.getChildren().add(toTreeItem(bookmark));
            }
            tree.setRoot(hiddenRoot);
        } finally {
            navigating = false;
        }
        updateButtons();
    }

    private TreeItem<Bookmark> toTreeItem(Bookmark bookmark) {
        TreeItem<Bookmark> item = new TreeItem<>(bookmark);
        item.setExpanded(true);
        for (Bookmark child : bookmark.getChildren()) {
            item.getChildren().add(toTreeItem(child));
        }
        return item;
    }

    private Bookmark selectedBookmark() {
        TreeItem<Bookmark> item = tree.getSelectionModel().getSelectedItem();
        return item != null ? item.getValue() : null;
    }

    private void updateButtons() {
        boolean hasDoc = state.hasDocument();
        boolean hasSelection = selectedBookmark() != null;
        addButton.setDisable(!hasDoc);
        renameButton.setDisable(!hasSelection);
        deleteButton.setDisable(!hasSelection);
    }

    /** Celda que muestra el título y la página de destino del marcador. */
    private static final class BookmarkCell extends TreeCell<Bookmark> {
        @Override
        protected void updateItem(Bookmark bookmark, boolean empty) {
            super.updateItem(bookmark, empty);
            if (empty || bookmark == null) {
                setText(null);
                return;
            }
            String page = bookmark.getPageIndex() >= 0
                    ? "  ·  p. " + (bookmark.getPageIndex() + 1)
                    : "";
            setText(bookmark.getTitle() + page);
        }
    }
}
