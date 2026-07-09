package com.orsconsulting.orssuitepdf.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;

import com.orsconsulting.orssuitepdf.core.PdfDocument;

import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Pantalla de inicio (pantalla «A» del spec «Cobalto flotante»): zona de
 * arrastre con borde discontinuo, chips de acciones rápidas y rejilla de
 * documentos recientes con miniatura de la primera página, número de páginas
 * y antigüedad — estos últimos se cargan en segundo plano.
 */
public final class WelcomePane extends VBox {

    private static final int MAX_CARDS = 8;

    /**
     * @param onOpen        acción de "Abrir…"
     * @param onQuickAction acción rápida: {@code merge | split | sign | protect}
     * @param recent        rutas recientes (puede estar vacía)
     * @param onOpenRecent  acción al elegir un reciente
     */
    public WelcomePane(Runnable onOpen, Consumer<String> onQuickAction,
                       List<Path> recent, Consumer<Path> onOpenRecent) {
        getStyleClass().add("welcome");
        setAlignment(Pos.TOP_CENTER);
        setPadding(new Insets(40, 60, 24, 60));

        getChildren().add(buildDropZone(onOpen));

        HBox quick = new HBox(10,
                quickChip("Unir PDFs", Feather.LAYERS, () -> onQuickAction.accept("merge")),
                quickChip("Dividir documento", Feather.SCISSORS, () -> onQuickAction.accept("split")),
                quickChip("Firmar", Feather.FEATHER, () -> onQuickAction.accept("sign")),
                quickChip("Proteger", Feather.LOCK, () -> onQuickAction.accept("protect")));
        quick.setAlignment(Pos.CENTER);
        VBox.setMargin(quick, new Insets(20, 0, 0, 0));
        getChildren().add(quick);

        Region recents = buildRecents(recent, onOpenRecent);
        VBox.setMargin(recents, new Insets(44, 0, 0, 0));
        getChildren().add(recents);
    }

    // ------------------------------------------------------ zona de arrastre

    private Region buildDropZone(Runnable onOpen) {
        StackPane icon = new StackPane(FontIcon.of(Feather.UPLOAD));
        icon.getStyleClass().add("drop-icon");

        Label title = new Label("Arrastra un PDF aquí");
        title.getStyleClass().add("drop-title");

        Label kbd = new Label("Ctrl O");
        kbd.getStyleClass().addAll("kbd", "accent");
        HBox hint = new HBox(6, new Label("o pulsa"), kbd, new Label("para abrir un archivo"));
        hint.setAlignment(Pos.CENTER);
        for (var node : hint.getChildren()) {
            if (node instanceof Label label && !label.getStyleClass().contains("kbd")) {
                label.getStyleClass().add("drop-hint");
            }
        }

        VBox zone = new VBox(12, icon, title, hint);
        zone.getStyleClass().add("drop-zone");
        zone.setMaxWidth(720);
        zone.setMinHeight(220);
        zone.setPrefHeight(220);
        zone.setMaxHeight(220);
        zone.setCursor(javafx.scene.Cursor.HAND);
        zone.setOnMouseClicked(e -> onOpen.run());
        // El drop real lo gestiona la ventana; aquí solo el estado visual.
        zone.setOnDragEntered(e -> {
            if (e.getDragboard().hasFiles() && !zone.getStyleClass().contains("armed")) {
                zone.getStyleClass().add("armed");
            }
        });
        zone.setOnDragExited(e -> zone.getStyleClass().remove("armed"));
        return zone;
    }

    private Button quickChip(String text, Feather ikon, Runnable action) {
        Button chip = new Button(text, FontIcon.of(ikon));
        chip.getStyleClass().add("quick-chip");
        chip.setOnAction(e -> action.run());
        return chip;
    }

    // ------------------------------------------------------------- recientes

