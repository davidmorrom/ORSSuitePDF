package com.orsconsulting.orssuitepdf.ui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.orsconsulting.orssuitepdf.core.PdfDocument;
import com.orsconsulting.orssuitepdf.core.PdfOperations;
import com.orsconsulting.orssuitepdf.core.StampService;
import com.orsconsulting.orssuitepdf.ocr.OcrService;
import com.orsconsulting.orssuitepdf.signing.PAdESSigner;
import com.orsconsulting.orssuitepdf.signing.SignResult;
import com.orsconsulting.orssuitepdf.signing.SignSpec;
import com.orsconsulting.orssuitepdf.signing.SigningTokens;
import com.orsconsulting.orssuitepdf.signing.VisibleSignature;

import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.SignatureTokenConnection;

import javafx.application.Platform;
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
import javafx.scene.control.Menu;
import javafx.scene.control.PasswordField;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
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
    private final BookmarkPanel bookmarkPanel = new BookmarkPanel(state);
    private final FormPanel formPanel = new FormPanel(state);
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
        root.setCenter(buildWorkspace());
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

    private Region buildWorkspace() {
        Tab bookmarksTab = new Tab("Marcadores", bookmarkPanel);
        bookmarksTab.setClosable(false);
        Tab formTab = new Tab("Formulario", formPanel);
        formTab.setClosable(false);
        TabPane sidebar = new TabPane(bookmarksTab, formTab);
        sidebar.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        SplitPane split = new SplitPane(sidebar, pdfView);
        split.setDividerPositions(0.24);
        SplitPane.setResizableWithParent(sidebar, Boolean.FALSE);
        return split;
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

        Menu insert = new Menu("Insertar");
        MenuItem stamp = new MenuItem("Imagen o sello…");
        stamp.setOnAction(e -> insertImage());
        MenuItem textItem = new MenuItem("Texto…");
        textItem.setOnAction(e -> insertText());
        insert.getItems().addAll(stamp, textItem);

        Menu tools = new Menu("Herramientas");
        MenuItem ocr = new MenuItem("OCR de la página actual");
        ocr.setOnAction(e -> ocrCurrentPage());
        tools.getItems().add(ocr);

        Menu sign = new Menu("Firma");
        MenuItem signItem = new MenuItem("Firmar con certificado…");
        signItem.setOnAction(e -> signDocument());
        sign.getItems().add(signItem);

        Menu help = new Menu("Ayuda");
        MenuItem about = new MenuItem("Acerca de ORS Suite PDF");
        about.setOnAction(e -> showAbout());
        help.getItems().add(about);

        return new MenuBar(file, page, insert, tools, sign, help);
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

    /** Abre un documento por su ruta (p. ej. desde la línea de comandos). */
    public void open(Path path) {
        loadInBackground(path);
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

    private void insertImage() {
        if (!state.hasDocument()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar imagen o sello");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg"));
        File imageFile = chooser.showOpenDialog(stage);
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

    // ------------------------------------------------------------- firma

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
        Optional<SigningOptions> options = askSigningOptions();
        if (options.isEmpty()) {
            return;
        }
        SigningOptions opts = options.get();

        SignatureTokenConnection token;
        try {
            token = openToken(opts);
        } catch (Exception ex) {
            opts.wipeSecrets();
            showError("No se pudo acceder al certificado", ex.getMessage());
            return;
        }

        DSSPrivateKeyEntry key;
        try {
            List<DSSPrivateKeyEntry> keys = token.getKeys();
            if (keys.isEmpty()) {
                closeQuietly(token);
                opts.wipeSecrets();
                showError("Firmar", "No se encontró ningún certificado con clave privada.");
                return;
            }
            key = keys.size() == 1 ? keys.get(0) : chooseKey(keys);
            if (key == null) {
                closeQuietly(token);
                opts.wipeSecrets();
                return;
            }
        } catch (Exception ex) {
            closeQuietly(token);
            opts.wipeSecrets();
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
                    opts.wipeSecrets();
                    statusLabel.setText("Firma cancelada");
                    return;
                }
                VisibleSignature visible = new VisibleSignature(region.page(),
                        (float) region.x(), (float) region.y(),
                        (float) region.width(), (float) region.height(), null);
                runSign(token, chosenKey,
                        new SignSpec(source, output, opts.tsaUrl(), opts.reason(), opts.location(), visible),
                        opts);
            });
        } else {
            runSign(token, chosenKey,
                    new SignSpec(source, output, opts.tsaUrl(), opts.reason(), opts.location(), null),
                    opts);
        }
    }

    private SignatureTokenConnection openToken(SigningOptions opts) throws Exception {
        return switch (opts.method()) {
            case PKCS12 -> SigningTokens.pkcs12(Path.of(opts.certPath()), opts.password());
            case WINDOWS -> SigningTokens.windowsStore();
            case PKCS11 -> SigningTokens.pkcs11(Path.of(opts.driverPath()), opts.pin());
        };
    }

    private void runSign(SignatureTokenConnection token, DSSPrivateKeyEntry key,
                         SignSpec spec, SigningOptions opts) {
        statusLabel.setText("Firmando… (puede tardar si se contacta con el TSA)");
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
                opts.wipeSecrets();
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

    private Optional<SigningOptions> askSigningOptions() {
        Dialog<SigningOptions> dialog = new Dialog<>();
        dialog.setTitle("Firmar con certificado");
        dialog.setHeaderText("Firma digital PAdES");
        dialog.initOwner(stage);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> method = new ComboBox<>();
        method.getItems().addAll("Archivo .p12/.pfx", "Almacén de Windows", "DNIe / tarjeta (PKCS#11)");
        method.setValue("Archivo .p12/.pfx");
        method.setMaxWidth(Double.MAX_VALUE);

        TextField certField = new TextField();
        certField.setEditable(false);
        certField.setPromptText("Selecciona un .p12/.pfx");
        Button browseCert = new Button("Examinar…");
        browseCert.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Seleccionar certificado");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Certificados PKCS#12", "*.p12", "*.pfx"));
            File defaultDir = new File("CERTIFICADO PERSONAL (MUY CONFIDENCIAL)");
            if (defaultDir.isDirectory()) {
                chooser.setInitialDirectory(defaultDir);
            }
            File chosen = chooser.showOpenDialog(dialog.getOwner());
            if (chosen != null) {
                certField.setText(chosen.getAbsolutePath());
            }
        });
        HBox certRow = new HBox(8, certField, browseCert);
        HBox.setHgrow(certField, Priority.ALWAYS);
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Contraseña del certificado");

        TextField driverField = new TextField();
        driverField.setPromptText("Ruta al módulo PKCS#11 (DLL del DNIe/lector)");
        Button browseDriver = new Button("Examinar…");
        browseDriver.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Seleccionar módulo PKCS#11");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Bibliotecas", "*.dll", "*.so", "*.dylib"));
            File chosen = chooser.showOpenDialog(dialog.getOwner());
            if (chosen != null) {
                driverField.setText(chosen.getAbsolutePath());
            }
        });
        HBox driverRow = new HBox(8, driverField, browseDriver);
        HBox.setHgrow(driverField, Priority.ALWAYS);
        PasswordField pinField = new PasswordField();
        pinField.setPromptText("PIN de la tarjeta / DNIe");

        TextField tsaField = new TextField("https://freetsa.org/tsr");
        tsaField.setPromptText("URL de TSA (vacío = sin sello de tiempo)");
        TextField reasonField = new TextField();
        reasonField.setPromptText("Motivo (opcional)");
        TextField locationField = new TextField();
        locationField.setPromptText("Lugar (opcional)");
        CheckBox visibleBox = new CheckBox("Firma visible en el documento (seleccionar recuadro)");

        Label certLabel = new Label("Certificado:");
        Label pwdLabel = new Label("Contraseña:");
        Label driverLabel = new Label("Módulo:");
        Label pinLabel = new Label("PIN:");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.add(new Label("Método:"), 0, 0);
        grid.add(method, 1, 0);
        grid.add(certLabel, 0, 1);
        grid.add(certRow, 1, 1);
        grid.add(pwdLabel, 0, 2);
        grid.add(passwordField, 1, 2);
        grid.add(driverLabel, 0, 3);
        grid.add(driverRow, 1, 3);
        grid.add(pinLabel, 0, 4);
        grid.add(pinField, 1, 4);
        grid.add(new Label("TSA:"), 0, 5);
        grid.add(tsaField, 1, 5);
        grid.add(new Label("Motivo:"), 0, 6);
        grid.add(reasonField, 1, 6);
        grid.add(new Label("Lugar:"), 0, 7);
        grid.add(locationField, 1, 7);
        grid.add(visibleBox, 1, 8);
        dialog.getDialogPane().setContent(grid);

        Runnable updateVisibility = () -> {
            boolean p12 = method.getValue().startsWith("Archivo");
            boolean pkcs11 = method.getValue().startsWith("DNIe");
            setRowVisible(certLabel, certRow, p12);
            setRowVisible(pwdLabel, passwordField, p12);
            setRowVisible(driverLabel, driverRow, pkcs11);
            setRowVisible(pinLabel, pinField, pkcs11);
        };
        method.valueProperty().addListener((o, a, b) -> updateVisibility.run());
        updateVisibility.run();

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            SigningMethod selected = switch (method.getValue()) {
                case "Almacén de Windows" -> SigningMethod.WINDOWS;
                case "DNIe / tarjeta (PKCS#11)" -> SigningMethod.PKCS11;
                default -> SigningMethod.PKCS12;
            };
            if (selected == SigningMethod.PKCS12
                    && (certField.getText().isBlank() || passwordField.getText().isEmpty())) {
                return null;
            }
            if (selected == SigningMethod.PKCS11 && driverField.getText().isBlank()) {
                return null;
            }
            return new SigningOptions(selected,
                    certField.getText(), passwordField.getText().toCharArray(),
                    driverField.getText(), pinField.getText().toCharArray(),
                    tsaField.getText(), reasonField.getText(), locationField.getText(),
                    visibleBox.isSelected());
        });
        return dialog.showAndWait();
    }

    private void setRowVisible(Label label, javafx.scene.Node field, boolean visible) {
        label.setVisible(visible);
        label.setManaged(visible);
        field.setVisible(visible);
        field.setManaged(visible);
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

    /** Resultado del diálogo de inserción de imagen: esquina y anchura (pt). */
    private record Placement(String corner, double width) {
    }

    /** Resultado del diálogo de inserción de texto: contenido, tamaño y esquina. */
    private record TextContent(String text, int fontSize, String corner) {
    }

    /** Origen del certificado para firmar. */
    private enum SigningMethod { PKCS12, WINDOWS, PKCS11 }

    /** Opciones recogidas en el diálogo de firma. */
    private record SigningOptions(SigningMethod method, String certPath, char[] password,
                                  String driverPath, char[] pin, String tsaUrl,
                                  String reason, String location, boolean visible) {

        /** Borra de memoria los secretos (contraseña/PIN) tras su uso. */
        void wipeSecrets() {
            if (password != null) {
                java.util.Arrays.fill(password, '\0');
            }
            if (pin != null) {
                java.util.Arrays.fill(pin, '\0');
            }
        }
    }
}
