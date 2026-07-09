package com.orsconsulting.orssuitepdf.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.orsconsulting.orssuitepdf.core.AcroFormService;
import com.orsconsulting.orssuitepdf.core.FormField;
import com.orsconsulting.orssuitepdf.core.PdfDocument;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Panel de edición de campos de formulario (AcroForm) del documento abierto.
 * Muestra un editor por campo (texto, casilla o desplegable) y permite
 * aplicar los valores al documento o aplanar el formulario.
 */
public final class FormPanel extends BorderPane {

    private final AppState state;
    private final VBox fieldsBox = new VBox(10);
    private final Label placeholder = new Label("El documento no tiene formulario");
    private final Button applyButton = new Button("Aplicar");
    private final Button flattenButton = new Button("Aplanar");

    private final List<FieldRow> rows = new ArrayList<>();

    public FormPanel(AppState state) {
        this.state = state;

        fieldsBox.setPadding(new Insets(12));
        placeholder.getStyleClass().add("text-muted");

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setContent(fieldsBox);

        applyButton.setOnAction(e -> applyValues());
        flattenButton.setOnAction(e -> flatten());
        HBox buttons = new HBox(8, applyButton, flattenButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(8));

        setCenter(scroll);
        setBottom(buttons);

        state.documentProperty().addListener((obs, oldDoc, newDoc) -> reload());
        state.revisionProperty().addListener((obs, oldR, newR) -> reload());
        reload();
    }

    private void reload() {
        rows.clear();
        fieldsBox.getChildren().clear();

        PdfDocument document = state.getDocument();
        if (document == null || !AcroFormService.hasForm(document.pdbox())) {
            fieldsBox.getChildren().add(placeholder);
            applyButton.setDisable(true);
            flattenButton.setDisable(true);
            return;
        }

        List<FormField> fields = AcroFormService.read(document.pdbox());
        for (FormField field : fields) {
            fieldsBox.getChildren().add(buildRow(field));
        }
        boolean editable = rows.stream().anyMatch(row -> row.valueReader() != null);
        applyButton.setDisable(!editable);
        flattenButton.setDisable(false);
    }

    private Region buildRow(FormField field) {
        Label label = new Label(field.getName());
        label.setWrapText(true);
        label.getStyleClass().add("text-bold");

        Region editor;
        Supplier<String> reader;

        switch (field.getType()) {
            case TEXT -> {
                TextField input = new TextField(field.getValue());
                input.setDisable(field.isReadOnly());
                editor = input;
                reader = input::getText;
            }
            case CHECKBOX -> {
                CheckBox check = new CheckBox("Marcado");
                check.setSelected(field.isChecked());
                check.setDisable(field.isReadOnly());
                String onValue = field.getOnValue() != null ? field.getOnValue() : "On";
                editor = check;
                reader = () -> check.isSelected() ? onValue : "Off";
            }
            case CHOICE -> {
                ComboBox<String> combo = new ComboBox<>();
                combo.getItems().addAll(field.getOptions());
                combo.setValue(field.getValue());
                combo.setDisable(field.isReadOnly());
                combo.setMaxWidth(Double.MAX_VALUE);
                editor = combo;
                reader = combo::getValue;
            }
            default -> {
                TextField display = new TextField(field.getValue());
                display.setEditable(false);
                display.setDisable(true);
                editor = display;
                reader = null; // no editable
            }
        }

        rows.add(new FieldRow(field.getName(), reader));
        VBox row = new VBox(4, label, editor);
        VBox.setVgrow(editor, Priority.NEVER);
        return row;
    }

    private void applyValues() {
        PdfDocument document = state.getDocument();
        if (document == null) {
            return;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (FieldRow row : rows) {
            if (row.valueReader() != null) {
                String value = row.valueReader().get();
                values.put(row.name(), value != null ? value : "");
            }
        }
        try {
            AcroFormService.apply(document.pdbox(), values);
            state.markMutated();
        } catch (Exception ex) {
            showError("No se pudieron aplicar los valores", ex.getMessage());
        }
    }

    private void flatten() {
        PdfDocument document = state.getDocument();
        if (document == null) {
            return;
        }
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("Aplanar formulario");
        alert.setHeaderText("Aplanar convierte los campos en contenido fijo.");
        alert.setContentText("Esta acción no se puede deshacer. ¿Continuar?");
        alert.initOwner(getScene() != null ? getScene().getWindow() : null);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        try {
            AcroFormService.apply(document.pdbox(), collectValues());
            AcroFormService.flatten(document.pdbox());
            state.markMutated();
        } catch (Exception ex) {
            showError("No se pudo aplanar el formulario", ex.getMessage());
        }
    }

    private Map<String, String> collectValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (FieldRow row : rows) {
            if (row.valueReader() != null) {
                String value = row.valueReader().get();
                values.put(row.name(), value != null ? value : "");
            }
        }
        return values;
    }

    private void showError(String header, String detail) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(detail);
        alert.initOwner(getScene() != null ? getScene().getWindow() : null);
        alert.showAndWait();
    }

    /** Fila de campo: nombre cualificado y lector del valor del editor. */
    private record FieldRow(String name, Supplier<String> valueReader) {
    }
}
