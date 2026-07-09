package com.orsconsulting.orssuitepdf.ui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

import com.orsconsulting.orssuitepdf.core.PdfDocument;
import com.orsconsulting.orssuitepdf.core.PdfOperations;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Pair;

/**
 * Ventana principal de la aplicación. Compone la barra de menús, la barra de
 * herramientas (apertura, guardado, navegación, operaciones de página y
 * zoom), el visor central y la barra de estado, y conecta todo con
 * {@link AppState}.
 */
public final class MainView {

    private static final String APP_NAME = "ORS Suite PDF";

    private final Stage stage;
    private final AppState state = new AppState();
    private final PdfView pdfView = new PdfView(state);
    private final BorderPane root = new BorderPane();

    private final Label pageLabel = new Label("—");
    private final Label zoomLabel = new Label("100 %");
    private final Label statusLabel = new Label("Listo");

    private Button prevButton;
    private Button nextButton;
    private Button saveButton;
    private Button rotateLeftButton;
    private Button rotateRightButton;
    private Button deleteButton;

    public MainView(Stage stage) {
        this.stage = stage;
        root.setTop(buildTopBar());
        root.setCenter(pdfView);
        root.setBottom(buildStatusBar());

        state.documentProperty().addListener((obs, oldDoc, newDoc) -> refreshControls());
        state.currentPageProperty().addListener((obs, oldP, newP) -> refreshControls());
        state.zoomProperty().addListener((obs, oldZ, newZ) -> refreshControls());
        state.revisionProperty().addListener((obs, oldR, newR) -> refreshControls());
        state.dirtyProperty().addListener((obs, oldD, newD) -> updateTitle());

        stage.setOnCloseRequest(event -> {
            if (!confirmDiscardChanges()) {
                event.consume();
            }
        });

        refreshControls();
        updateTitle();
    }

    public BorderPane getRoot() {
        return root;
    }

    // ---------------------------------------------------------------- barras

    private Region buildTopBar() {
        BorderPane top = new BorderPane();
        top.setTop(buildMenuBar());
        top.setCenter(buildToolBar());
        return top;
    }

    private MenuBar buildMenuBar() {
        Menu file = new Menu("Archivo");
        MenuItem open = new MenuItem("Abrir…");
        open.setAccelerator(new KeyCharacterCombination("O", KeyCombination.SHORTCUT_DOWN));
        open.setOnAction(e -> openDocument());
        MenuItem save = new MenuItem("Guardar");
        save.setAccelerator(new KeyCharacterCombination("S", KeyCombination.SHORTCUT_DOWN));
        save.setOnAction(e -> save());
        MenuItem saveAs = new MenuItem("Guardar como…");
        saveAs.setOnAction(e -> saveAs());
        MenuItem merge = new MenuItem("Unir PDF…");
        merge.setOnAction(e -> mergeDocuments());
        MenuItem exit = new MenuItem("Salir");
        exit.setOnAction(e -> {
            if (confirmDiscardChanges()) {
                Platform.exit();
            }
        });
        file.getItems().addAll(open, save, saveAs, new SeparatorMenuItem(),
                merge, new SeparatorMenuItem(), exit);

        Menu page = new Menu("Página");
        MenuItem rotL = new MenuItem("Rotar a la izquierda");
        rotL.setOnAction(e -> rotateCurrent(-90));
        MenuItem rotR = new MenuItem("Rotar a la derecha");
        rotR.setOnAction(e -> rotateCurrent(90));
        MenuItem moveBack = new MenuItem("Mover hacia atrás");
        moveBack.setOnAction(e -> moveCurrent(-1));
        MenuItem moveFwd = new MenuItem("Mover hacia delante");
        moveFwd.setOnAction(e -> moveCurrent(1));
        MenuItem delete = new MenuItem("Eliminar página actual");
        delete.setOnAction(e -> deleteCurrent());
        MenuItem extract = new MenuItem("Extraer rango…");
        extract.setOnAction(e -> extractRange());
        page.getItems().addAll(rotL, rotR, new SeparatorMenuItem(),
                moveBack, moveFwd, new SeparatorMenuItem(),
                delete, new SeparatorMenuItem(), extract);

        Menu help = new Menu("Ayuda");
        MenuItem about = new MenuItem("Acerca de ORS Suite PDF");
        about.setOnAction(e -> showAbout());
        help.getItems().add(about);

        return new MenuBar(file, page, help);
    }

