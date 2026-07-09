package com.orsconsulting.orssuitepdf.ui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.Preferences;

import com.orsconsulting.orssuitepdf.core.AnnotationService;
import com.orsconsulting.orssuitepdf.core.ExportService;
import com.orsconsulting.orssuitepdf.core.PdfDocument;
import com.orsconsulting.orssuitepdf.core.PdfOperations;
import com.orsconsulting.orssuitepdf.core.RedactionService;
import com.orsconsulting.orssuitepdf.core.SearchService;
import com.orsconsulting.orssuitepdf.core.SecurityService;
import com.orsconsulting.orssuitepdf.core.StampService;
import com.orsconsulting.orssuitepdf.core.WatermarkService;
import com.orsconsulting.orssuitepdf.ocr.OcrService;
import com.orsconsulting.orssuitepdf.signing.PAdESSigner;
import com.orsconsulting.orssuitepdf.signing.SignResult;
import com.orsconsulting.orssuitepdf.signing.SignSpec;
import com.orsconsulting.orssuitepdf.signing.SignatureValidationService;
import com.orsconsulting.orssuitepdf.signing.SigningTokens;
import com.orsconsulting.orssuitepdf.signing.VisibleSignature;

import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.SignatureTokenConnection;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
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
    private final BorderPane root = new BorderPane();

    /** Una pestaña por documento abierto. */
    private final TabPane documentsPane = new TabPane();
    private final Map<Tab, DocumentSession> sessions = new HashMap<>();
    /** Estado "vacío" cuando no hay ningún documento abierto. */
    private final AppState emptyState = new AppState();

    /** Estado y visor de la pestaña activa (o los vacíos si no hay ninguna). */
    private AppState state = emptyState;
    private PdfView pdfView;

    private final Label pageLabel = new Label("—");
    private final Label zoomLabel = new Label("100 %");
    private final Label statusLabel = new Label("Listo");

    private final Menu recentMenu = new Menu("Abrir reciente");

    private HBox searchBar;
    private TextField searchField;
    private final Label searchCount = new Label("0/0");
    private List<SearchService.Match> searchResults = new ArrayList<>();
    private int searchIndex;

    private Button prevButton;
    private Button nextButton;
    private Button saveButton;
    private Button rotateLeftButton;
    private Button rotateRightButton;
    private Button deleteButton;

    public MainView(Stage stage) {
        this.stage = stage;
        root.setTop(buildTopBar());
        root.setCenter(buildWorkspace());
        root.setBottom(new VBox(buildSearchBar(), buildStatusBar()));

        documentsPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            DocumentSession session = newTab == null ? null : sessions.get(newTab);
            state = session != null ? session.state : emptyState;
            pdfView = session != null ? session.pdfView : null;
            refreshControls();
            updateTitle();
        });

        stage.setOnCloseRequest(event -> {
            if (!confirmCloseAll()) {
                event.consume();
            }
        });

        // Arrastrar y soltar PDF sobre la ventana para abrirlos.
        root.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });
        root.setOnDragDropped(event -> {
            var dragboard = event.getDragboard();
            boolean done = false;
            if (dragboard.hasFiles()) {
                for (File file : dragboard.getFiles()) {
                    if (file.getName().toLowerCase().endsWith(".pdf")) {
                        loadInBackground(file.toPath());
                        done = true;
                    }
                }
            }
            event.setDropCompleted(done);
            event.consume();
        });

        applyTheme(prefs.getBoolean(PREF_DARK, false));
        refreshControls();
        updateTitle();
    }

    public BorderPane getRoot() {
        return root;
    }

    // ---------------------------------------------------------------- barras

    private final javafx.scene.layout.StackPane workspace = new javafx.scene.layout.StackPane();

    private Region buildWorkspace() {
        documentsPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        workspace.getChildren().add(documentsPane);
        documentsPane.getTabs().addListener(
                (javafx.collections.ListChangeListener<Tab>) c -> refreshWelcome());
        refreshWelcome();
        return workspace;
    }

    /** Muestra la pantalla de inicio cuando no hay documentos abiertos. */
    private void refreshWelcome() {
        workspace.getChildren().removeIf(node -> node instanceof WelcomePane);
        boolean empty = documentsPane.getTabs().isEmpty();
        documentsPane.setVisible(!empty);
        if (empty) {
            List<Path> recent = recentFiles().stream().map(Path::of).toList();
            WelcomePane welcome = new WelcomePane(this::openDocument, this::mergeDocuments,
                    recent, this::loadInBackground, prefs.getBoolean(PREF_DARK, false));
            workspace.getChildren().add(welcome);
        }
    }

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
        refreshRecentMenu();
        MenuItem save = new MenuItem("Guardar");
        save.setAccelerator(new KeyCharacterCombination("S", KeyCombination.SHORTCUT_DOWN));
        save.setOnAction(e -> save());
        MenuItem saveAs = new MenuItem("Guardar como…");
        saveAs.setOnAction(e -> saveAs());
        MenuItem merge = new MenuItem("Unir PDF…");
        merge.setOnAction(e -> mergeDocuments());
        MenuItem print = new MenuItem("Imprimir…");
        print.setAccelerator(new KeyCharacterCombination("P", KeyCombination.SHORTCUT_DOWN));
        print.setOnAction(e -> printDocument());
        Menu export = buildExportMenu();
        MenuItem exit = new MenuItem("Salir");
        exit.setOnAction(e -> {
            if (confirmCloseAll()) {
                Platform.exit();
            }
        });
        file.getItems().addAll(open, recentMenu, save, saveAs, new SeparatorMenuItem(),
                merge, print, export, new SeparatorMenuItem(), exit);

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

        Menu insert = new Menu("Insertar");
        MenuItem stamp = new MenuItem("Imagen o sello…");
        stamp.setOnAction(e -> insertImage());
        MenuItem textItem = new MenuItem("Texto…");
        textItem.setOnAction(e -> insertText());
        MenuItem watermark = new MenuItem("Marca de agua…");
        watermark.setOnAction(e -> addWatermark());
        MenuItem numbering = new MenuItem("Numerar páginas");
        numbering.setOnAction(e -> numberPages());
        insert.getItems().addAll(stamp, textItem, new SeparatorMenuItem(), watermark, numbering);

        Menu annotate = new Menu("Anotar");
        MenuItem hl = new MenuItem("Resaltar zona");
        hl.setOnAction(e -> annotate("Resaltado añadido",
                (d, r) -> AnnotationService.highlight(d, r.page(), r.x(), r.y(), r.width(), r.height())));
        MenuItem rect = new MenuItem("Recuadro");
        rect.setOnAction(e -> annotate("Recuadro añadido",
                (d, r) -> AnnotationService.rectangle(d, r.page(), r.x(), r.y(), r.width(), r.height())));
        MenuItem note = new MenuItem("Nota…");
        note.setOnAction(e -> annotateNote());
        MenuItem ink = new MenuItem("Dibujo libre");
        ink.setOnAction(e -> annotateFreehand());
        MenuItem arrow = new MenuItem("Flecha");
        arrow.setOnAction(e -> annotateArrow());
        annotate.getItems().addAll(hl, rect, note, new SeparatorMenuItem(), ink, arrow);

        Menu tools = new Menu("Herramientas");
        MenuItem ocr = new MenuItem("OCR de la página actual");
        ocr.setOnAction(e -> ocrCurrentPage());
        MenuItem redact = new MenuItem("Redactar zona…");
        redact.setOnAction(e -> redactZone());
        MenuItem find = new MenuItem("Buscar…");
        find.setAccelerator(new KeyCharacterCombination("F", KeyCombination.SHORTCUT_DOWN));
        find.setOnAction(e -> toggleSearch());
        MenuItem protect = new MenuItem("Proteger con contraseña…");
        protect.setOnAction(e -> protectDocument());
        MenuItem unprotect = new MenuItem("Quitar protección");
        unprotect.setOnAction(e -> unprotectDocument());
        tools.getItems().addAll(ocr, redact, find, new SeparatorMenuItem(), protect, unprotect);

        Menu sign = new Menu("Firma");
        MenuItem signItem = new MenuItem("Firmar con certificado…");
        signItem.setOnAction(e -> signDocument());
        MenuItem validateItem = new MenuItem("Validar firmas…");
        validateItem.setOnAction(e -> validateSignatures());
        sign.getItems().addAll(signItem, validateItem);

        Menu view = new Menu("Ver");
        CheckMenuItem darkMode = new CheckMenuItem("Modo oscuro");
        darkMode.setSelected(prefs.getBoolean(PREF_DARK, false));
        darkMode.setOnAction(e -> applyTheme(darkMode.isSelected()));
        view.getItems().add(darkMode);

        Menu help = new Menu("Ayuda");
        MenuItem about = new MenuItem("Acerca de ORS Suite PDF");
        about.setOnAction(e -> showAbout());
        help.getItems().add(about);

        return new MenuBar(file, page, insert, annotate, tools, sign, view, help);
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

    // ---------------------------------------------------------- búsqueda

    private HBox buildSearchBar() {
        searchField = new TextField();
        searchField.setPromptText("Buscar en el documento…");
        searchField.setPrefColumnCount(24);
        searchField.setOnAction(e -> runSearch());
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                toggleSearch();
            }
        });
        Button prev = new Button("◀");
        prev.setOnAction(e -> navigateSearch(-1));
        Button next = new Button("▶");
        next.setOnAction(e -> navigateSearch(1));
        Button close = new Button("✕");
        close.setOnAction(e -> toggleSearch());

        searchBar = new HBox(8, new Label("Buscar:"), searchField, prev, next, searchCount, close);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setPadding(new Insets(6, 12, 6, 12));
        searchBar.setStyle("-fx-background-color: -color-bg-subtle;");
        searchBar.setVisible(false);
        searchBar.setManaged(false);
        return searchBar;
    }

    private void toggleSearch() {
        boolean show = !searchBar.isVisible();
        searchBar.setVisible(show);
        searchBar.setManaged(show);
        if (show) {
            searchField.requestFocus();
            searchField.selectAll();
        } else {
            if (pdfView != null) {
                pdfView.clearSearchMatches();
            }
            searchResults = new ArrayList<>();
            searchCount.setText("0/0");
        }
    }

    private void runSearch() {
        if (!state.hasDocument() || searchField.getText().isBlank()) {
            return;
        }
        PdfDocument document = state.getDocument();
        PdfView view = pdfView;
        AppState target = state;
        String query = searchField.getText();
        statusLabel.setText("Buscando…");
        runBackground(() -> {
            List<SearchService.Match> results = SearchService.find(document, query);
            Platform.runLater(() -> {
                searchResults = results;
                searchIndex = 0;
                if (view != null) {
                    view.setSearchMatches(results);
                }
                if (results.isEmpty()) {
                    searchCount.setText("0/0");
                    statusLabel.setText("Sin coincidencias");
                } else {
                    searchCount.setText("1/" + results.size());
                    target.setCurrentPage(results.get(0).page());
                    statusLabel.setText(results.size() + " coincidencias");
                }
            });
        }, "No se pudo buscar");
    }

    private void navigateSearch(int direction) {
        if (searchResults.isEmpty()) {
            runSearch();
            return;
        }
        searchIndex = (searchIndex + direction + searchResults.size()) % searchResults.size();
        searchCount.setText((searchIndex + 1) + "/" + searchResults.size());
        if (state.hasDocument()) {
            state.setCurrentPage(searchResults.get(searchIndex).page());
        }
    }

    // ------------------------------------------------ diálogos de archivo

    private static final String PREF_LAST_DIR = "lastDirectory";
    private static final String PREF_DARK = "darkMode";
    private final Preferences prefs = Preferences.userNodeForPackage(MainView.class);

    private void applyTheme(boolean dark) {
        Application.setUserAgentStylesheet(dark
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        prefs.putBoolean(PREF_DARK, dark);
        refreshWelcome();
    }

    private File lastDirectory() {
        String path = prefs.get(PREF_LAST_DIR, null);
        if (path == null) {
            return null;
        }
        File dir = new File(path);
        return dir.isDirectory() ? dir : null;
    }

    private void rememberDirectory(File dir) {
        if (dir != null && dir.isDirectory()) {
            prefs.put(PREF_LAST_DIR, dir.getAbsolutePath());
        }
    }

    private static final String PREF_RECENT = "recentFiles";
    private static final int MAX_RECENT = 8;

    private List<String> recentFiles() {
        String stored = prefs.get(PREF_RECENT, "");
        List<String> list = new ArrayList<>();
        for (String line : stored.split("\n")) {
            if (!line.isBlank()) {
                list.add(line);
            }
        }
        return list;
    }

    private void addRecent(Path path) {
        String abs = path.toAbsolutePath().toString();
        List<String> list = recentFiles();
        list.remove(abs);
        list.add(0, abs);
        while (list.size() > MAX_RECENT) {
            list.remove(list.size() - 1);
        }
        prefs.put(PREF_RECENT, String.join("\n", list));
        refreshRecentMenu();
    }

    private void refreshRecentMenu() {
        recentMenu.getItems().clear();
        List<String> list = recentFiles();
        recentMenu.setDisable(list.isEmpty());
        for (String pathString : list) {
            Path path = Path.of(pathString);
            MenuItem item = new MenuItem(path.getFileName().toString());
            item.setOnAction(e -> {
                if (java.nio.file.Files.exists(path)) {
                    loadInBackground(path);
                } else {
                    showError("Abrir reciente", "El archivo ya no existe:\n" + pathString);
                }
            });
            recentMenu.getItems().add(item);
        }
        if (!list.isEmpty()) {
            MenuItem clear = new MenuItem("Vaciar lista");
            clear.setOnAction(e -> {
                prefs.remove(PREF_RECENT);
                refreshRecentMenu();
            });
            recentMenu.getItems().addAll(new SeparatorMenuItem(), clear);
        }
    }

    /** Aplica la última carpeta recordada salvo que ya se haya fijado una. */
    private void applyLastDirectory(FileChooser chooser) {
        File dir = lastDirectory();
        if (dir != null && chooser.getInitialDirectory() == null) {
            chooser.setInitialDirectory(dir);
        }
    }

    private File showOpen(FileChooser chooser) {
        applyLastDirectory(chooser);
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            rememberDirectory(file.getParentFile());
        }
        return file;
    }

    private List<File> showOpenMultiple(FileChooser chooser) {
        applyLastDirectory(chooser);
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files != null && !files.isEmpty()) {
            rememberDirectory(files.get(0).getParentFile());
        }
        return files;
    }

    private File showSave(FileChooser chooser) {
        applyLastDirectory(chooser);
        File file = chooser.showSaveDialog(stage);
        if (file != null) {
            rememberDirectory(file.getParentFile());
        }
        return file;
    }

    private File showDir(DirectoryChooser chooser) {
        File dir = lastDirectory();
        if (dir != null && chooser.getInitialDirectory() == null) {
            chooser.setInitialDirectory(dir);
        }
        File chosen = chooser.showDialog(stage);
        if (chosen != null) {
            rememberDirectory(chosen);
        }
        return chosen;
    }

    // ----------------------------------------------------- apertura/guardado

    private void openDocument() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir PDF");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Documentos PDF", "*.pdf"));
        File selected = showOpen(chooser);
        if (selected == null) {
            return;
        }
        loadInBackground(selected.toPath());
    }

    /** Abre un documento por su ruta (p. ej. desde la línea de comandos). */
    public void open(Path path) {
        loadInBackground(path);
    }

    /** Abre el documento en una pestaña nueva. */
    private void loadInBackground(Path path) {
        loadInBackground(path, null);
    }

    private void loadInBackground(Path path, String password) {
        statusLabel.setText("Abriendo " + path.getFileName() + "…");
        runBackground(() -> {
            PdfDocument doc;
            try {
                doc = password == null ? PdfDocument.open(path) : PdfDocument.open(path, password);
            } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException badPassword) {
                Platform.runLater(() -> {
                    Optional<String> pwd = askPassword("Documento protegido",
                            "Introduce la contraseña de " + path.getFileName() + ":");
                    if (pwd.isPresent() && !pwd.get().isEmpty()) {
                        loadInBackground(path, pwd.get());
                    } else {
                        statusLabel.setText("Apertura cancelada");
                    }
                });
                return;
            }
            PdfDocument opened = doc;
            Platform.runLater(() -> {
                DocumentSession session = new DocumentSession();
                session.state.setDocument(opened);
                sessions.put(session.tab, session);
                updateTabTitle(session);
                documentsPane.getTabs().add(session.tab);
                documentsPane.getSelectionModel().select(session.tab);
                addRecent(path);
                statusLabel.setText(path.getFileName() + "  ·  " + opened.pageCount() + " páginas");
            });
        }, "No se pudo abrir el PDF");
    }

    private void protectDocument() {
        if (!state.hasDocument()) {
            return;
        }
        Optional<String> password = askPassword("Proteger con contraseña",
                "Contraseña necesaria para abrir el documento:");
        if (password.isEmpty() || password.get().isEmpty()) {
            return;
        }
        try {
            SecurityService.protect(state.getDocument(), password.get());
            state.markMutated();
            statusLabel.setText("Protección aplicada — se cifrará al guardar");
        } catch (Exception ex) {
            showError("No se pudo proteger", ex.getMessage());
        }
    }

    private void unprotectDocument() {
        if (!state.hasDocument()) {
            return;
        }
        SecurityService.removeProtection(state.getDocument());
        state.markMutated();
        statusLabel.setText("Se quitará la protección al guardar");
    }

    private Optional<String> askPassword(String title, String header) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.initOwner(stage);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        PasswordField field = new PasswordField();
        field.setPromptText("Contraseña");
        HBox box = new HBox(field);
        box.setPadding(new Insets(16));
        dialog.getDialogPane().setContent(box);
        Platform.runLater(field::requestFocus);
        dialog.setResultConverter(b -> b == ButtonType.OK ? field.getText() : null);
        return dialog.showAndWait();
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
        File target = showSave(chooser);
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
        List<File> files = choosePdfFiles("Selecciona los PDF a unir");
        if (files == null || files.isEmpty()) {
            return;
        }
        List<Path> initial = new ArrayList<>(files.stream().map(File::toPath).toList());
        Optional<List<Path>> ordered = reviewMerge(initial);
        if (ordered.isEmpty()) {
            return;
        }
        List<Path> inputs = ordered.get();
        if (inputs.size() < 2) {
            showError("Unir PDF", "Se necesitan al menos dos documentos para unir.");
            return;
        }
        FileChooser saveChooser = new FileChooser();
        saveChooser.setTitle("Guardar PDF unido");
        saveChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Documentos PDF", "*.pdf"));
        saveChooser.setInitialFileName("unido.pdf");
        File output = showSave(saveChooser);
        if (output == null) {
            return;
        }
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

    private List<File> choosePdfFiles(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Documentos PDF", "*.pdf"));
        return showOpenMultiple(chooser);
    }

    /** Pantalla de revisión: reordenar, quitar o añadir documentos a unir. */
    private Optional<List<Path>> reviewMerge(List<Path> initial) {
        ObservableList<Path> items = FXCollections.observableArrayList(initial);
        Map<Path, Integer> pageCounts = new HashMap<>();
        for (Path path : initial) {
            pageCounts.put(path, safePageCount(path));
        }

        ListView<Path> list = new ListView<>(items);
        list.setPrefSize(460, 320);
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Path path, boolean empty) {
                super.updateItem(path, empty);
                if (empty || path == null) {
                    setText(null);
                    return;
                }
                int count = pageCounts.getOrDefault(path, -1);
                setText((getIndex() + 1) + ".  " + path.getFileName()
                        + (count >= 0 ? "   ·   " + count + " págs" : "   ·   (no legible)"));
            }
        });

        Button up = new Button("▲ Subir");
        Button down = new Button("▼ Bajar");
        Button remove = new Button("Quitar");
        Button add = new Button("Añadir…");
        for (Button b : new Button[]{up, down, remove, add}) {
            b.setMaxWidth(Double.MAX_VALUE);
        }
        up.setOnAction(e -> {
            int i = list.getSelectionModel().getSelectedIndex();
            if (i > 0) {
                items.add(i - 1, items.remove(i));
                list.getSelectionModel().select(i - 1);
            }
        });
        down.setOnAction(e -> {
            int i = list.getSelectionModel().getSelectedIndex();
            if (i >= 0 && i < items.size() - 1) {
                items.add(i + 1, items.remove(i));
                list.getSelectionModel().select(i + 1);
            }
        });
        remove.setOnAction(e -> {
            int i = list.getSelectionModel().getSelectedIndex();
            if (i >= 0) {
                items.remove(i);
            }
        });
        add.setOnAction(e -> {
            List<File> more = choosePdfFiles("Añadir documentos");
            if (more != null) {
                for (File file : more) {
                    Path path = file.toPath();
                    pageCounts.put(path, safePageCount(path));
                    items.add(path);
                }
            }
        });

        VBox buttons = new VBox(8, up, down, remove, new Separator(), add);
        buttons.setPrefWidth(120);
        HBox content = new HBox(12, list, buttons);
        HBox.setHgrow(list, Priority.ALWAYS);
        content.setPadding(new Insets(12));

        Dialog<List<Path>> dialog = new Dialog<>();
        dialog.setTitle("Unir PDF — revisión");
        dialog.setHeaderText("Ordena los documentos (se unirán de arriba a abajo)");
        dialog.initOwner(stage);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(b -> b == ButtonType.OK ? new ArrayList<>(items) : null);
        return dialog.showAndWait();
    }

    private int safePageCount(Path path) {
        try (PdfDocument doc = PdfDocument.open(path)) {
            return doc.pageCount();
        } catch (Exception ex) {
            return -1;
        }
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
        File output = showSave(chooser);
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

    private void insertImage() {
        if (!state.hasDocument()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar imagen o sello");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg"));
        File imageFile = showOpen(chooser);
        if (imageFile == null) {
            return;
        }
        Image preview = new Image(imageFile.toURI().toString());
        if (preview.isError() || preview.getWidth() <= 0) {
            showError("Insertar imagen", "No se pudo leer la imagen seleccionada.");
            return;
        }
        double aspect = preview.getHeight() / preview.getWidth();

        Optional<Placement> placement = askPlacement();
        if (placement.isEmpty()) {
            return;
        }

        int page = state.getCurrentPage();
        PdfDocument document = state.getDocument();
        float pageW = document.pageWidth(page);
        float pageH = document.pageHeight(page);
        float margin = 36f; // 0,5 pulgadas
        float width = (float) placement.get().width();
        float height = (float) (width * aspect);

        float x;
        float y;
        switch (placement.get().corner()) {
            case "Inferior izquierda" -> { x = margin; y = margin; }
            case "Superior izquierda" -> { x = margin; y = pageH - margin - height; }
            case "Superior derecha" -> { x = pageW - margin - width; y = pageH - margin - height; }
            case "Centro" -> { x = (pageW - width) / 2; y = (pageH - height) / 2; }
            default -> { x = pageW - margin - width; y = margin; } // Inferior derecha
        }

        try {
            StampService.stampImage(document.pdbox(), page, imageFile.toPath(), x, y, width, height);
            state.markMutated();
            statusLabel.setText("Imagen insertada en la página " + (page + 1));
        } catch (Exception ex) {
            showError("No se pudo insertar la imagen", ex.getMessage());
        }
    }

    private void insertText() {
        if (!state.hasDocument()) {
            return;
        }
        Optional<TextContent> content = askText();
        if (content.isEmpty()) {
            return;
        }
        int page = state.getCurrentPage();
        PdfDocument document = state.getDocument();
        float pageW = document.pageWidth(page);
        float pageH = document.pageHeight(page);
        float margin = 36f;
        float fontSize = content.get().fontSize();
        String text = content.get().text();
        // Estimación del recuadro para colocar según la esquina elegida.
        float approxWidth = Math.min(pageW - 2 * margin, fontSize * 0.5f * text.length());

        float x;
        float y;
        switch (content.get().corner()) {
            case "Inferior derecha" -> { x = pageW - margin - approxWidth; y = margin; }
            case "Superior izquierda" -> { x = margin; y = pageH - margin - fontSize; }
            case "Superior derecha" -> { x = pageW - margin - approxWidth; y = pageH - margin - fontSize; }
            case "Centro" -> { x = (pageW - approxWidth) / 2; y = pageH / 2; }
            default -> { x = margin; y = margin; } // Inferior izquierda
        }

        try {
            StampService.stampText(document.pdbox(), page, text, x, y, fontSize);
            state.markMutated();
            statusLabel.setText("Texto insertado en la página " + (page + 1));
        } catch (Exception ex) {
            showError("No se pudo insertar el texto", ex.getMessage());
        }
    }

    private void ocrCurrentPage() {
        if (!state.hasDocument()) {
            return;
        }
        Path dataPath = OcrService.defaultDataPath();
        if (!OcrService.isDataAvailable(dataPath)) {
            showError("OCR no disponible",
                    "No se encontraron datos de idioma (tessdata) en:\n"
                            + dataPath.toAbsolutePath()
                            + "\n\nDescarga los modelos de idioma y colócalos ahí (ver README).");
            return;
        }
        int page = state.getCurrentPage();
        statusLabel.setText("Reconociendo texto (OCR) de la página " + (page + 1) + "…");
        runBackground(() -> {
            String text = OcrService.ocrPage(
                    state.getDocument(), page, OcrService.DEFAULT_LANGUAGES, dataPath);
            Platform.runLater(() -> {
                statusLabel.setText("OCR completado (página " + (page + 1) + ")");
                showOcrResult(page, text);
            });
        }, "No se pudo ejecutar el OCR");
    }

    private void showOcrResult(int page, String text) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Texto reconocido");
        dialog.setHeaderText("OCR de la página " + (page + 1));
        dialog.initOwner(stage);

        TextArea area = new TextArea(text.isEmpty() ? "(no se reconoció texto)" : text);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefSize(560, 400);
        dialog.getDialogPane().setContent(area);

        ButtonType copy = new ButtonType("Copiar", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(copy, ButtonType.CLOSE);
        dialog.getDialogPane().lookupButton(copy).addEventFilter(
                javafx.event.ActionEvent.ACTION, event -> {
                    ClipboardContent clip = new ClipboardContent();
                    clip.putString(text);
                    Clipboard.getSystemClipboard().setContent(clip);
                    statusLabel.setText("Texto copiado al portapapeles");
                    event.consume();
                });
        dialog.showAndWait();
    }

    // ------------------------------------------------------- exportación

    private Menu buildExportMenu() {
        Menu export = new Menu("Exportar");
        MenuItem txt = new MenuItem("Texto (.txt)…");
        txt.setOnAction(e -> exportAsText());
        MenuItem img = new MenuItem("Imágenes (PNG)…");
        img.setOnAction(e -> exportAsImages());
        export.getItems().addAll(txt, img, new SeparatorMenuItem());

        boolean office = ExportService.isOfficeConversionAvailable();
        for (String[] fmt : new String[][]{
                {"Word (.docx)…", "docx"}, {"PowerPoint (.pptx)…", "pptx"},
                {"OpenDocument texto (.odt)…", "odt"}, {"RTF (.rtf)…", "rtf"}}) {
            MenuItem item = new MenuItem(fmt[0]);
            item.setDisable(!office);
            item.setOnAction(e -> exportWithLibreOffice(fmt[1]));
            export.getItems().add(item);
        }
        if (!office) {
            MenuItem hint = new MenuItem("(instala LibreOffice para DOCX/PPTX/ODT)");
            hint.setDisable(true);
            export.getItems().add(hint);
        }
        return export;
    }

    private void exportAsText() {
        if (!state.hasDocument()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar a texto");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texto", "*.txt"));
        chooser.setInitialFileName(baseName() + ".txt");
        File target = showSave(chooser);
        if (target == null) {
            return;
        }
        PdfDocument document = state.getDocument();
        statusLabel.setText("Exportando texto…");
        runBackground(() -> {
            ExportService.exportText(document, target.toPath());
            Platform.runLater(() -> statusLabel.setText("Texto exportado: " + target.getName()));
        }, "No se pudo exportar el texto");
    }

    private void exportAsImages() {
        if (!state.hasDocument()) {
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Carpeta para las imágenes");
        File dir = showDir(chooser);
        if (dir == null) {
            return;
        }
        PdfDocument document = state.getDocument();
        String base = baseName();
        statusLabel.setText("Exportando imágenes…");
        runBackground(() -> {
            int count = ExportService.exportImages(document, dir.toPath(), "png", 150f, base);
            Platform.runLater(() -> statusLabel.setText(count + " imágenes exportadas en " + dir.getName()));
        }, "No se pudieron exportar las imágenes");
    }

    private void exportWithLibreOffice(String format) {
        if (!state.hasDocument()) {
            return;
        }
        Path source = state.getDocument().source();
        if (source == null || state.isDirty()) {
            showError("Exportar", "Guarda el documento antes de convertirlo a " + format + ".");
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Carpeta de destino");
        chooser.setInitialDirectory(source.getParent().toFile());
        File dir = showDir(chooser);
        if (dir == null) {
            return;
        }
        statusLabel.setText("Convirtiendo a " + format + " con LibreOffice…");
        runBackground(() -> {
            Path result = ExportService.convertWithLibreOffice(source, format, dir.toPath());
            Platform.runLater(() -> statusLabel.setText("Exportado: " + result.getFileName()));
        }, "No se pudo convertir con LibreOffice");
    }

    private String baseName() {
        Path source = state.hasDocument() ? state.getDocument().source() : null;
        if (source == null) {
            return "documento";
        }
        return source.getFileName().toString().replaceFirst("(?i)\\.pdf$", "");
    }

    // --------------------------------------------------------- anotaciones

    @FunctionalInterface
    private interface RegionAction {
        void apply(PdfDocument document, PdfView.PageRegion region) throws Exception;
    }

    private void annotate(String successMessage, RegionAction action) {
        if (!state.hasDocument()) {
            return;
        }
        statusLabel.setText("Dibuja la zona a anotar sobre la página…");
        pdfView.beginRegionSelection(region -> {
            if (region == null) {
                statusLabel.setText("Anotación cancelada");
                return;
            }
            try {
                action.apply(state.getDocument(), region);
                state.markMutated();
                statusLabel.setText(successMessage);
            } catch (Exception ex) {
                showError("No se pudo anotar", ex.getMessage());
            }
        });
    }

    private static final float[] DRAW_COLOR = {0.85f, 0.15f, 0.15f};

    private void annotateFreehand() {
        if (!state.hasDocument()) {
            return;
        }
        statusLabel.setText("Dibuja a mano alzada sobre la página…");
        pdfView.beginFreehand(path -> {
            if (path == null) {
                statusLabel.setText("Dibujo cancelado");
                return;
            }
            try {
                AnnotationService.freehand(state.getDocument(), path.page(), path.points(), DRAW_COLOR, 2f);
                state.markMutated();
                statusLabel.setText("Trazo añadido");
            } catch (Exception ex) {
                showError("No se pudo dibujar", ex.getMessage());
            }
        });
    }

    private void annotateArrow() {
        if (!state.hasDocument()) {
            return;
        }
        statusLabel.setText("Arrastra para dibujar una flecha…");
        pdfView.beginArrow(line -> {
            if (line == null) {
                statusLabel.setText("Flecha cancelada");
                return;
            }
            try {
                AnnotationService.arrow(state.getDocument(), line.page(),
                        line.x1(), line.y1(), line.x2(), line.y2(), DRAW_COLOR, 2f);
                state.markMutated();
                statusLabel.setText("Flecha añadida");
            } catch (Exception ex) {
                showError("No se pudo dibujar la flecha", ex.getMessage());
            }
        });
    }

    private void annotateNote() {
        if (!state.hasDocument()) {
            return;
        }
        statusLabel.setText("Dibuja dónde colocar la nota…");
        pdfView.beginRegionSelection(region -> {
            if (region == null) {
                statusLabel.setText("Nota cancelada");
                return;
            }
            javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
            dialog.setTitle("Nota");
            dialog.setHeaderText("Texto de la nota");
            dialog.setContentText("Nota:");
            dialog.initOwner(stage);
            Optional<String> text = dialog.showAndWait();
            if (text.isEmpty() || text.get().isBlank()) {
                statusLabel.setText("Nota cancelada");
                return;
            }
            try {
                AnnotationService.note(state.getDocument(), region.page(),
                        region.x(), region.y(), text.get().trim());
                state.markMutated();
                statusLabel.setText("Nota añadida");
            } catch (Exception ex) {
                showError("No se pudo añadir la nota", ex.getMessage());
            }
        });
    }

    // ------------------------------------------------ marca de agua / nº

    private void addWatermark() {
        if (!state.hasDocument()) {
            return;
        }
        javafx.scene.control.TextInputDialog dialog =
                new javafx.scene.control.TextInputDialog("CONFIDENCIAL");
        dialog.setTitle("Marca de agua");
        dialog.setHeaderText("Texto de la marca de agua (se aplica a todas las páginas)");
        dialog.setContentText("Texto:");
        dialog.initOwner(stage);
        Optional<String> text = dialog.showAndWait();
        if (text.isEmpty() || text.get().isBlank()) {
            return;
        }
        try {
            WatermarkService.watermark(state.getDocument(), text.get().trim());
            state.markMutated();
            statusLabel.setText("Marca de agua añadida");
        } catch (Exception ex) {
            showError("No se pudo añadir la marca de agua", ex.getMessage());
        }
    }

    private void numberPages() {
        if (!state.hasDocument()) {
            return;
        }
        try {
            WatermarkService.numberPages(state.getDocument());
            state.markMutated();
            statusLabel.setText("Páginas numeradas");
        } catch (Exception ex) {
            showError("No se pudieron numerar las páginas", ex.getMessage());
        }
    }

    // --------------------------------------------------------- redacción

    private void redactZone() {
        if (!state.hasDocument()) {
            return;
        }
        statusLabel.setText("Dibuja la zona a redactar sobre la página…");
        pdfView.beginRegionSelection(region -> {
            if (region == null) {
                statusLabel.setText("Redacción cancelada");
                return;
            }
            try {
                RedactionService.redact(state.getDocument(), region.page(),
                        List.of(new double[]{region.x(), region.y(), region.width(), region.height()}));
                state.markMutated();
                statusLabel.setText("Zona redactada en la página " + (region.page() + 1)
                        + " (la página pasa a ser imagen para que el contenido no sea recuperable)");
            } catch (Exception ex) {
                showError("No se pudo redactar", ex.getMessage());
            }
        });
    }

    // --------------------------------------------------------- impresión

    private void printDocument() {
        if (!state.hasDocument()) {
            return;
        }
        PdfDocument document = state.getDocument();
        statusLabel.setText("Abriendo el diálogo de impresión…");
        runBackground(() -> {
            java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
            job.setJobName(document.source() != null
                    ? document.source().getFileName().toString() : "ORS Suite PDF");
            job.setPageable(new org.apache.pdfbox.printing.PDFPageable(document.pdbox()));
            // Diálogo nativo con selección de impresora (incluye Microsoft Print to PDF).
            if (job.printDialog()) {
                job.print();
                Platform.runLater(() -> statusLabel.setText("Documento enviado a la impresora"));
            } else {
                Platform.runLater(() -> statusLabel.setText("Impresión cancelada"));
            }
        }, "No se pudo imprimir el documento");
    }

    // ------------------------------------------------------------- firma

    private void validateSignatures() {
        if (!state.hasDocument()) {
            return;
        }
        Path source = state.getDocument().source();
        if (source == null) {
            showError("Validar firmas", "Guarda el documento antes de validar sus firmas.");
            return;
        }
        statusLabel.setText("Validando firmas…");
        runBackground(() -> {
            List<SignatureValidationService.SignatureInfo> signatures =
                    SignatureValidationService.validate(source);
            Platform.runLater(() -> {
                if (signatures.isEmpty()) {
                    statusLabel.setText("El documento no tiene firmas");
                    Alert alert = new Alert(AlertType.INFORMATION);
                    alert.setTitle("Validar firmas");
                    alert.setHeaderText(null);
                    alert.setContentText("El documento no contiene firmas digitales.");
                    alert.initOwner(stage);
                    alert.showAndWait();
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (SignatureValidationService.SignatureInfo s : signatures) {
                    sb.append("Firma ").append(s.index()).append('\n')
                            .append("  Firmante:  ").append(s.signedBy()).append('\n')
                            .append("  Formato:   ").append(s.format()).append('\n')
                            .append("  Resultado: ").append(s.indication()).append('\n')
                            .append("  Fecha:     ").append(s.signingTime()).append("\n\n");
                }
                statusLabel.setText(signatures.size() + " firma(s) encontrada(s)");
                showReport("Firmas del documento", sb.toString().strip());
            });
        }, "No se pudieron validar las firmas");
    }

    private void showReport(String title, String body) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.initOwner(stage);
        TextArea area = new TextArea(body);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefSize(520, 320);
        dialog.getDialogPane().setContent(area);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void signDocument() {
        if (!state.hasDocument()) {
            return;
        }
        Path source = state.getDocument().source();
        if (source == null || state.isDirty()) {
            showError("Firmar documento",
                    "Guarda los cambios del documento (Ctrl+S) antes de firmarlo.");
            return;
        }
        Optional<SignOptions> options = askSigningOptions();
        if (options.isEmpty()) {
            return;
        }
        SignOptions opts = options.get();

        // Firma con el almacén de certificados de Windows. Cubre tanto los
        // certificados software instalados como el DNIe/tarjeta insertada (sus
        // certificados aparecen en el almacén mediante el minidriver, y el PIN
        // lo solicita el propio sistema al firmar).
        SignatureTokenConnection token;
        try {
            token = SigningTokens.windowsStore();
        } catch (Exception ex) {
            showError("No se pudo acceder al almacén de Windows", ex.getMessage());
            return;
        }

        DSSPrivateKeyEntry key;
        try {
            List<DSSPrivateKeyEntry> keys = token.getKeys();
            if (keys.isEmpty()) {
                closeQuietly(token);
                showError("Firmar", "No hay certificados en el almacén de Windows.\n"
                        + "Si vas a usar el DNIe, inserta la tarjeta en el lector.");
                return;
            }
            key = keys.size() == 1 ? keys.get(0) : chooseKey(keys);
            if (key == null) {
                closeQuietly(token);
                return;
            }
        } catch (Exception ex) {
            closeQuietly(token);
            showError("No se pudieron leer los certificados", ex.getMessage());
            return;
        }

        Path output = deriveSignedPath(source);
        DSSPrivateKeyEntry chosenKey = key;

        if (opts.visible()) {
            statusLabel.setText("Dibuja el recuadro de la firma sobre la página…");
            pdfView.beginRegionSelection(region -> {
                if (region == null) {
                    closeQuietly(token);
                    statusLabel.setText("Firma cancelada");
                    return;
                }
                VisibleSignature visible = new VisibleSignature(region.page(),
                        (float) region.x(), (float) region.y(),
                        (float) region.width(), (float) region.height(), null);
                runSign(token, chosenKey,
                        new SignSpec(source, output, opts.tsaUrl(), opts.reason(), opts.location(), visible));
            });
        } else {
            runSign(token, chosenKey,
                    new SignSpec(source, output, opts.tsaUrl(), opts.reason(), opts.location(), null));
        }
    }

    private void runSign(SignatureTokenConnection token, DSSPrivateKeyEntry key, SignSpec spec) {
        statusLabel.setText("Firmando… (el sistema puede pedir el PIN; puede tardar por el TSA)");
        runBackground(() -> {
            try {
                SignResult result = new PAdESSigner().sign(token, key, spec);
                Platform.runLater(() -> {
                    statusLabel.setText("Firmado (" + result.levelDescription() + "): "
                            + spec.output().getFileName());
                    if (confirm("Firma completada",
                            "Documento firmado como " + result.levelDescription()
                                    + ".\n\n¿Abrir el documento firmado?")) {
                        loadInBackground(spec.output());
                    }
                });
            } finally {
                closeQuietly(token);
            }
        }, "No se pudo firmar el documento");
    }

    private void closeQuietly(SignatureTokenConnection token) {
        try {
            token.close();
        } catch (Exception ignored) {
            // El cierre del token no debe enmascarar otros errores.
        }
    }

    private DSSPrivateKeyEntry chooseKey(List<DSSPrivateKeyEntry> keys) {
        Map<String, DSSPrivateKeyEntry> byLabel = new LinkedHashMap<>();
        int i = 1;
        for (DSSPrivateKeyEntry key : keys) {
            byLabel.put((i++) + ". " + PAdESSigner.commonName(key), key);
        }
        List<String> labels = new ArrayList<>(byLabel.keySet());
        ChoiceDialog<String> dialog = new ChoiceDialog<>(labels.get(0), labels);
        dialog.setTitle("Seleccionar certificado");
        dialog.setHeaderText("Elige el certificado con el que firmar");
        dialog.setContentText("Certificado:");
        dialog.initOwner(stage);
        Optional<String> chosen = dialog.showAndWait();
        return chosen.map(byLabel::get).orElse(null);
    }

    private Path deriveSignedPath(Path source) {
        String name = source.getFileName().toString();
        String base = name.toLowerCase().endsWith(".pdf")
                ? name.substring(0, name.length() - 4) : name;
        return source.resolveSibling(base + "-firmado.pdf");
    }

    private Optional<SignOptions> askSigningOptions() {
        Dialog<SignOptions> dialog = new Dialog<>();
        dialog.setTitle("Firmar con certificado");
        dialog.setHeaderText("Firma digital PAdES con el almacén de certificados de Windows");
        dialog.initOwner(stage);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField tsaField = new TextField("https://freetsa.org/tsr");
        tsaField.setPromptText("URL de TSA (vacío = sin sello de tiempo)");
        TextField reasonField = new TextField();
        reasonField.setPromptText("Motivo (opcional)");
        TextField locationField = new TextField();
        locationField.setPromptText("Lugar (opcional)");
        CheckBox visibleBox = new CheckBox("Firma visible en el documento (seleccionar recuadro)");

        Label hint = new Label("Se usará un certificado del almacén de Windows. "
                + "Para el DNIe, inserta la tarjeta; el sistema pedirá el PIN al firmar.");
        hint.setWrapText(true);
        hint.getStyleClass().add("text-muted");
        hint.setMaxWidth(360);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.add(hint, 0, 0, 2, 1);
        grid.add(new Label("TSA:"), 0, 1);
        grid.add(tsaField, 1, 1);
        grid.add(new Label("Motivo:"), 0, 2);
        grid.add(reasonField, 1, 2);
        grid.add(new Label("Lugar:"), 0, 3);
        grid.add(locationField, 1, 3);
        grid.add(visibleBox, 1, 4);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new SignOptions(tsaField.getText(), reasonField.getText(),
                        locationField.getText(), visibleBox.isSelected())
                : null);
        return dialog.showAndWait();
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

    private Optional<Placement> askPlacement() {
        Dialog<Placement> dialog = new Dialog<>();
        dialog.setTitle("Insertar imagen");
        dialog.setHeaderText("Posición y tamaño del sello en la página actual");
        dialog.initOwner(stage);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> corner = new ComboBox<>();
        corner.getItems().addAll("Inferior derecha", "Inferior izquierda",
                "Superior derecha", "Superior izquierda", "Centro");
        corner.setValue("Inferior derecha");
        corner.setMaxWidth(Double.MAX_VALUE);

        Spinner<Integer> width = new Spinner<>(30, 600, 150, 10);
        width.setEditable(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.add(new Label("Posición:"), 0, 0);
        grid.add(corner, 1, 0);
        grid.add(new Label("Anchura (pt):"), 0, 1);
        grid.add(width, 1, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button ->
                button == ButtonType.OK ? new Placement(corner.getValue(), width.getValue()) : null);
        return dialog.showAndWait();
    }

    private Optional<TextContent> askText() {
        Dialog<TextContent> dialog = new Dialog<>();
        dialog.setTitle("Insertar texto");
        dialog.setHeaderText("Texto a insertar en la página actual");
        dialog.initOwner(stage);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField text = new TextField();
        text.setPromptText("Escribe el texto…");
        text.setPrefColumnCount(24);
        Spinner<Integer> size = new Spinner<>(6, 96, 18, 1);
        size.setEditable(true);
        ComboBox<String> corner = new ComboBox<>();
        corner.getItems().addAll("Inferior izquierda", "Inferior derecha",
                "Superior izquierda", "Superior derecha", "Centro");
        corner.setValue("Inferior izquierda");
        corner.setMaxWidth(Double.MAX_VALUE);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.add(new Label("Texto:"), 0, 0);
        grid.add(text, 1, 0);
        grid.add(new Label("Tamaño (pt):"), 0, 1);
        grid.add(size, 1, 1);
        grid.add(new Label("Posición:"), 0, 2);
        grid.add(corner, 1, 2);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK && !text.getText().isBlank()) {
                return new TextContent(text.getText(), size.getValue(), corner.getValue());
            }
            return null;
        });
        return dialog.showAndWait();
    }

    private boolean confirmDiscard(AppState target) {
        if (!target.isDirty()) {
            return true;
        }
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("Cambios sin guardar");
        alert.setHeaderText("El documento tiene cambios sin guardar.");
        alert.setContentText("¿Descartar los cambios de esta pestaña?");
        alert.initOwner(stage);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private boolean confirmCloseAll() {
        boolean anyDirty = sessions.values().stream().anyMatch(s -> s.state.isDirty());
        if (!anyDirty) {
            return true;
        }
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("Cambios sin guardar");
        alert.setHeaderText("Hay documentos con cambios sin guardar.");
        alert.setContentText("¿Salir sin guardar?");
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
                OCR y firma digital PAdES.

                © 2026 ORS Consulting""");
        javafx.scene.image.ImageView logo = new javafx.scene.image.ImageView(Branding.symbol());
        logo.setFitWidth(64);
        logo.setPreserveRatio(true);
        alert.setGraphic(logo);
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

    // --------------------------------------------------------- pestañas

    private boolean isActive(DocumentSession session) {
        Tab selected = documentsPane.getSelectionModel().getSelectedItem();
        return selected != null && sessions.get(selected) == session;
    }

    private void updateTabTitle(DocumentSession session) {
        Path source = session.state.hasDocument() ? session.state.getDocument().source() : null;
        String name = source != null ? source.getFileName().toString() : "documento";
        session.tab.setText(name + (session.state.isDirty() ? " *" : ""));
    }

    private void onTabClosed(DocumentSession session) {
        sessions.remove(session.tab);
        PdfDocument document = session.state.getDocument();
        if (document != null) {
            try {
                document.close();
            } catch (Exception ignored) {
                // El cierre del documento no debe interrumpir el cierre de la pestaña.
            }
        }
    }

    /**
     * Estado y vistas de un documento abierto en su pestaña. Cada sesión tiene
     * su propio {@link AppState}, visor y paneles laterales.
     */
    private final class DocumentSession {

        private final AppState state = new AppState();
        private final PdfView pdfView = new PdfView(state);
        private final Tab tab = new Tab();

        DocumentSession() {
            Tab pages = new Tab("Páginas", new ThumbnailPanel(state));
            pages.setClosable(false);
            Tab bookmarks = new Tab("Marcadores", new BookmarkPanel(state));
            bookmarks.setClosable(false);
            Tab form = new Tab("Formulario", new FormPanel(state));
            form.setClosable(false);
            TabPane sidebar = new TabPane(pages, bookmarks, form);
            sidebar.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

            SplitPane split = new SplitPane(sidebar, pdfView);
            split.setDividerPositions(0.24);
            SplitPane.setResizableWithParent(sidebar, Boolean.FALSE);
            tab.setContent(split);

            state.documentProperty().addListener((o, a, b) -> {
                updateTabTitle(this);
                if (isActive(this)) {
                    refreshControls();
                    updateTitle();
                }
            });
            state.currentPageProperty().addListener((o, a, b) -> {
                if (isActive(this)) {
                    refreshControls();
                }
            });
            state.zoomProperty().addListener((o, a, b) -> {
                if (isActive(this)) {
                    refreshControls();
                }
            });
            state.revisionProperty().addListener((o, a, b) -> {
                if (isActive(this)) {
                    refreshControls();
                }
            });
            state.dirtyProperty().addListener((o, a, b) -> {
                updateTabTitle(this);
                if (isActive(this)) {
                    updateTitle();
                }
            });

            tab.setOnCloseRequest(event -> {
                if (!confirmDiscard(state)) {
                    event.consume();
                }
            });
            tab.setOnClosed(event -> onTabClosed(this));
        }
    }

    /** Resultado del diálogo de inserción de imagen: esquina y anchura (pt). */
    private record Placement(String corner, double width) {
    }

    /** Resultado del diálogo de inserción de texto: contenido, tamaño y esquina. */
    private record TextContent(String text, int fontSize, String corner) {
    }

    /** Opciones recogidas en el diálogo de firma. */
    private record SignOptions(String tsaUrl, String reason, String location, boolean visible) {
    }
}
