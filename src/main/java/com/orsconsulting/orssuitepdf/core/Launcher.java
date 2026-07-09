package com.orsconsulting.orssuitepdf.core;

/**
 * Lanzador de la aplicación para ejecución desde un JAR único (fat jar) o
 * desde un empaquetado de jpackage.
 *
 * <p>No extiende {@link javafx.application.Application} a propósito: cuando la
 * clase principal del manifiesto extiende {@code Application} y JavaFX no está
 * en el <em>module path</em> (como ocurre con un fat jar en el classpath), el
 * arranque falla con "JavaFX runtime components are missing". Delegar aquí en
 * {@link Main} evita ese problema.</p>
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}