    private ToolBar buildToolBar() {
        Button open = new Button("Abrir");
        open.setOnAction(e -> openDocument());

        saveButton = new Button("Guardar");
        saveButton.setOnAction(e -> save());

        prevButton = iconButton("◀", "Página anterior", () -> goToPage(state.getCurrentPage() - 1));
        nextButton = iconButton("▶", "Página siguiente", () -> goToPage(state.getCurrentPage() + 1));

        rotateLeftButton = iconButton("↺", "Rotar a la izquierda", () -> rotateCurrent(-90));
        rotateRightButton = iconButton("↻", "Rotar a la derecha", () -> rotateCurrent(90));
        deleteButton = iconButton("🗑", "Eliminar página actual", this::deleteCurrent);

        Button zoomOut = iconButton("−", "Reducir", () -> state.setZoom(state.getZoom() - 0.25));
        Button zoomReset = iconButton("100 %", "Zoom natural", () -> state.setZoom(1.0));
        Button zoomIn = iconButton("+", "Ampliar", () -> state.setZoom(state.getZoom() + 0.25));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new ToolBar(
                open, saveButton,
                new Separator(),
                prevButton, pageLabel, nextButton,
                new Separator(),
                rotateLeftButton, rotateRightButton, deleteButton,
                new Separator(),
                zoomOut, zoomReset, zoomIn, zoomLabel,
                spacer);
    }

