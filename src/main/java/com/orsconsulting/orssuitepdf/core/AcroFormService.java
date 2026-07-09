package com.orsconsulting.orssuitepdf.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDChoice;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDPushButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

/**
 * Lectura y escritura de campos de formulario (AcroForm) de un PDF, con
 * conversión al modelo {@link FormField} de la aplicación.
 */
public final class AcroFormService {

    private AcroFormService() {
    }

    /** {@code true} si el documento tiene un formulario con campos. */
    public static boolean hasForm(PDDocument document) {
        PDAcroForm form = document.getDocumentCatalog().getAcroForm();
        return form != null && !form.getFields().isEmpty();
    }

    /** Lee los campos de formulario editables del documento. */
    public static List<FormField> read(PDDocument document) {
        List<FormField> fields = new ArrayList<>();
        PDAcroForm form = document.getDocumentCatalog().getAcroForm();
        if (form == null) {
            return fields;
        }
        for (PDField field : form.getFieldTree()) {
            if (field instanceof PDTerminalField && !(field instanceof PDPushButton)) {
                fields.add(toFormField(field));
            }
        }
        return fields;
    }

    private static FormField toFormField(PDField field) {
        String name = field.getFullyQualifiedName();
        String value = field.getValueAsString();
        boolean readOnly = field.isReadOnly();

        if (field instanceof PDTextField) {
            return new FormField(name, FormField.Type.TEXT, value,
                    Collections.emptyList(), null, readOnly);
        }
        if (field instanceof PDCheckBox checkBox) {
            return new FormField(name, FormField.Type.CHECKBOX, value,
                    Collections.emptyList(), checkBox.getOnValue(), readOnly);
        }
        if (field instanceof PDChoice choice) {
            List<String> options = new ArrayList<>(choice.getOptionsDisplayValues());
            if (options.isEmpty()) {
                options = new ArrayList<>(choice.getOptions());
            }
            // getValueAsString() de un choice devuelve la representación de
            // array ("[valor]"); se toma el valor seleccionado real.
            List<String> selected = choice.getValue();
            String choiceValue = selected.isEmpty() ? "" : selected.get(0);
            return new FormField(name, FormField.Type.CHOICE, choiceValue, options, null, readOnly);
        }
        return new FormField(name, FormField.Type.OTHER, value,
                Collections.emptyList(), null, true);
    }

    /**
     * Aplica los valores indicados (por nombre cualificado) a los campos del
     * documento. Los campos inexistentes o de solo lectura se ignoran.
     */
    public static void apply(PDDocument document, Map<String, String> values) throws IOException {
        PDAcroForm form = document.getDocumentCatalog().getAcroForm();
        if (form == null) {
            return;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            PDField field = form.getField(entry.getKey());
            if (field != null && !field.isReadOnly() && !(field instanceof PDPushButton)) {
                field.setValue(entry.getValue());
            }
        }
    }

    /**
     * Aplana el formulario: convierte los valores en contenido fijo y elimina
     * los campos editables. Operación irreversible sobre el documento.
     */
    public static void flatten(PDDocument document) throws IOException {
        PDAcroForm form = document.getDocumentCatalog().getAcroForm();
        if (form != null) {
            form.flatten();
        }
    }
}
