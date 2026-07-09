package com.orsconsulting.orssuitepdf.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Pantalla de inicio que se muestra cuando no hay ningún documento abierto.
 * Reproduce la especificación visual (pantalla «A»): logo centrado, acciones
 * principales, sugerencia de arrastrar y soltar, y tarjeta de recientes con
 * el número de páginas en tipografía monoespaciada.
 */
public final class WelcomePane extends VBox {

    /**
     * @param onOpen        acción de "Abrir…"
     * @param onMerge       acción de "Unir PDF…"
     * @param recent        rutas recientes (puede estar vacía)
     * @param onOpenRecent  acción al elegir un reciente
     */
    public WelcomePane(Runnable onOpen, Runnable onMerge,
                       List<Path> recent, Consumer<Path> onOpenRecent, boolean dark) {
        getStyleClass().add("welcome");
        setAlignment(Pos.CENTER);
        setSpacing(22);
        setPadding(new Insets(48));
        setFillWidth(false);

        ImageView logo = new ImageView(Branding.horizontalLogo(dark));
        logo.setPreserveRatio(true);
        logo.setFitWidth(300);

        Label subtitle = new Label("Editor de PDF profesional · offline-first");
        subtitle.getStyleClass().add("welcome-subtitle");

        Button open = new Button("Abrir PDF…", new FontIcon(Feather.FOLDER));
        open.getStyleClass().add("accent");
        open.setDefaultButton(true);
        open.setOnAction(e -> onOpen.run());
        Button merge = new Button("Unir PDF…", new FontIcon(Feather.LAYERS));
        merge.setOnAction(e -> onMerge.run());
        HBox actions = new HBox(16, open, merge);
        actions.setAlignment(Pos.CENTER);

        Label hint = new Label("También puedes arrastrar y soltar archivos PDF sobre la ventana.");
        hint.getStyleClass().add("welcome-hint");

        getChildren().addAll(spacer(), logo, subtitle, actions, hint, buildRecent(recent, onOpenRecent), spacer());
    }

    private Region buildRecent(List<Path> recent, Consumer<Path> onOpenRecent) {
        VBox card = new VBox(2);
        card.setMaxWidth(400);
        card.setPadding(new Insets(16, 20, 16, 20));
        card.setStyle("-fx-background-color: -color-bg-default; -fx-background-radius: 12;"
                + " -fx-border-color: -color-border-default; -fx-border-radius: 12;");

        Label title = new Label("RECIENTES");
        title.getStyleClass().add("welcome-recent-title");
        VBox.setMargin(title, new Insets(0, 0, 10, 0));
        card.getChildren().add(title);

        if (recent.isEmpty()) {
            Label none = new Label("(todavía no has abierto ningún documento)");
            none.getStyleClass().add("welcome-hint");
            card.getChildren().add(none);
        } else {
            for (Path path : recent) {
                card.getChildren().add(recentRow(path, onOpenRecent));
            }
        }
        return card;
    }

    private Region recentRow(Path path, Consumer<Path> onOpenRecent) {
        FontIcon icon = new FontIcon(Feather.FILE_TEXT);
        icon.setIconColor(javafx.scene.paint.Color.web(Branding.PRIMARY));
        Label name = new Label(path.getFileName().toString());
        name.setStyle("-fx-font-size: 13.5px; -fx-text-fill: -color-fg-default;");
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMaxWidth(Double.MAX_VALUE);

        HBox row = new HBox(11, icon, name);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8));
        row.setStyle("-fx-background-radius: 8; -fx-cursor: hand;");
        row.setOnMouseEntered(e -> row.setStyle(
                "-fx-background-radius: 8; -fx-cursor: hand; -fx-background-color: -color-bg-inset;"));
        row.setOnMouseExited(e -> row.setStyle("-fx-background-radius: 8; -fx-cursor: hand;"));
        row.setOnMouseClicked(e -> onOpenRecent.accept(path));
        javafx.scene.control.Tooltip.install(row, new javafx.scene.control.Tooltip(path.toString()));
        return row;
    }

    private Region spacer() {
        Region region = new Region();
        VBox.setVgrow(region, Priority.ALWAYS);
        return region;
    }
}