    private Button iconButton(String text, String tip, Runnable action) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(tip));
        button.setOnAction(e -> action.run());
        return button;
    }

    private Region buildStatusBar() {
        HBox bar = new HBox(statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 12, 4, 12));
        bar.setStyle("-fx-background-color: -color-bg-subtle;");
        return bar;
    }

    // ----------------------------------------------------- apertura/guardado

    private void openDocument() {
        if (!confirmDiscardChanges()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir PDF");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Documentos PDF", "*.pdf"));
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }
        loadInBackground(selected.toPath());
    }

    private void loadInBackground(Path path) {
        statusLabel.setText("Abriendo " + path.getFileName() + "…");
        runBackground(() -> {
            PdfDocument doc = PdfDocument.open(path);
            Platform.runLater(() -> {
                state.setDocument(doc);
                statusLabel.setText(path.getFileName() + "  ·  " + doc.pageCount() + " páginas");
            });
        }, "No se pudo abrir el PDF");
    }

    private void save() {
        if (!state.hasDocument()) {
            return;
        }
        Path source = state.getDocument().source();
        if (source == null) {
            saveAs();
        } else {
            saveToInBackground(source);
        }
    }

    private void saveAs() {
        if (!state.hasDocument()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar como");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Documentos PDF", "*.pdf"));
        Path source = state.getDocument().source();
        if (source != null) {
            chooser.setInitialDirectory(source.getParent().toFile());
            chooser.setInitialFileName(source.getFileName().toString());
        }
        File target = chooser.showSaveDialog(stage);
        if (target != null) {
            saveToInBackground(target.toPath());
        }
    }

    /**
     * Guarda el documento de forma segura frente al bloqueo de fichero de
     * Windows: escribe a un temporal, cierra el documento abierto (que puede
     * estar reteniendo el fichero destino), reemplaza el destino y reabre el
     * resultado, conservando la página actual.
     */
    private void saveToInBackground(Path target) {
        int pageToRestore = state.getCurrentPage();
        statusLabel.setText("Guardando " + target.getFileName() + "…");
        runBackground(() -> {
            PdfDocument current = state.getDocument();
            Path parent = target.toAbsolutePath().getParent();
            Path tmp = Files.createTempFile(parent, "ors", ".pdf");
            PdfOperations.save(current.pdbox(), tmp);
            current.close();
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            PdfDocument reopened = PdfDocument.open(target);
            Platform.runLater(() -> {
                state.setDocument(reopened);
                int last = reopened.pageCount() - 1;
                state.setCurrentPage(Math.min(pageToRestore, last));
                state.setDirty(false);
                statusLabel.setText("Guardado: " + target.getFileName());
            });
        }, "No se pudo guardar el documento");
    }

    private void mergeDocuments() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecciona los PDF a unir (en orden)");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Documentos PDF", "*.pdf"));
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files == null || files.size() < 2) {
            if (files != null) {
                showError("Unir PDF", "Selecciona al menos dos documentos.");
            }
            return;
        }
        FileChooser saveChooser = new FileChooser();
        saveChooser.setTitle("Guardar PDF unido");
        saveChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Documentos PDF", "*.pdf"));
        saveChooser.setInitialFileName("unido.pdf");
        File output = saveChooser.showSaveDialog(stage);
        if (output == null) {
            return;
        }
        List<Path> inputs = files.stream().map(File::toPath).toList();
        statusLabel.setText("Uniendo " + inputs.size() + " documentos…");
        runBackground(() -> {
            PdfOperations.merge(inputs, output.toPath());
            Platform.runLater(() -> {
                statusLabel.setText("PDF unido: " + output.getName());
                if (confirm("Unir PDF", "Documentos unidos correctamente. ¿Abrir el resultado?")) {
                    loadInBackground(output.toPath());
                }
            });
        }, "No se pudieron unir los documentos");
    }

    private void extractRange() {
        if (!state.hasDocument()) {
            return;
        }
        int total = state.getDocument().pageCount();
        Optional<Pair<Integer, Integer>> range = askPageRange(total);
        if (range.isEmpty()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar páginas extraídas");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Documentos PDF", "*.pdf"));
        chooser.setInitialFileName("extracto.pdf");
        File output = chooser.showSaveDialog(stage);
        if (output == null) {
            return;
        }
        int from = range.get().getKey() - 1;
        int to = range.get().getValue() - 1;
        statusLabel.setText("Extrayendo páginas…");
        runBackground(() -> {
            PdfOperations.extractRange(state.getDocument().pdbox(), from, to, output.toPath());
            Platform.runLater(() -> statusLabel.setText("Extracto guardado: " + output.getName()));
        }, "No se pudo extraer el rango");
    }

    // ------------------------------------------------- operaciones de página

    private void rotateCurrent(int degrees) {
        mutate(() -> PdfOperations.rotatePage(
                state.getDocument().pdbox(), state.getCurrentPage(), degrees));
    }

    private void deleteCurrent() {
        if (!state.hasDocument()) {
            return;
        }
        mutate(() -> PdfOperations.deletePage(
                state.getDocument().pdbox(), state.getCurrentPage()));
    }

    private void moveCurrent(int direction) {
        if (!state.hasDocument()) {
            return;
        }
        int from = state.getCurrentPage();
        int to = from + direction;
        if (to < 0 || to >= state.getDocument().pageCount()) {
            return;
        }
        mutate(() -> {
            PdfOperations.movePage(state.getDocument().pdbox(), from, to);
            state.setCurrentPage(to);
        });
    }

    /** Ejecuta una mutación in situ y refresca el visor, capturando errores. */
    private void mutate(Runnable operation) {
        if (!state.hasDocument()) {
            return;
        }
        try {
            operation.run();
            state.markMutated();
        } catch (Exception ex) {
            showError("No se pudo completar la operación", ex.getMessage());
        }
    }

    // ---------------------------------------------------------- navegación

    private void goToPage(int index) {
        if (!state.hasDocument()) {
            return;
        }
        int last = state.getDocument().pageCount() - 1;
        state.setCurrentPage(Math.max(0, Math.min(last, index)));
    }

    private void refreshControls() {
        boolean has = state.hasDocument();
        int total = has ? state.getDocument().pageCount() : 0;
        int current = state.getCurrentPage();

        pageLabel.setText(has ? (current + 1) + " / " + total : "—");
        zoomLabel.setText(Math.round(state.getZoom() * 100) + " %");

        if (prevButton == null) {
            return;
        }
        prevButton.setDisable(!has || current <= 0);
        nextButton.setDisable(!has || current >= total - 1);
        saveButton.setDisable(!has);
        rotateLeftButton.setDisable(!has);
        rotateRightButton.setDisable(!has);
        deleteButton.setDisable(!has || total <= 1);
    }

    private void updateTitle() {
        if (!state.hasDocument()) {
            stage.setTitle(APP_NAME);
            return;
        }
        Path source = state.getDocument().source();
        String name = source != null ? source.getFileName().toString() : "documento sin guardar";
        stage.setTitle(APP_NAME + " — " + name + (state.isDirty() ? " *" : ""));
    }

    // -------------------------------------------------------------- diálogos

    private Optional<Pair<Integer, Integer>> askPageRange(int total) {
        Dialog<Pair<Integer, Integer>> dialog = new Dialog<>();
        dialog.setTitle("Extraer rango");
        dialog.setHeaderText("Selecciona el rango de páginas a extraer (1–" + total + ")");
        dialog.initOwner(stage);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Spinner<Integer> fromSpinner = new Spinner<>(1, total, 1);
        Spinner<Integer> toSpinner = new Spinner<>(1, total, total);
        fromSpinner.setEditable(true);
        toSpinner.setEditable(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.add(new Label("Desde:"), 0, 0);
        grid.add(fromSpinner, 1, 0);
        grid.add(new Label("Hasta:"), 0, 1);
        grid.add(toSpinner, 1, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                int from = fromSpinner.getValue();
                int to = toSpinner.getValue();
                return new Pair<>(Math.min(from, to), Math.max(from, to));
            }
            return null;
        });
        return dialog.showAndWait();
    }

    private boolean confirmDiscardChanges() {
        if (!state.isDirty()) {
            return true;
        }
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("Cambios sin guardar");
        alert.setHeaderText("El documento tiene cambios sin guardar.");
        alert.setContentText("¿Descartar los cambios?");
        alert.initOwner(stage);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private boolean confirm(String header, String message) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle(header);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.initOwner(stage);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void showAbout() {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Acerca de");
        alert.setHeaderText(APP_NAME);
        alert.setContentText("""
                Editor de PDF profesional, offline-first.
                Unir, dividir, rotar, marcadores, formularios,
                OCR y firma digital PAdES.""");
        alert.initOwner(stage);
        alert.showAndWait();
    }

    private void showError(String header, String detail) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(detail);
        alert.initOwner(stage);
        alert.showAndWait();
    }

    private void runBackground(BackgroundTask task, String errorHeader) {
        Thread worker = new Thread(() -> {
            try {
                task.run();
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error");
                    showError(errorHeader, ex.getMessage());
                });
            }
        }, "pdf-worker");
        worker.setDaemon(true);
        worker.start();
    }

    @FunctionalInterface
    private interface BackgroundTask {
        void run() throws Exception;
    }
}
