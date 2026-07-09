package com.orsconsulting.orssuitepdf.core;

import com.orsconsulting.orssuitepdf.ui.Branding;
import com.orsconsulting.orssuitepdf.ui.Fonts;
import com.orsconsulting.orssuitepdf.ui.MainView;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Punto de entrada de la aplicación.
 *
 * <p>Monta la ventana principal ({@link MainView}) con el tema AtlantaFX. La
 * lógica de PDF vive en los paquetes {@code core}/{@code ui}/{@code signing}/
 * {@code ocr} — ver arquitectura en {@code docs/adr/}.</p>
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) {
        Fonts.load();
        setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        MainView mainView = new MainView(stage);
        Scene scene = new Scene(mainView.getRoot(), 1280, 800);

        stage.setTitle("ORS Suite PDF");
        stage.getIcons().setAll(Branding.appIcons());
        stage.setScene(scene);
        stage.show();

        // Cierra la pantalla splash (java.awt.SplashScreen) una vez montada la UI.
        try {
            java.awt.SplashScreen splash = java.awt.SplashScreen.getSplashScreen();
            if (splash != null) {
                splash.close();
            }
        } catch (Exception ignored) {
            // Sin splash disponible (p. ej. entorno headless): no es un error.
        }

        // Permite abrir PDF pasados como argumentos (asociación de archivos),
        // cada uno en su propia pestaña.
        for (String arg : getParameters().getRaw()) {
            java.nio.file.Path path = java.nio.file.Path.of(arg);
            if (java.nio.file.Files.exists(path)) {
                mainView.open(path);
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
