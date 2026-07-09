package com.orsconsulting.orssuitepdf.ui;

import javafx.scene.text.Font;

/**
 * Registra las familias tipográficas de la interfaz (IBM Plex Sans para el
 * texto de UI e IBM Plex Mono para valores técnicos: nº de página, zoom,
 * coordenadas). Los TTF viajan en el classpath ({@code /fonts}) para que la
 * aplicación funcione sin conexión y sin depender de fuentes del sistema.
 */
public final class Fonts {

    /** Familia de la interfaz. */
    public static final String SANS = "IBM Plex Sans";
    /** Familia monoespaciada para valores técnicos. */
    public static final String MONO = "IBM Plex Mono";

    private static final String[] FILES = {
            "IBMPlexSans-Regular.ttf",
            "IBMPlexSans-Medium.ttf",
            "IBMPlexSans-SemiBold.ttf",
            "IBMPlexSans-Bold.ttf",
            "IBMPlexMono-Regular.ttf",
            "IBMPlexMono-Medium.ttf",
            "IBMPlexMono-SemiBold.ttf",
    };

    private static boolean loaded;

    private Fonts() {
    }

    /** Carga las fuentes una sola vez. Silencioso si alguna no está presente. */
    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        for (String file : FILES) {
            try (var in = Fonts.class.getResourceAsStream("/fonts/" + file)) {
                if (in != null) {
                    Font.loadFont(in, 12);
                }
            } catch (Exception ignored) {
                // Si una fuente no carga, la UI cae a la familia de sistema
                // definida como fallback en app.css.
            }
        }
    }
}
