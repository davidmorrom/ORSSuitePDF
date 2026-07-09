package com.orsconsulting.orssuitepdf.ui;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import com.orsconsulting.orssuitepdf.core.PdfDocument;

import org.apache.pdfbox.text.PDFTextStripper;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Paleta de comandos (Ctrl+K): buscador universal que filtra en vivo los
 * comandos de la aplicación y las coincidencias de texto dentro del documento
 * abierto («EN ESTE DOCUMENTO»). Navegable por teclado: ↑↓ para moverse,
 * ⏎ para ejecutar y esc para cerrar.
 *
 * <p>Flota sobre el lienzo con un velo oscuro, según la pantalla «C» de la
 * especificación «Cobalto flotante».</p>
 */
final class CommandPalette {

    /** Comando ejecutable desde la paleta. */
    record Command(String title, String subtitle, Feather icon, String shortcut,
                   boolean needsDocument, Runnable action) {
    }

    /** Coincidencia de texto en el documento, con su contexto. */
    private record DocMatch(int page, String before, String match, String after) {
    }

    /** Elemento seleccionable de la lista (nodo visual + acción). */
    private record Entry(HBox node, Runnable action) {
    }

    private static final int MAX_COMMANDS = 7;
    private static final int MAX_MATCHES = 5;

    private final List<Command> commands;
    private final Supplier<AppState> stateSupplier;
    /** Al elegir una coincidencia: (consulta, página) para navegar y resaltar. */
    private final BiConsumer<String, Integer> onMatch;

    private final StackPane overlay = new StackPane();
    private final TextField field = new TextField();
    private final VBox body = new VBox();
    private final ScrollPane bodyScroll = new ScrollPane(body);
    private final PauseTransition debounce = new PauseTransition(Duration.millis(280));

    private final List<Entry> entries = new ArrayList<>();
    private int selectedIndex;

    /** Caché del texto por página del documento activo (se invalida por revisión). */
    private PdfDocument cachedDocument;
    private int cachedRevision = -1;
    private String[] pageTexts;
    private final AtomicLong searchSeq = new AtomicLong();

    CommandPalette(List<Command> commands, Supplier<AppState> stateSupplier,
                   BiConsumer<String, Integer> onMatch) {
        this.commands = commands;
        this.stateSupplier = stateSupplier;
        this.onMatch = onMatch;
        build();
    }

    /** Nodo a insertar (oculto) sobre el lienzo. */
    StackPane getOverlay() {
        return overlay;
    }

