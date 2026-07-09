package com.orsconsulting.orssuitepdf.core;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Punto de entrada de la aplicación.
 * MVP inicial: ventana básica con tema AtlantaFX. La lógica de PDF vive en
 * los paquetes core/ui/signing/ocr — ver arquitectura en docs/adr/.
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) {
        setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        StackPane root = new StackPane(new Label("ORS Suite PDF — MVP en construcción"));
        Scene scene = new Scene(root, 1280, 800);

        stage.setTitle("ORS Suite PDF");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
