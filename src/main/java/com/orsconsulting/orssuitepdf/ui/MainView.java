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
import com.orsconsulting.orssuitepdf.signing.CertificateInfo;
import com.orsconsulting.orssuitepdf.signing.PAdESSigner;
import com.orsconsulting.orssuitepdf.signing.SignResult;
import com.orsconsulting.orssuitepdf.signing.SignSpec;
import com.orsconsulting.orssuitepdf.signing.SignatureAppearance;
import com.orsconsulting.orssuitepdf.signing.SignatureValidationService;
import com.orsconsulting.orssuitepdf.signing.SigningTokens;
import com.orsconsulting.orssuitepdf.signing.VisibleSignature;

import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.SignatureTokenConnection;

import org.controlsfx.control.Notifications;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

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
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Pair;

/**
 * Ventana principal de la aplicación, según el lenguaje visual «Cobalto
 * flotante»: barra superior unificada (marca, chips de documento, control
 * segmentado de secciones y búsqueda), fila de comandos silenciosa y lienzo
 * redondeado sobre el que flotan el panel de miniaturas, el riel de
 * herramientas y la píldora de navegación. Conecta todo con {@link AppState}.
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
    private final Label statusLabel = new Label("");

    private final MenuButton recentButton = new MenuButton("Recientes", FontIcon.of(Feather.CLOCK));

    private Button themeButton;
    private StackPane markHolder;
    private Label appNameLabel;
    /** Chips de documento de la barra superior (uno por pestaña). */
    private final HBox docChips = new HBox(8);
    private final Map<String, ToggleButton> segButtons = new LinkedHashMap<>();

    /** Fila de comandos: su contenido cambia con la sección del segmentado. */
    private final HBox sectionBox = new HBox(2);
    /** Botones de cada sección, creados una vez y reutilizados. */
    private final Map<String, List<javafx.scene.Node>> sectionItems = new LinkedHashMap<>();
    private Label savedIndicator;

    private CommandPalette palette;

    private Button prevButton;
    private Button nextButton;
    private Button saveButton;
    private Button rotateLeftButton;
    private Button rotateRightButton;
    private Button deleteButton;
    private HBox navPill;
    private VBox toolRail;

    /** Hoja de estilos de marca, compartida por la ventana y los diálogos. */
    static final String APP_CSS =
            MainView.class.getResource("/css/app.css").toExternalForm();

    public MainView(Stage stage) {
        this.stage = stage;
        root.getStylesheets().add(APP_CSS);
        installDialogTheming();
        buildSections();
        root.setTop(new VBox(buildTopBar(), buildCommandRow()));
        root.setCenter(buildWorkspace());
        showSection("doc");
        registerAccelerators();

        documentsPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            DocumentSession session = newTab == null ? null : sessions.get(newTab);
            state = session != null ? session.state : emptyState;
            pdfView = session != null ? session.pdfView : null;
            refreshControls();
            refreshDocChips();
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

    // ------------------------------------------------------------ workspace

    private final StackPane workspace = new StackPane();

    private Region buildWorkspace() {
        documentsPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        documentsPane.getStyleClass().add("doc-tabs");
        documentsPane.getTabs().addListener(
                (javafx.collections.ListChangeListener<Tab>) c -> {
                    refreshWelcome();
                    refreshDocChips();
                });

        toolRail = buildToolRail();
        StackPane.setAlignment(toolRail, Pos.CENTER_RIGHT);
        StackPane.setMargin(toolRail, new Insets(0, 28, 0, 0));

        navPill = buildNavPill();
        StackPane.setAlignment(navPill, Pos.BOTTOM_CENTER);
        StackPane.setMargin(navPill, new Insets(0, 0, 30, 0));

        palette = new CommandPalette(buildCommands(), () -> state, this::showDocumentMatch);

        workspace.getChildren().addAll(documentsPane, toolRail, navPill, palette.getOverlay());
        refreshWelcome();
        return workspace;
    }

    /** Muestra la pantalla de inicio cuando no hay documentos abiertos. */
    private void refreshWelcome() {
        workspace.getChildren().removeIf(node -> node instanceof WelcomePane);
        boolean empty = documentsPane.getTabs().isEmpty();
        documentsPane.setVisible(!empty);
        toolRail.setVisible(!empty);
        navPill.setVisible(!empty);
        if (empty) {
            List<Path> recent = recentFiles().stream().map(Path::of).toList();
            WelcomePane welcome = new WelcomePane(this::openDocument, this::quickAction,
                    recent, this::loadInBackground);
            workspace.getChildren().add(1, welcome);
        }
    }

    /** Acción rápida desde la bienvenida: algunas piden antes abrir un PDF. */
    private void quickAction(String key) {
        switch (key) {
            case "merge" -> mergeDocuments();
            case "split" -> openThen(this::extractRange);
            case "sign" -> openThen(this::signDocument);
            case "protect" -> openThen(this::protectDocument);
            default -> openDocument();
        }
    }

    /** Abre un PDF elegido por el usuario y, al cargarlo, ejecuta la acción. */
    private void openThen(Runnable action) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir PDF");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Documentos PDF", "*.pdf"));
        File selected = showOpen(chooser);
        if (selected != null) {
            loadInBackground(selected.toPath(), null, action);
        }
    }

    // ------------------------------------------- barra superior + comandos

    private static FontIcon fi(Feather ikon) {
        return FontIcon.of(ikon);
    }

    /** Barra superior: marca, chips de documento, segmentado y búsqueda. */
    private Region buildTopBar() {
        boolean dark = prefs.getBoolean(PREF_DARK, false);
        markHolder = new StackPane(Branding.mark(22, dark));

        appNameLabel = new Label(APP_NAME);
        appNameLabel.getStyleClass().add("app-name");

        docChips.setAlignment(Pos.CENTER_LEFT);

        Region growLeft = new Region();
        HBox.setHgrow(growLeft, Priority.ALWAYS);
        Region growRight = new Region();
        HBox.setHgrow(growRight, Priority.ALWAYS);

        // Botón con aspecto de campo: icono + «Buscar» + chip de atajo.
        Label searchKbd = new Label("Ctrl K");
        searchKbd.getStyleClass().add("kbd");
        HBox searchContent = new HBox(7, fi(Feather.SEARCH), new Label("Buscar"), searchKbd);
        searchContent.setAlignment(Pos.CENTER);
        Button search = new Button(null, searchContent);
        search.getStyleClass().add("search-chip");
        search.setTooltip(new Tooltip("Buscar comandos y texto (Ctrl+K)"));
        search.setOnAction(e -> openPalette());

        themeButton = new Button(null, fi(prefs.getBoolean(PREF_DARK, false) ? Feather.SUN : Feather.MOON));
        themeButton.getStyleClass().add("icon-button");
        themeButton.setTooltip(new Tooltip("Alternar modo claro / oscuro"));
        themeButton.setOnAction(e -> applyTheme(!prefs.getBoolean(PREF_DARK, false)));

        Button about = new Button(null, fi(Feather.INFO));
        about.getStyleClass().add("icon-button");
        about.setTooltip(new Tooltip("Acerca de ORS Suite PDF"));
        about.setOnAction(e -> showAbout());

        HBox bar = new HBox(12, markHolder, appNameLabel, docChips, growLeft,
                buildSegControl(), growRight, search, themeButton, about);
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    /** Control segmentado central con las secciones de trabajo. */
    private Region buildSegControl() {
        ToggleGroup group = new ToggleGroup();
        HBox seg = new HBox();
        seg.getStyleClass().add("seg-control");
        String[][] sections = {
                {"doc", "Documento"}, {"pages", "Páginas"}, {"insert", "Insertar"},
                {"annot", "Anotar"}, {"tools", "Herramientas"}, {"sign", "Firma"}};
        for (String[] section : sections) {
            ToggleButton button = new ToggleButton(section[1]);
            button.getStyleClass().add("seg-button");
            button.setToggleGroup(group);
            button.setOnAction(e -> {
                if (!button.isSelected()) {
                    // Impide que un clic en la sección activa la deseleccione.
                    button.setSelected(true);
                    return;
                }
                showSection(section[0]);
            });
            segButtons.put(section[0], button);
            seg.getChildren().add(button);
        }
        segButtons.get("doc").setSelected(true);
        return seg;
    }

    /** Reconstruye los chips de documento de la barra superior. */
    private void refreshDocChips() {
        docChips.getChildren().clear();
        boolean empty = documentsPane.getTabs().isEmpty();
        appNameLabel.setVisible(empty);
        appNameLabel.setManaged(empty);
        Tab selected = documentsPane.getSelectionModel().getSelectedItem();
        for (Tab tab : documentsPane.getTabs()) {
            DocumentSession session = sessions.get(tab);
            if (session != null) {
                docChips.getChildren().add(buildDocChip(session, tab == selected));
            }
        }
    }

    private Region buildDocChip(DocumentSession session, boolean active) {
        Path source = session.state.hasDocument() ? session.state.getDocument().source() : null;
        String name = source != null ? source.getFileName().toString() : "documento";

        Label label = new Label(name);
        Region dot = new Region();
        dot.getStyleClass().add("chip-dot");
        if (session.state.isDirty()) {
            dot.getStyleClass().add("dirty");
        }
        Button close = new Button(null, fi(Feather.X));
        close.getStyleClass().add("chip-close");
        close.setOnAction(e -> requestCloseTab(session.tab));

        HBox chip = new HBox(8, label, dot, close);
        chip.getStyleClass().add("doc-chip");
        if (active) {
            chip.getStyleClass().add("active");
        }
        chip.setOnMouseClicked(e -> documentsPane.getSelectionModel().select(session.tab));
        if (source != null) {
            Tooltip.install(chip, new Tooltip(source.toString()));
        }
        return chip;
    }

    /** Cierra una pestaña respetando la confirmación de cambios sin guardar. */
    private void requestCloseTab(Tab tab) {
        DocumentSession session = sessions.get(tab);
        if (session != null && !confirmDiscard(session.state)) {
            return;
        }
        documentsPane.getTabs().remove(tab);
        if (session != null) {
            onTabClosed(session);
        }
    }

    /** Fila de comandos silenciosa bajo la barra superior. */
    private Region buildCommandRow() {
        statusLabel.getStyleClass().add("status-note");

        savedIndicator = new Label("Guardado", fi(Feather.CHECK_CIRCLE));
        savedIndicator.getStyleClass().add("saved-indicator");
        savedIndicator.setVisible(false);

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);

        sectionBox.setAlignment(Pos.CENTER_LEFT);
        HBox row = new HBox(2, sectionBox, grow, statusLabel, savedIndicator);
        row.getStyleClass().add("command-row");
        HBox.setMargin(statusLabel, new Insets(0, 10, 0, 0));
        return row;
    }

    private void showSection(String key) {
        List<javafx.scene.Node> items = sectionItems.get(key);
        if (items != null) {
            sectionBox.getChildren().setAll(items);
        }
        ToggleButton button = segButtons.get(key);
        if (button != null && !button.isSelected()) {
            button.setSelected(true);
        }
    }

    /** Crea, una sola vez, los botones de cada sección de la fila de comandos. */
    private void buildSections() {
        // --- Documento ---
        saveButton = cmdButton("Guardar", Feather.SAVE, "Guardar (Ctrl+S)", this::save);
        saveButton.getStyleClass().add("primary");
        Button open = cmdButton("Abrir", Feather.FOLDER, "Abrir PDF (Ctrl+O)", this::openDocument);
        Button saveAs = cmdButton("Guardar como", Feather.COPY, "Guardar con otro nombre", this::saveAs);
        recentButton.getStyleClass().add("cmd-button");
        recentButton.setDisable(true);
        refreshRecentMenu();
        Button merge = cmdButton("Unir", Feather.LAYERS, "Unir varios PDF en uno (Ctrl+M)", this::mergeDocuments);
        Button split = cmdButton("Dividir", Feather.SCISSORS, "Extraer un rango de páginas (Ctrl+D)", this::extractRange);
        Button print = cmdButton("Imprimir", Feather.PRINTER, "Imprimir (Ctrl+P)", this::printDocument);
        MenuButton export = buildExportMenuButton();
        export.getStyleClass().add("cmd-button");
        sectionItems.put("doc", List.of(
                saveButton, open, recentButton, saveAs, cmdSep(), merge, split, print, export));

        // --- Páginas ---
        rotateLeftButton = cmdButton("Rotar izq.", Feather.ROTATE_CCW, "Rotar a la izquierda", () -> rotateCurrent(-90));
        rotateRightButton = cmdButton("Rotar der.", Feather.ROTATE_CW, "Rotar a la derecha", () -> rotateCurrent(90));
        Button moveBack = cmdButton("Atrás", Feather.ARROW_UP, "Mover la página hacia atrás", () -> moveCurrent(-1));
        Button moveFwd = cmdButton("Adelante", Feather.ARROW_DOWN, "Mover la página hacia delante", () -> moveCurrent(1));
        deleteButton = cmdButton("Eliminar", Feather.TRASH_2, "Eliminar la página actual", this::deleteCurrent);
        Button extract = cmdButton("Extraer rango", Feather.SCISSORS, "Extraer un rango de páginas", this::extractRange);
        sectionItems.put("pages", List.of(
                rotateLeftButton, rotateRightButton, cmdSep(), moveBack, moveFwd, cmdSep(), deleteButton, extract));

        // --- Insertar ---
        Button image = cmdButton("Imagen / sello", Feather.IMAGE, "Insertar una imagen o sello", this::insertImage);
        Button text = cmdButton("Texto", Feather.TYPE, "Insertar texto", this::insertText);
        Button watermark = cmdButton("Marca de agua", Feather.DROPLET, "Añadir marca de agua a todas las páginas", this::addWatermark);
        Button numbering = cmdButton("Numerar páginas", Feather.HASH, "Numerar las páginas", this::numberPages);
        sectionItems.put("insert", List.of(image, text, cmdSep(), watermark, numbering));

        // --- Anotar ---
        Button highlight = cmdButton("Resaltar", Feather.EDIT_3, "Resaltar una zona", () -> annotate("Resaltado añadido",
                (d, r) -> AnnotationService.highlight(d, r.page(), r.x(), r.y(), r.width(), r.height())));
        Button rectangle = cmdButton("Recuadro", Feather.SQUARE, "Dibujar un recuadro", () -> annotate("Recuadro añadido",
                (d, r) -> AnnotationService.rectangle(d, r.page(), r.x(), r.y(), r.width(), r.height())));
        Button note = cmdButton("Nota", Feather.MESSAGE_SQUARE, "Añadir una nota", this::annotateNote);
        Button ink = cmdButton("Dibujo libre", Feather.EDIT_2, "Dibujar a mano alzada", this::annotateFreehand);
        Button arrow = cmdButton("Flecha", Feather.ARROW_RIGHT, "Dibujar una flecha", this::annotateArrow);
        sectionItems.put("annot", List.of(highlight, rectangle, note, cmdSep(), ink, arrow));

        // --- Herramientas ---
        Button ocr = cmdButton("OCR página", Feather.ALIGN_LEFT, "Reconocer texto (OCR) de la página actual", this::ocrCurrentPage);
        Button redact = cmdButton("Redactar", Feather.EYE_OFF, "Redactar (ocultar de forma segura) una zona", this::redactZone);
        Button find = cmdButton("Buscar", Feather.SEARCH, "Buscar en el documento (Ctrl+K)", this::openPalette);
        Button protect = cmdButton("Proteger", Feather.LOCK, "Proteger con contraseña", this::protectDocument);
        Button unprotect = cmdButton("Quitar protección", Feather.UNLOCK, "Quitar la protección con contraseña", this::unprotectDocument);
        sectionItems.put("tools", List.of(ocr, redact, find, cmdSep(), protect, unprotect));

        // --- Firma ---
        Button signButton = cmdButton("Firmar documento", Feather.FEATHER, "Firmar con un certificado digital", this::signDocument);
        signButton.getStyleClass().add("primary");
        Button validate = cmdButton("Verificar firmas", Feather.CHECK_CIRCLE, "Validar las firmas del documento", this::validateSignatures);
        sectionItems.put("sign", List.of(signButton, validate));
    }

    private Button cmdButton(String text, Feather ikon, String tip, Runnable action) {
        Button button = new Button(text, fi(ikon));
        button.getStyleClass().add("cmd-button");
        button.setTooltip(new Tooltip(tip));
        button.setOnAction(e -> action.run());
        return button;
    }

    private Region cmdSep() {
        Region sep = new Region();
        sep.getStyleClass().add("cmd-sep");
        HBox.setMargin(sep, new Insets(0, 6, 0, 6));
        return sep;
    }

    /**
     * Aplica el tema de marca a todos los diálogos y alertas: cuando aparece
     * una ventana hija (propiedad de esta o descendiente), le inyecta la hoja
     * de estilos y la clase {@code app-dialog}, y replica el modo oscuro. Así
     * los {@code Dialog}/{@code Alert} nativos heredan la identidad visual sin
     * tener que estilarlos uno a uno.
     */
    private void installDialogTheming() {
        javafx.stage.Window.getWindows().addListener(
                (javafx.collections.ListChangeListener<javafx.stage.Window>) change -> {
                    while (change.next()) {
                        for (javafx.stage.Window window : change.getAddedSubList()) {
                            themeChildWindow(window);
                        }
                    }
                });
    }

    private void themeChildWindow(javafx.stage.Window window) {
        if (window == stage) {
            return;
        }
        // Solo ventanas asociadas a nuestra aplicación (diálogos con owner).
        javafx.scene.Scene scene = window.getScene();
        if (scene == null || scene.getRoot() == null) {
            // La escena aún no está lista: reintenta cuando se asigne.
            window.sceneProperty().addListener((o, a, b) -> themeChildWindow(window));
            return;
        }
        if (!scene.getStylesheets().contains(APP_CSS)) {
            scene.getStylesheets().add(APP_CSS);
        }
        var styleClasses = scene.getRoot().getStyleClass();
        if (!styleClasses.contains("app-dialog")) {
            styleClasses.add("app-dialog");
        }
        styleClasses.remove("dark");
        if (prefs.getBoolean(PREF_DARK, false)) {
            styleClasses.add("dark");
        }
    }

    /** Registra los aceleradores de teclado una vez la escena está disponible. */
    private void registerAccelerators() {
        root.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null) {
                return;
            }
            var accelerators = scene.getAccelerators();
            accelerators.put(new KeyCharacterCombination("O", KeyCombination.SHORTCUT_DOWN), this::openDocument);
            accelerators.put(new KeyCharacterCombination("S", KeyCombination.SHORTCUT_DOWN), this::save);
            accelerators.put(new KeyCharacterCombination("P", KeyCombination.SHORTCUT_DOWN), this::printDocument);
            accelerators.put(new KeyCharacterCombination("K", KeyCombination.SHORTCUT_DOWN), this::openPalette);
            accelerators.put(new KeyCharacterCombination("F", KeyCombination.SHORTCUT_DOWN), this::openPalette);
            accelerators.put(new KeyCharacterCombination("M", KeyCombination.SHORTCUT_DOWN), this::mergeDocuments);
            accelerators.put(new KeyCharacterCombination("D", KeyCombination.SHORTCUT_DOWN), this::extractRange);
        });
    }

    // ------------------------- píldora de navegación y riel de herramientas

    /** Píldora oscura flotante: página, zoom y ajuste, en el borde inferior. */
    private HBox buildNavPill() {
        prevButton = pillButton(Feather.CHEVRON_LEFT, "Página anterior",
                () -> goToPage(state.getCurrentPage() - 1));
        nextButton = pillButton(Feather.CHEVRON_RIGHT, "Página siguiente",
                () -> goToPage(state.getCurrentPage() + 1));
        pageLabel.getStyleClass().add("pill-value");

        Button zoomOut = pillButton(Feather.MINUS, "Reducir",
                () -> state.setZoom(state.getZoom() - 0.25));
        Button zoomIn = pillButton(Feather.PLUS, "Ampliar",
                () -> state.setZoom(state.getZoom() + 0.25));
        Button zoomReset = pillButton(Feather.MAXIMIZE, "Zoom natural (100 %)",
                () -> state.setZoom(1.0));
        zoomLabel.getStyleClass().add("pill-value");

        HBox pill = new HBox(8, prevButton, pageLabel, nextButton, pillSep(),
                zoomOut, zoomLabel, zoomIn, pillSep(), zoomReset);
        pill.getStyleClass().add("nav-pill");
        pill.setMaxWidth(Region.USE_PREF_SIZE);
        pill.setMaxHeight(Region.USE_PREF_SIZE);
        return pill;
    }

    private Button pillButton(Feather ikon, String tip, Runnable action) {
        Button button = new Button(null, fi(ikon));
        button.getStyleClass().add("pill-button");
        button.setTooltip(new Tooltip(tip));
        button.setOnAction(e -> action.run());
        return button;
    }

    private Region pillSep() {
        Region sep = new Region();
        sep.getStyleClass().add("pill-sep");
        return sep;
    }

    /** Riel flotante de herramientas rápidas, en el borde derecho del lienzo. */
    private VBox buildToolRail() {
        Button text = toolButton(Feather.TYPE, "Insertar texto", this::insertText);
        Button draw = toolButton(Feather.EDIT_3, "Dibujar a mano alzada", this::annotateFreehand);
        Button image = toolButton(Feather.IMAGE, "Insertar imagen o sello", this::insertImage);
        Button note = toolButton(Feather.MESSAGE_SQUARE, "Añadir una nota", this::annotateNote);
        Button sign = toolButton(Feather.FEATHER, "Firmar documento", this::signDocument);

        VBox rail = new VBox(3, text, draw, image, note, sign);
        rail.getStyleClass().add("float-rail");
        rail.setMaxWidth(Region.USE_PREF_SIZE);
        rail.setMaxHeight(Region.USE_PREF_SIZE);
        return rail;
    }

    private Button toolButton(Feather ikon, String tip, Runnable action) {
        Button button = new Button(null, fi(ikon));
        button.getStyleClass().add("tool-button");
        button.setTooltip(new Tooltip(tip));
        button.setOnAction(e -> action.run());
        return button;
    }

    /** Notificación "toast" no modal en la esquina, para confirmar acciones. */
    private void toast(String title, String message) {
        Notifications.create()
                .title(title)
                .text(message)
                .owner(stage)
                .showInformation();
    }

    // ------------------------------------------ paleta de comandos (Ctrl+K)

    private void openPalette() {
        palette.show();
    }

    /** Comandos disponibles en la paleta; los que requieren documento se
     *  ocultan cuando no hay ninguno abierto. */
    private List<CommandPalette.Command> buildCommands() {
        List<CommandPalette.Command> commands = new ArrayList<>();
        commands.add(new CommandPalette.Command("Abrir PDF…",
                "Abrir un documento del disco", Feather.FOLDER, "Ctrl+O", false, this::openDocument));
        commands.add(new CommandPalette.Command("Unir PDFs…",
                "Combinar varios documentos en uno", Feather.LAYERS, "Ctrl+M", false, this::mergeDocuments));
        commands.add(new CommandPalette.Command("Guardar",
                "Guardar el documento actual", Feather.SAVE, "Ctrl+S", true, this::save));
        commands.add(new CommandPalette.Command("Guardar como…",
                "Guardar con otro nombre", Feather.COPY, null, true, this::saveAs));
        commands.add(new CommandPalette.Command("Dividir documento…",
                "Extraer un rango de páginas", Feather.SCISSORS, "Ctrl+D", true, this::extractRange));
        commands.add(new CommandPalette.Command("Imprimir…",
                "Enviar a la impresora", Feather.PRINTER, "Ctrl+P", true, this::printDocument));
        commands.add(new CommandPalette.Command("Exportar texto…",
                "Extraer el texto a un .txt", Feather.UPLOAD, null, true, this::exportAsText));
        commands.add(new CommandPalette.Command("Exportar imágenes…",
                "Una imagen PNG por página", Feather.IMAGE, null, true, this::exportAsImages));
        commands.add(new CommandPalette.Command("Rotar página a la izquierda",
                null, Feather.ROTATE_CCW, null, true, () -> rotateCurrent(-90)));
        commands.add(new CommandPalette.Command("Rotar página a la derecha",
                null, Feather.ROTATE_CW, null, true, () -> rotateCurrent(90)));
        commands.add(new CommandPalette.Command("Eliminar página actual",
                null, Feather.TRASH_2, null, true, this::deleteCurrent));
        commands.add(new CommandPalette.Command("Insertar imagen o sello…",
                null, Feather.IMAGE, null, true, this::insertImage));
        commands.add(new CommandPalette.Command("Insertar texto…",
                null, Feather.TYPE, null, true, this::insertText));
        commands.add(new CommandPalette.Command("Marca de agua…",
                "Aplicar a todas las páginas", Feather.DROPLET, null, true, this::addWatermark));
        commands.add(new CommandPalette.Command("Numerar páginas",
                null, Feather.HASH, null, true, this::numberPages));
        commands.add(new CommandPalette.Command("Resaltar zona",
                null, Feather.EDIT_3, null, true, () -> annotate("Resaltado añadido",
                (d, r) -> AnnotationService.highlight(d, r.page(), r.x(), r.y(), r.width(), r.height()))));
        commands.add(new CommandPalette.Command("Añadir nota",
                null, Feather.MESSAGE_SQUARE, null, true, this::annotateNote));
        commands.add(new CommandPalette.Command("Dibujo libre",
                null, Feather.EDIT_2, null, true, this::annotateFreehand));
        commands.add(new CommandPalette.Command("OCR de la página actual",
                "Reconocer texto escaneado", Feather.ALIGN_LEFT, null, true, this::ocrCurrentPage));
        commands.add(new CommandPalette.Command("Redactar zona",
                "Ocultar contenido de forma irreversible", Feather.EYE_OFF, null, true, this::redactZone));
        commands.add(new CommandPalette.Command("Proteger con contraseña…",
                "Cifrado AES-256, local", Feather.LOCK, null, true, this::protectDocument));
        commands.add(new CommandPalette.Command("Quitar protección",
                null, Feather.UNLOCK, null, true, this::unprotectDocument));
        commands.add(new CommandPalette.Command("Firmar documento…",
                "Firma digital PAdES con certificado", Feather.FEATHER, null, true, this::signDocument));
        commands.add(new CommandPalette.Command("Verificar firmas",
                null, Feather.CHECK_CIRCLE, null, true, this::validateSignatures));
        commands.add(new CommandPalette.Command("Alternar modo claro / oscuro",
                null, Feather.MOON, null, false,
                () -> applyTheme(!prefs.getBoolean(PREF_DARK, false))));
        commands.add(new CommandPalette.Command("Acerca de ORS Suite PDF",
                null, Feather.INFO, null, false, this::showAbout));
        return commands;
    }

    /** Navega a una coincidencia elegida en la paleta y resalta la búsqueda. */
    private void showDocumentMatch(String query, int page) {
        if (!state.hasDocument()) {
            return;
        }
        PdfDocument document = state.getDocument();
        PdfView view = pdfView;
        AppState target = state;
        runBackground(() -> {
            List<SearchService.Match> results = SearchService.find(document, query);
            Platform.runLater(() -> {
                if (view != null) {
                    view.setSearchMatches(results);
                }
                target.setCurrentPage(page);
                statusLabel.setText(results.isEmpty() ? "" : results.size() + " coincidencias de «" + query + "»");
            });
        }, "No se pudo buscar");
    }

    // ------------------------------------------------ diálogos de archivo

    private static final String PREF_LAST_DIR = "lastDirectory";
    private static final String PREF_DARK = "darkMode";
    private final Preferences prefs = Preferences.userNodeForPackage(MainView.class);

    private void applyTheme(boolean dark) {
        Application.setUserAgentStylesheet(dark
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        // El acento de marca en oscuro se define en app.css bajo la clase ".dark",
        // que AtlantaFX no añade por sí solo: la conmutamos aquí.
        root.getStyleClass().remove("dark");
        if (dark) {
            root.getStyleClass().add("dark");
        }
        prefs.putBoolean(PREF_DARK, dark);
        if (themeButton != null) {
            themeButton.setGraphic(fi(dark ? Feather.SUN : Feather.MOON));
        }
        if (markHolder != null) {
            markHolder.getChildren().setAll(Branding.mark(22, dark));
        }
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
        recentButton.getItems().clear();
        List<String> list = recentFiles();
        recentButton.setDisable(list.isEmpty());
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
            recentButton.getItems().add(item);
        }
        if (!list.isEmpty()) {
            MenuItem clear = new MenuItem("Vaciar lista");
            clear.setOnAction(e -> {
                prefs.remove(PREF_RECENT);
                refreshRecentMenu();
            });
            recentButton.getItems().addAll(new SeparatorMenuItem(), clear);
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
        loadInBackground(path, null, null);
    }

    private void loadInBackground(Path path, String password, Runnable onLoaded) {
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
                        loadInBackground(path, pwd.get(), onLoaded);
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
                if (onLoaded != null) {
                    onLoaded.run();
                }
            });
        }, "No se pudo abrir el PDF");
    }

    private void protectDocument() {
        if (!state.hasDocument()) {
            return;
        }
        Dialog<ProtectSetup> dialog = new Dialog<>();
        dialog.setTitle("Proteger con contraseña");
        dialog.initOwner(stage);
        ButtonType protectType = new ButtonType("Proteger", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(protectType, ButtonType.CANCEL);

        Label subtitle = new Label("Cifrado AES-256, local");
        subtitle.setStyle("-fx-text-fill: -ors-text-muted; -fx-font-size: 12px;");
        StackPane chip = new StackPane(fi(Feather.LOCK));
        chip.getStyleClass().add("dialog-icon-chip");
        Label title = new Label("Proteger con contraseña");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: 600;");
        HBox header = new HBox(10, chip, new VBox(2, title, subtitle));
        header.setAlignment(Pos.CENTER_LEFT);

        PasswordField password = new PasswordField();
        password.setPromptText("Contraseña");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Repite la contraseña");
        CheckBox allowPrint = new CheckBox("Permitir imprimir");
        allowPrint.setSelected(true);
        CheckBox allowCopy = new CheckBox("Permitir copiar texto");

        VBox content = new VBox(10, header,
                new Label("Contraseña"), password,
                new Label("Confirmar contraseña"), confirm,
                allowPrint, allowCopy);
        content.setPadding(new Insets(4, 4, 0, 4));
        content.setPrefWidth(340);
        dialog.getDialogPane().setContent(content);
        Platform.runLater(password::requestFocus);

        var okButton = dialog.getDialogPane().lookupButton(protectType);
        okButton.disableProperty().bind(password.textProperty().isEmpty()
                .or(password.textProperty().isNotEqualTo(confirm.textProperty())));

        dialog.setResultConverter(button -> button == protectType
                ? new ProtectSetup(password.getText(), allowPrint.isSelected(), allowCopy.isSelected())
                : null);

        Optional<ProtectSetup> setup = dialog.showAndWait();
        if (setup.isEmpty()) {
            return;
        }
        try {
            SecurityService.protect(state.getDocument(), setup.get().password(),
                    setup.get().allowPrint(), setup.get().allowCopy());
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
            // Si el documento tiene firmas digitales, se guarda de forma
            // incremental (append-only) para no romper su ByteRange; un guardado
            // normal reescribe el fichero e invalida las firmas (HASH_FAILURE).
            if (current.hasSignatures()) {
                PdfOperations.saveIncremental(current.pdbox(), tmp);
            } else {
                PdfOperations.save(current.pdbox(), tmp);
            }
            current.close();
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            PdfDocument reopened = PdfDocument.open(target);
            Platform.runLater(() -> {
                state.setDocument(reopened);
                int last = reopened.pageCount() - 1;
                state.setCurrentPage(Math.min(pageToRestore, last));
                state.setDirty(false);
                statusLabel.setText("Guardado: " + target.getFileName());
                toast("Documento guardado", target.getFileName().toString());
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
        if (!state.hasDocument() || !ensureCanModifySigned()) {
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
        if (!state.hasDocument() || !ensureCanModifySigned()) {
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

    private MenuButton buildExportMenuButton() {
        MenuButton export = new MenuButton("Exportar", fi(Feather.UPLOAD));
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
        if (!state.hasDocument() || !ensureCanModifySigned()) {
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
        if (!state.hasDocument() || !ensureCanModifySigned()) {
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
        if (!state.hasDocument() || !ensureCanModifySigned()) {
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

    /** TSA por defecto (RFC 3161). Si falla o no hay red, se firma en PAdES-B. */
    private static final String DEFAULT_TSA_URL = "https://freetsa.org/tsr";

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

        CertificateInfo info = CertificateInfo.from(key);
        Optional<SignSetup> setup = askSignSetup(info);
        if (setup.isEmpty()) {
            closeQuietly(token);
            return;
        }
        SignSetup s = setup.get();
        Path output = deriveSignedPath(source);
        DSSPrivateKeyEntry chosenKey = key;

        if (s.visible()) {
            statusLabel.setText("Dibuja el recuadro de la firma sobre la página…");
            pdfView.beginRegionSelection(region -> {
                if (region == null) {
                    closeQuietly(token);
                    statusLabel.setText("Firma cancelada");
                    return;
                }
                String text = s.appearance().buildText(info, s.reason(), s.location(),
                        java.time.LocalDateTime.now());
                VisibleSignature visible = new VisibleSignature(region.page(),
                        (float) region.x(), (float) region.y(),
                        (float) region.width(), (float) region.height(), text);
                runSign(token, chosenKey,
                        new SignSpec(source, output, s.tsaUrl(), s.reason(), s.location(), visible));
            });
        } else {
            runSign(token, chosenKey,
                    new SignSpec(source, output, s.tsaUrl(), s.reason(), s.location(), null));
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

    /** Diálogo de firma estilo Adobe: opciones de apariencia + vista previa en vivo. */
    private Optional<SignSetup> askSignSetup(CertificateInfo info) {
        Dialog<SignSetup> dialog = new Dialog<>();
        String signerName = info.fullName(false);
        dialog.setTitle("Firmar como «" + (signerName.isBlank() ? info.commonName() : signerName) + "»");
        dialog.initOwner(stage);
        ButtonType signType = new ButtonType("Firmar", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(signType, ButtonType.CANCEL);

        // --- Opciones de apariencia ---
        CheckBox visibleBox = new CheckBox("Insertar firma visible en el documento");
        visibleBox.setSelected(true);
        CheckBox headingBox = new CheckBox("Encabezado:");
        headingBox.setSelected(true);
        TextField headingField = new TextField("Firmado digitalmente por");
        CheckBox nameBox = new CheckBox("Nombre y apellidos");
        nameBox.setSelected(true);
        ComboBox<String> nameOrder = new ComboBox<>();
        nameOrder.getItems().addAll("Nombre Apellidos", "Apellidos, Nombre");
        nameOrder.setValue("Nombre Apellidos");
        CheckBox nifBox = new CheckBox("NIF / DNI");
        nifBox.setSelected(info.hasNif());
        nifBox.setDisable(!info.hasNif());
        CheckBox reasonBox = new CheckBox("Motivo");
        CheckBox locationBox = new CheckBox("Lugar");
        CheckBox dateBox = new CheckBox("Fecha");
        dateBox.setSelected(true);
        CheckBox timeBox = new CheckBox("Hora");
        timeBox.setSelected(true);

        TextField reasonField = new TextField();
        reasonField.setPromptText("Motivo (opcional)");
        TextField locationField = new TextField();
        locationField.setPromptText("Lugar (opcional)");
        CheckBox timestampBox = new CheckBox("Añadir sello de tiempo (recomendado)");
        timestampBox.setSelected(true);

        VBox options = new VBox(8,
                visibleBox,
                new Separator(),
                new Label("Aspecto de la firma"),
                headingBox, headingField,
                nameBox, nameOrder,
                nifBox, reasonBox, locationBox,
                new HBox(16, dateBox, timeBox),
                new Separator(),
                labeled("Motivo:", reasonField),
                labeled("Lugar:", locationField),
                timestampBox);
        options.setPrefWidth(340);

        // --- Vista previa en vivo ---
        Label preview = new Label();
        preview.setWrapText(true);
        preview.setStyle("-fx-font-family: 'Instrument Sans'; -fx-font-size: 12px;"
                + " -fx-text-fill: -color-accent-emphasis;"
                + " -fx-background-color: -color-bg-default;"
                + " -fx-border-color: -color-accent-emphasis; -fx-border-width: 1.5;"
                + " -fx-border-radius: 7; -fx-background-radius: 7;"
                + " -fx-padding: 13 15; -fx-min-height: 104;");
        preview.setMaxWidth(Double.MAX_VALUE);
        Label previewTitle = new Label("VISTA PREVIA");
        previewTitle.getStyleClass().add("mono");
        previewTitle.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-fg-subtle;");
        VBox previewBox = new VBox(9, previewTitle, preview);
        previewBox.setPrefWidth(280);

        Runnable refreshPreview = () -> {
            SignatureAppearance app = new SignatureAppearance(
                    headingBox.isSelected(), headingField.getText(),
                    nameBox.isSelected(), nameOrder.getValue().startsWith("Apellidos"),
                    nifBox.isSelected(),
                    reasonBox.isSelected(), locationBox.isSelected(),
                    dateBox.isSelected(), timeBox.isSelected());
            String text = app.buildText(info, reasonField.getText(), locationField.getText(),
                    java.time.LocalDateTime.now());
            preview.setText(text.isBlank() ? "(sin contenido)" : text);
            boolean vis = visibleBox.isSelected();
            for (var node : new javafx.scene.control.Control[]{headingBox, headingField, nameBox,
                    nameOrder, nifBox, reasonBox, locationBox, dateBox, timeBox}) {
                node.setDisable(!vis || (node == nifBox && !info.hasNif()));
            }
            previewBox.setDisable(!vis);
        };
        for (CheckBox cb : new CheckBox[]{visibleBox, headingBox, nameBox, nifBox, reasonBox,
                locationBox, dateBox, timeBox}) {
            cb.selectedProperty().addListener((o, a, b) -> refreshPreview.run());
        }
        nameOrder.valueProperty().addListener((o, a, b) -> refreshPreview.run());
        headingField.textProperty().addListener((o, a, b) -> refreshPreview.run());
        reasonField.textProperty().addListener((o, a, b) -> refreshPreview.run());
        locationField.textProperty().addListener((o, a, b) -> refreshPreview.run());
        refreshPreview.run();

        Label pinHint = new Label("Al firmar, el sistema pedirá el PIN si usas DNIe o tarjeta.");
        pinHint.setWrapText(true);
        HBox pinNote = new HBox(8, fi(Feather.INFO), pinHint);
        pinNote.getStyleClass().add("info-note");
        HBox.setHgrow(pinHint, Priority.ALWAYS);
        previewBox.getChildren().add(pinNote);

        HBox content = new HBox(20, options, previewBox);
        content.setPadding(new Insets(16));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setHeaderText(null);

        dialog.setResultConverter(button -> {
            if (button != signType) {
                return null;
            }
            SignatureAppearance app = new SignatureAppearance(
                    headingBox.isSelected(), headingField.getText(),
                    nameBox.isSelected(), nameOrder.getValue().startsWith("Apellidos"),
                    nifBox.isSelected(),
                    reasonBox.isSelected(), locationBox.isSelected(),
                    dateBox.isSelected(), timeBox.isSelected());
            String tsa = timestampBox.isSelected() ? DEFAULT_TSA_URL : "";
            return new SignSetup(tsa, reasonField.getText(),
                    locationField.getText(), visibleBox.isSelected(), app);
        });
        return dialog.showAndWait();
    }

    private HBox labeled(String label, javafx.scene.Node field) {
        Label l = new Label(label);
        l.setMinWidth(150);
        HBox box = new HBox(8, l, field);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        return box;
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
        if (!state.hasDocument() || !ensureCanModifySigned()) {
            return;
        }
        try {
            operation.run();
            state.markMutated();
        } catch (Exception ex) {
            showError("No se pudo completar la operación", ex.getMessage());
        }
    }

    /**
     * Si el documento tiene firmas digitales, advierte de que la operación
     * quedará registrada como un cambio posterior a la firma y pide
     * confirmación. Devuelve {@code true} si se puede continuar.
     *
     * <p>El guardado de estos cambios es incremental (ver
     * {@code PdfOperations.saveIncremental}), así que la firma no se corrompe
     * criptográficamente, pero el documento constará como modificado tras
     * firmarse — algo que el usuario debe decidir conscientemente.</p>
     */
    private boolean ensureCanModifySigned() {
        if (!state.hasDocument() || !state.getDocument().hasSignatures()) {
            return true;
        }
        return confirm("Documento firmado",
                "Este documento contiene firmas digitales. Si lo modificas, el "
                        + "cambio se registrará después de la firma y los validadores "
                        + "podrán marcarlo como alterado tras firmar.\n\n¿Continuar?");
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

        if (savedIndicator != null) {
            boolean dirty = state.isDirty();
            savedIndicator.setVisible(has);
            savedIndicator.setText(dirty ? "Sin guardar" : "Guardado");
            savedIndicator.setGraphic(fi(dirty ? Feather.EDIT_2 : Feather.CHECK_CIRCLE));
            savedIndicator.setStyle(dirty ? "-fx-text-fill: #E8A13C;" : "");
            ((FontIcon) savedIndicator.getGraphic()).setStyle(dirty ? "-fx-icon-color: #E8A13C;" : "");
        }

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
        refreshDocChips();
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
            // Lienzo redondeado sobre el que flota el panel lateral.
            StackPane canvasHolder = new StackPane(pdfView);
            canvasHolder.getStyleClass().add("canvas-holder");

            StackPane wrap = new StackPane(canvasHolder, buildSidePanel());
            wrap.setPadding(new Insets(10, 14, 14, 14));
            tab.setContent(wrap);

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

        /** Panel flotante izquierdo: miniaturas, marcadores y formulario. */
        private Region buildSidePanel() {
            ThumbnailPanel thumbnails = new ThumbnailPanel(state);
            BookmarkPanel bookmarks = new BookmarkPanel(state);
            FormPanel form = new FormPanel(state);

            StackPane content = new StackPane(thumbnails, bookmarks, form);
            VBox.setVgrow(content, Priority.ALWAYS);

            ToggleGroup group = new ToggleGroup();
            HBox miniSeg = new HBox(2,
                    miniSegButton(Feather.LAYERS, "Páginas", group, thumbnails, content),
                    miniSegButton(Feather.BOOKMARK, "Marcadores", group, bookmarks, content),
                    miniSegButton(Feather.CHECK_SQUARE, "Formulario", group, form, content));
            miniSeg.getStyleClass().add("mini-seg");
            ((ToggleButton) miniSeg.getChildren().get(0)).setSelected(true);
            showOnly(content, thumbnails);

            VBox panel = new VBox(10, miniSeg, content);
            panel.getStyleClass().add("float-panel");
            panel.setPadding(new Insets(12));
            panel.setPrefWidth(186);
            panel.setMaxWidth(186);
            StackPane.setAlignment(panel, Pos.CENTER_LEFT);
            StackPane.setMargin(panel, new Insets(14));
            return panel;
        }

        private ToggleButton miniSegButton(Feather ikon, String tip, ToggleGroup group,
                                           javafx.scene.Node target, StackPane content) {
            ToggleButton button = new ToggleButton(null, fi(ikon));
            button.getStyleClass().add("mini-seg-button");
            button.setToggleGroup(group);
            button.setTooltip(new Tooltip(tip));
            button.setOnAction(e -> {
                if (!button.isSelected()) {
                    button.setSelected(true);
                    return;
                }
                showOnly(content, target);
            });
            return button;
        }

        private void showOnly(StackPane content, javafx.scene.Node target) {
            for (javafx.scene.Node child : content.getChildren()) {
                child.setVisible(child == target);
                child.setManaged(child == target);
            }
        }
    }

    /** Resultado del diálogo de inserción de imagen: esquina y anchura (pt). */
    private record Placement(String corner, double width) {
    }

    /** Resultado del diálogo de protección: contraseña y permisos. */
    private record ProtectSetup(String password, boolean allowPrint, boolean allowCopy) {
    }

    /** Resultado del diálogo de inserción de texto: contenido, tamaño y esquina. */
    private record TextContent(String text, int fontSize, String corner) {
    }

    /** Opciones recogidas en el diálogo de firma. */
    private record SignSetup(String tsaUrl, String reason, String location, boolean visible,
                             SignatureAppearance appearance) {
    }
}
