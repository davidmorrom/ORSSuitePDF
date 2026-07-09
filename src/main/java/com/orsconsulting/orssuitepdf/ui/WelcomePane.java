package com.orsconsulting.orssuitepdf.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Pantalla de inicio que se muestra cuando no hay ningún documento abierto.
 * Ofrece accesos directos a las acciones principales (abrir, unir) y la lista
 * de archivos recientes.
 */
public final class WelcomePane extends VBox {

    /**
     * @param onOpen        acción de "Abrir…"
     * @param onMerge       acción de "Unir PDF…"
     * @param recent        rutas recientes (puede estar vacía)
     * @param onOpenRecent  acción al elegir un reciente
     */
    public WelcomePane(Runnable onOpen, Runnable onMerge,
                       List<Path> recent, Consumer<Path> onOpenRecent) {
        setAlignment(Pos.CENTER);
        setSpacing(24);
        setPadding(new Insets(48));
        setFillWidth(false);

        Label title = new Label("ORS Suite PDF");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");
        Label subtitle = new Label("Editor de PDF profesional, offline-first");
        subtitle.getStyleClass().add("text-muted");

        Button open = new Button("Abrir PDF…");
        open.setOnAction(e -> onOpen.run());
        open.setPrefWidth(180);
        Button merge = new Button("Unir PDF…");
        merge.setOnAction(e -> onMerge.run());
        merge.setPrefWidth(180);
        HBox actions = new HBox(16, open, merge);
        actions.setAlignment(Pos.CENTER);

        Label hint = new Label("También puedes arrastrar y soltar archivos PDF sobre la ventana.");
        hint.getStyleClass().add("text-muted");

        VBox recentBox = new VBox(6);
        recentBox.setAlignment(Pos.CENTER_LEFT);
        recentBox.setMaxWidth(420);
        Label recentTitle = new Label("Recientes");
        recentTitle.setStyle("-fx-font-weight: bold;");
        recentBox.getChildren().add(recentTitle);
        if (recent.isEmpty()) {
            Label none = new Label("(todavía no has abierto ningún documento)");
            none.getStyleClass().add("text-muted");
            recentBox.getChildren().add(none);
        } else {
            for (Path path : recent) {
                Hyperlink link = new Hyperlink(path.getFileName().toString());
                link.setTooltip(new javafx.scene.control.Tooltip(path.toString()));
                link.setOnAction(e -> onOpenRecent.accept(path));
                recentBox.getChildren().add(link);
            }
        }

        Region spacerTop = new Region();
        Region spacerBottom = new Region();
        VBox.setVgrow(spacerTop, Priority.ALWAYS);
        VBox.setVgrow(spacerBottom, Priority.ALWAYS);

        getChildren().addAll(spacerTop, title, subtitle, actions, hint, recentBox, spacerBottom);
    }
}