    private Region buildRecents(List<Path> recent, Consumer<Path> onOpenRecent) {
        VBox block = new VBox(14);
        block.setMaxWidth(1000);
        block.setAlignment(Pos.TOP_LEFT);

        Label title = new Label("Recientes");
        title.getStyleClass().add("recent-title");
        Label caption = new Label(recent.isEmpty()
                ? "todavía no has abierto ningún documento"
                : "abiertos recientemente");
        caption.getStyleClass().add("recent-caption");
        HBox header = new HBox(10, title, caption);
        header.setAlignment(Pos.BASELINE_LEFT);
        block.getChildren().add(header);

        if (!recent.isEmpty()) {
            GridPane grid = new GridPane();
            grid.setHgap(14);
            grid.setVgap(14);
            for (int i = 0; i < 4; i++) {
                ColumnConstraints column = new ColumnConstraints();
                column.setPercentWidth(25);
                column.setHalignment(HPos.LEFT);
                column.setFillWidth(true);
                grid.getColumnConstraints().add(column);
            }
            List<Path> shown = recent.stream().limit(MAX_CARDS).toList();
            for (int i = 0; i < shown.size(); i++) {
                Region card = buildCard(shown.get(i), onOpenRecent);
                GridPane.setFillWidth(card, true);
                grid.add(card, i % 4, i / 4);
            }
            block.getChildren().add(grid);
        }
        return block;
    }

    private Region buildCard(Path path, Consumer<Path> onOpenRecent) {
        StackPane thumb = new StackPane(FontIcon.of(Feather.FILE_TEXT));
        thumb.getStyleClass().add("recent-thumb");

        Label name = new Label(path.getFileName().toString());
        name.getStyleClass().add("recent-name");
        FontIcon lock = FontIcon.of(Feather.LOCK);
        lock.setVisible(false);
        HBox nameRow = new HBox(6, name, lock);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        Label meta = new Label(relativeAge(path));
        meta.getStyleClass().add("recent-meta");

        VBox card = new VBox(3, thumb, nameRow, meta);
        VBox.setMargin(nameRow, new Insets(9, 0, 0, 0));
        card.getStyleClass().add("recent-card");
        card.setOnMouseClicked(e -> onOpenRecent.accept(path));
        Tooltip.install(card, new Tooltip(path.toString()));

        loadDetails(path, thumb, meta, lock);
        return card;
    }

    /** Completa miniatura y nº de páginas sin bloquear la interfaz. */
    private void loadDetails(Path path, StackPane thumb, Label meta, FontIcon lock) {
        String age = relativeAge(path);
        Thread worker = new Thread(() -> {
            try (PdfDocument document = PdfDocument.open(path)) {
                int pages = document.pageCount();
                Image image = document.renderPage(0, 18f);
                Platform.runLater(() -> {
                    meta.setText(age + " · " + pages + (pages == 1 ? " página" : " páginas"));
                    ImageView view = new ImageView(image);
                    view.setPreserveRatio(true);
                    view.setFitHeight(90);
                    view.setSmooth(true);
                    thumb.getChildren().setAll(view);
                });
            } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException protectedPdf) {
                Platform.runLater(() -> {
                    meta.setText(age + " · protegido");
                    lock.setVisible(true);
                });
            } catch (Exception unreadable) {
                Platform.runLater(() -> meta.setText(age + " · no legible"));
            }
        }, "welcome-thumbs");
        worker.setDaemon(true);
        worker.start();
    }

    /** Antigüedad legible del archivo: «hace 2 h», «ayer», «hace 3 días»… */
    private static String relativeAge(Path path) {
        try {
            Instant modified = Files.getLastModifiedTime(path).toInstant();
            long minutes = ChronoUnit.MINUTES.between(modified, Instant.now());
            if (minutes < 60) {
                return "hace " + Math.max(1, minutes) + " min";
            }
            long hours = minutes / 60;
            if (hours < 24) {
                return "hace " + hours + " h";
            }
            long days = hours / 24;
            if (days == 1) {
                return "ayer";
            }
            if (days < 30) {
                return "hace " + days + " días";
            }
            return "hace tiempo";
        } catch (Exception e) {
            return "";
        }
    }
}
