package com.orsconsulting.orssuitepdf.ui;

import java.io.File;
import java.nio.file.Path;

import com.orsconsulting.orssuitepdf.core.PdfDocument;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Ventana principal de la aplicación. Compone la barra de menús, la barra de
 * herramientas (apertura, navegación y zoom), el visor central y la barra de
 * estado, y conecta todo con {@link AppState}.
 */
public final class MainView {

    private final Stage stage;
    private final AppState state = new AppState();
    private final PdfView pdfView = new PdfView(state);
    private final BorderPane root = new BorderPane();

    private final Label pageLabel = new Label("—");
    private final Label zoomLabel = new Label("100 %");
    private final Label statusLabel = new Label("Listo");

    private Button prevButton;
    private Button nextButton;

    public MainView(Stage stage) {
        this.stage = stage;
        root.setTop(buildTopBar());
        root.setCenter(pdfView);
        root.setBottom(buildStatusBar());

        state.documentProperty().addListener((obs, oldDoc, newDoc) -> refreshControls());
        state.currentPageProperty().addListener((obs, oldP, newP) -> refreshControls());
        state.zoomProperty().addListener((obs, oldZ, newZ) -> refreshControls());
        refreshControls();
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
        open.setOnAction(e -> openDocument());
        MenuItem exit = new MenuItem("Salir");
        exit.setOnAction(e -> Platform.exit());
        file.getItems().addAll(open, new SeparatorMenuItem(), exit);

        Menu help = new Menu("Ayuda");
        MenuItem about = new MenuItem("Acerca de ORS Suite PDF");
        about.setOnAction(e -> showAbout());
        help.getItems().add(about);

        return new MenuBar(file, help);
    }

    private ToolBar buildToolBar() {
        Button open = new Button("Abrir");
        open.setOnAction(e -> openDocument());

        prevButton = new Button("◀");
        prevButton.setTooltip(new Tooltip("Página anterior"));
        prevButton.setOnAction(e -> goToPage(state.getCurrentPage() - 1));

        nextButton = new Button("▶");
        nextButton.setTooltip(new Tooltip("Página siguiente"));
        nextButton.setOnAction(e -> goToPage(state.getCurrentPage() + 1));

        Button zoomOut = new Button("−");
        zoomOut.setTooltip(new Tooltip("Reducir"));
        zoomOut.setOnAction(e -> state.setZoom(state.getZoom() - 0.25));

        Button zoomReset = new Button("100 %");
        zoomReset.setTooltip(new Tooltip("Zoom natural"));
        zoomReset.setOnAction(e -> state.setZoom(1.0));

        Button zoomIn = new Button("+");
        zoomIn.setTooltip(new Tooltip("Ampliar"));
        zoomIn.setOnAction(e -> state.setZoom(state.getZoom() + 0.25));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new ToolBar(
                open,
                new javafx.scene.control.Separator(),
                prevButton, pageLabel, nextButton,
                new javafx.scene.control.Separator(),
                zoomOut, zoomReset, zoomIn, zoomLabel,
                spacer);
    }

    private Region buildStatusBar() {
        HBox bar = new HBox(statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 12, 4, 12));
        bar.setStyle("-fx-background-color: -color-bg-subtle;");
        return bar;
    }

    // ------------------------------------------------------------- acciones

    private void openDocument() {
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
        Thread loader = new Thread(() -> {
            try {
                PdfDocument doc = PdfDocument.open(path);
                Platform.runLater(() -> {
                    state.setDocument(doc);
                    statusLabel.setText(path.getFileName() + "  ·  " + doc.pageCount() + " páginas");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error al abrir el documento");
                    showError("No se pudo abrir el PDF", ex.getMessage());
                });
            }
        }, "pdf-loader");
        loader.setDaemon(true);
        loader.start();
    }

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

        if (prevButton != null) {
            prevButton.setDisable(!has || current <= 0);
            nextButton.setDisable(!has || current >= total - 1);
        }
    }

    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Acerca de");
        alert.setHeaderText("ORS Suite PDF");
        alert.setContentText("""
                Editor de PDF profesional, offline-first.
                Unir, dividir, rotar, marcadores, formularios,
                OCR y firma digital PAdES.""");
        alert.initOwner(stage);
        alert.showAndWait();
    }

    private void showError(String header, String detail) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(detail);
        alert.initOwner(stage);
        alert.showAndWait();
    }
}
