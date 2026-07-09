package com.orsconsulting.orssuitepdf.core;

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
        setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        MainView mainView = new MainView(stage);
        Scene scene = new Scene(mainView.getRoot(), 1280, 800);

        stage.setTitle("ORS Suite PDF");
        stage.setScene(scene);
        stage.show();

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
