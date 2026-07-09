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
 * Recursos de marca (colores, iconos y logotipos) de ORS Suite PDF según el
 * lenguaje visual «Cobalto flotante»: acento #3D5BF5 sobre tinta #0F1522,
 * con tintes claros #8B9DFF / #EDF0FE. Los PNG se cargan desde el classpath
 * ({@code /branding}); el isotipo se reconstruye en vectorial para que sea
 * nítido a cualquier tamaño.
 */
public final class Branding {

    /** Acento de marca (cobalto). */
    public static final String ACCENT = "#3D5BF5";
    /** Acento en hover / pulsado. */
    public static final String ACCENT_DOWN = "#2A44D4";
    /** Tinta (texto principal, superficies oscuras). */
    public static final String INK = "#0F1522";
    /** Acento claro (detalles, satélites de la marca). */
    public static final String ACCENT_LIGHT = "#8B9DFF";
    /** Tinte de acento para fondos suaves. */
    public static final String TINT = "#EDF0FE";
    /** Verde de confirmación. */
    public static final String OK = "#22B573";

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
     * Marca ORS (isotipo) en vectorial, en sus dos variantes de la
     * especificación: {@code ors-mark} (cuerpo cobalto, para fondos claros)
     * y {@code ors-mark-white} (cuerpo blanco, para fondos oscuros).
     *
     * @param size    lado en píxeles del contenedor cuadrado resultante
     * @param forDark {@code true} para la variante blanca sobre fondo oscuro
     */
    public static Node mark(double size, boolean forDark) {
        Color body = forDark ? Color.WHITE : Color.web(ACCENT);
        Color fold = forDark ? Color.web(ACCENT_LIGHT) : Color.web("#D9DFFC");
        Color pixel = forDark ? Color.web(ACCENT) : Color.WHITE;
        Color satBig = forDark ? Color.WHITE : Color.web(ACCENT);
        Color satSmall = forDark ? Color.web("#D9DFFC") : Color.web(ACCENT_LIGHT);

        Group mark = new Group();

        SVGPath sheet = new SVGPath();
        sheet.setContent("M10 4 H40 L54 18 V68 H10 Z");
        sheet.setFill(body);

        SVGPath corner = new SVGPath();
        corner.setContent("M40 4 L54 18 H40 Z");
        corner.setFill(fold);
        mark.getChildren().addAll(sheet, corner);

        for (int[] cell : MARK_PIXELS) {
            mark.getChildren().add(cell(cell[0], cell[1], 8, 8, pixel));
        }
        mark.getChildren().addAll(
                cell(58, 2, 9, 9, satBig),
                cell(66, 14, 5, 5, satSmall));

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