    private void build() {
        Region scrim = new Region();
        scrim.getStyleClass().add("palette-scrim");
        scrim.setOnMouseClicked(e -> hide());

        field.getStyleClass().add("palette-field");
        field.setPromptText("Escribe un comando o busca en el documento…");
        HBox.setHgrow(field, Priority.ALWAYS);
        Label esc = new Label("esc");
        esc.getStyleClass().add("kbd");
        HBox header = new HBox(11, FontIcon.of(Feather.SEARCH), field, esc);
        header.getStyleClass().add("palette-header");

        body.getStyleClass().add("palette-body");
        bodyScroll.setFitToWidth(true);
        bodyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        bodyScroll.setMaxHeight(430);

        HBox footer = new HBox(new Label("↑↓ navegar"), new Label("⏎ ejecutar"), new Label("esc cerrar"));
        footer.setSpacing(14);
        footer.getStyleClass().add("palette-footer");

        VBox card = new VBox(header, bodyScroll, footer);
        card.getStyleClass().add("palette-card");
        card.setMaxWidth(600);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(card, Pos.TOP_CENTER);
        StackPane.setMargin(card, new Insets(56, 0, 0, 0));

        overlay.getChildren().addAll(scrim, card);
        overlay.setVisible(false);

        field.textProperty().addListener((obs, oldText, newText) -> populate(newText));
        field.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ESCAPE -> hide();
                case DOWN -> moveSelection(1);
                case UP -> moveSelection(-1);
                case ENTER -> executeSelected();
                default -> {
                }
            }
            if (e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.UP) {
                e.consume();
            }
        });
        overlay.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                hide();
            }
        });
    }

    void show() {
        overlay.setVisible(true);
        field.clear();
        populate("");
        Platform.runLater(field::requestFocus);
    }

    void hide() {
        overlay.setVisible(false);
        debounce.stop();
    }

    boolean isShowing() {
        return overlay.isVisible();
    }

    // ------------------------------------------------------------ contenido

    private void populate(String query) {
        entries.clear();
        body.getChildren().clear();
        selectedIndex = 0;

        AppState state = stateSupplier.get();
        boolean hasDocument = state.hasDocument();
        String normalized = normalize(query);

        List<Command> visible = commands.stream()
                .filter(c -> !c.needsDocument() || hasDocument)
                .filter(c -> normalized.isEmpty()
                        || normalize(c.title()).contains(normalized)
                        || (c.subtitle() != null && normalize(c.subtitle()).contains(normalized)))
                .limit(MAX_COMMANDS)
                .toList();

        if (!visible.isEmpty()) {
            body.getChildren().add(sectionLabel("COMANDOS"));
            for (Command command : visible) {
                HBox item = commandItem(command);
                entries.add(new Entry(item, () -> command.action().run()));
                body.getChildren().add(item);
            }
        } else if (query.isBlank()) {
            body.getChildren().add(sectionLabel("COMANDOS"));
        }

        if (visible.isEmpty() && (!hasDocument || query.trim().length() < 2)) {
            Label none = new Label("Sin resultados");
            none.getStyleClass().add("item-subtitle");
            HBox empty = new HBox(none);
            empty.getStyleClass().add("palette-item");
            body.getChildren().add(empty);
        }

        applySelection();

        // Coincidencias dentro del documento, con un pequeño retardo para no
        // rebuscar en cada pulsación.
        if (hasDocument && query.trim().length() >= 2) {
            String pending = query.trim();
            debounce.setOnFinished(e -> searchDocument(pending));
            debounce.playFromStart();
        } else {
            debounce.stop();
        }
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("palette-section");
        return label;
    }

    private HBox commandItem(Command command) {
        StackPane chip = new StackPane(FontIcon.of(command.icon()));
        chip.getStyleClass().add("item-chip");

        Label title = new Label(command.title());
        title.getStyleClass().add("item-title");
        VBox text = new VBox(title);
        if (command.subtitle() != null) {
            Label subtitle = new Label(command.subtitle());
            subtitle.getStyleClass().add("item-subtitle");
            text.getChildren().add(subtitle);
        }
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox item = new HBox(11, chip, text);
        item.getStyleClass().add("palette-item");
        item.setAlignment(Pos.CENTER_LEFT);
        if (command.shortcut() != null) {
            Label shortcut = new Label(command.shortcut());
            shortcut.getStyleClass().add("item-shortcut");
            item.getChildren().add(shortcut);
        }
        item.setOnMouseClicked(e -> execute(new Entry(item, command.action())));
        return item;
    }

    private HBox matchItem(String query, DocMatch match) {
        StackPane chip = new StackPane(FontIcon.of(Feather.FILE_TEXT));
        chip.getStyleClass().add("item-chip");

        Label before = new Label("«…" + match.before());
        before.getStyleClass().add("item-title");
        // El contexto anterior se recorta por la izquierda; la coincidencia y
        // la página nunca se recortan.
        before.setTextOverrun(javafx.scene.control.OverrunStyle.LEADING_ELLIPSIS);
        Label hit = new Label(match.match());
        hit.getStyleClass().add("match-highlight");
        hit.setMinWidth(Region.USE_PREF_SIZE);
        Label after = new Label(match.after() + "…»");
        after.getStyleClass().add("item-title");
        HBox text = new HBox(before, hit, after);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label page = new Label("pág. " + (match.page() + 1));
        page.getStyleClass().add("item-shortcut");
        page.setMinWidth(Region.USE_PREF_SIZE);

        HBox item = new HBox(11, chip, text, page);
        item.getStyleClass().add("palette-item");
        item.setAlignment(Pos.CENTER_LEFT);
        Runnable action = () -> onMatch.accept(query, match.page());
        item.setOnMouseClicked(e -> execute(new Entry(item, action)));
        return item;
    }

    // ------------------------------------------------- selección y ejecución

    private void moveSelection(int direction) {
        if (entries.isEmpty()) {
            return;
        }
        selectedIndex = (selectedIndex + direction + entries.size()) % entries.size();
        applySelection();
    }

    private void applySelection() {
        for (int i = 0; i < entries.size(); i++) {
            List<String> classes = entries.get(i).node().getStyleClass();
            classes.remove("selected");
            if (i == selectedIndex) {
                classes.add("selected");
            }
        }
    }

    private void executeSelected() {
        if (selectedIndex >= 0 && selectedIndex < entries.size()) {
            execute(entries.get(selectedIndex));
        }
    }

    private void execute(Entry entry) {
        hide();
        // Tras ocultar la paleta, para que diálogos y selecciones de zona
        // no queden debajo del velo.
        Platform.runLater(entry.action()::run);
    }

    // -------------------------------------------- búsqueda en el documento

    private void searchDocument(String query) {
        AppState state = stateSupplier.get();
        PdfDocument document = state.getDocument();
        if (document == null) {
            return;
        }
        int revision = state.revisionProperty().get();
        long seq = searchSeq.incrementAndGet();
        Thread worker = new Thread(() -> {
            try {
                String[] texts = pageTextsFor(document, revision);
                List<DocMatch> matches = findMatches(query, texts);
                Platform.runLater(() -> {
                    // Descarta resultados obsoletos (otra consulta en curso o
                    // paleta cerrada / texto cambiado).
                    if (seq != searchSeq.get() || !overlay.isVisible()
                            || !field.getText().trim().equals(query)) {
                        return;
                    }
                    appendMatches(query, matches);
                });
            } catch (Exception ignored) {
                // La búsqueda en vivo nunca debe interrumpir la paleta.
            }
        }, "palette-search");
        worker.setDaemon(true);
        worker.start();
    }

    private void appendMatches(String query, List<DocMatch> matches) {
        if (matches.isEmpty()) {
            return;
        }
        body.getChildren().add(sectionLabel("EN ESTE DOCUMENTO"));
        for (DocMatch match : matches) {
            HBox item = matchItem(query, match);
            entries.add(new Entry(item, () -> onMatch.accept(query, match.page())));
            body.getChildren().add(item);
        }
        applySelection();
    }

    /** Extrae (y cachea) el texto de cada página del documento. */
    private String[] pageTextsFor(PdfDocument document, int revision) throws Exception {
        synchronized (this) {
            if (cachedDocument == document && cachedRevision == revision && pageTexts != null) {
                return pageTexts;
            }
        }
        int pages = document.pageCount();
        String[] texts = new String[pages];
        PDFTextStripper stripper = new PDFTextStripper();
        // El renderizado de PDFBox no es reentrante: se serializa contra el
        // visor sincronizando sobre el mismo documento que usa el render.
        synchronized (document) {
            for (int page = 0; page < pages; page++) {
                stripper.setStartPage(page + 1);
                stripper.setEndPage(page + 1);
                texts[page] = stripper.getText(document.pdbox());
            }
        }
        synchronized (this) {
            cachedDocument = document;
            cachedRevision = revision;
            pageTexts = texts;
        }
        return texts;
    }

    private List<DocMatch> findMatches(String query, String[] texts) {
        List<DocMatch> out = new ArrayList<>();
        String needle = query.toLowerCase();
        for (int page = 0; page < texts.length && out.size() < MAX_MATCHES; page++) {
            String text = texts[page];
            if (text == null || text.isBlank()) {
                continue;
            }
            String collapsed = text.replaceAll("\\s+", " ");
            String lower = collapsed.toLowerCase();
            int index = lower.indexOf(needle);
            while (index >= 0 && out.size() < MAX_MATCHES) {
                int start = Math.max(0, index - 30);
                int end = Math.min(collapsed.length(), index + needle.length() + 42);
                out.add(new DocMatch(page,
                        collapsed.substring(start, index),
                        collapsed.substring(index, index + needle.length()),
                        collapsed.substring(index + needle.length(), end)));
                index = lower.indexOf(needle, index + needle.length());
            }
        }
        return out;
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .trim();
    }
}
