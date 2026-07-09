package com.orsconsulting.orssuitepdf.ui;

import java.util.List;

import javafx.scene.image.Image;

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
}
