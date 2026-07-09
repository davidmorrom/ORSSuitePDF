package com.orsconsulting.orssuitepdf.ui;

import java.util.List;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

/**
 * Recursos de marca (iconos y logotipos) de ORS Suite PDF, cargados desde el
 * classpath ({@code /branding}).
 */
public final class Branding {

    /** Color primario de marca. */
    public static final String PRIMARY = "#1A5EA8";
    /** Azul profundo de marca. */
    public static final String DEEP = "#1C3C72";

    private Branding() {
    }

    private static Image load(String name) {
        return new Image(Branding.class.getResourceAsStream("/branding/" + name));
    }

    /** Iconos de la aplicación en varios tamaños, para la ventana y la barra de tareas. */
    public static List<Image> appIcons() {
        return List.of(
                load("icon-16.png"), load("icon-32.png"), load("icon-48.png"),
                load("icon-64.png"), load("icon-128.png"), load("icon-256.png"));
    }

    /** Logotipo horizontal; versión blanca para fondos oscuros. */
    public static Image horizontalLogo(boolean dark) {
        return load(dark ? "logo-horizontal-white.png" : "logo-horizontal.png");
    }

    /** Imagen del icono grande (para "Acerca de"). */
    public static Image symbol() {
        return load("icon-128.png");
    }

    /** Coordenadas (x, y) de los ocho "píxeles" del símbolo, en el lienzo 72×72. */
    private static final int[][] MARK_PIXELS = {
            {17, 26}, {28, 26}, {39, 26},
            {17, 37}, {28, 37}, {39, 37},
            {17, 48}, {28, 48}};

    /**
     * Marca ORS (isotipo) reconstruida en vectorial para el riel oscuro:
     * nítida a cualquier tamaño y sin depender de un PNG de imprenta. Se
     * corresponde con el símbolo {@code ors-mark-white} de la especificación.
     *
     * @param size lado en píxeles del contenedor cuadrado resultante
     */
    public static Node markWhite(double size) {
        Group mark = new Group();

        SVGPath body = new SVGPath();
        body.setContent("M10 4 H40 L54 18 V68 H10 Z");
        body.setFill(Color.WHITE);

        SVGPath corner = new SVGPath();
        corner.setContent("M40 4 L54 18 H40 Z");
        corner.setFill(Color.web("#4A90D9"));
        mark.getChildren().addAll(body, corner);

        Color pixel = Color.web("#1A5EA8");
        for (int[] cell : MARK_PIXELS) {
            mark.getChildren().add(cell(cell[0], cell[1], 8, 8, pixel));
        }
        mark.getChildren().addAll(
                cell(58, 2, 9, 9, Color.WHITE),
                cell(66, 14, 5, 5, Color.web("#C8DEF5")));

        double scale = size / 72.0;
        mark.getTransforms().add(new Scale(scale, scale));

        StackPane holder = new StackPane(mark);
        holder.setMinSize(size, size);
        holder.setPrefSize(size, size);
        holder.setMaxSize(size, size);
        return holder;
    }

    private static Rectangle cell(double x, double y, double w, double h, Color fill) {
        Rectangle rect = new Rectangle(x, y, w, h);
        rect.setArcWidth(2);
        rect.setArcHeight(2);
        rect.setFill(fill);
        return rect;
    }
}
