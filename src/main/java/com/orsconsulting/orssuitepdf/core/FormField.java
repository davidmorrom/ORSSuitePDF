package com.orsconsulting.orssuitepdf.core;

import java.util.List;

/**
 * Representación desacoplada de un campo de formulario (AcroForm) para la
 * interfaz: nombre cualificado, tipo, valor actual y metadatos según el tipo.
 */
public final class FormField {

    /** Tipos de campo soportados en la edición. */
    public enum Type {
        /** Campo de texto libre. */
        TEXT,
        /** Casilla de verificación. */
        CHECKBOX,
        /** Lista o desplegable con opciones. */
        CHOICE,
        /** Otros (radio, botón, firma…): solo lectura en el MVP. */
        OTHER
    }

    private final String name;
    private final Type type;
    private String value;
    private final List<String> options;
    private final String onValue;
    private final boolean readOnly;

    public FormField(String name, Type type, String value, List<String> options,
                     String onValue, boolean readOnly) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.options = options;
        this.onValue = onValue;
        this.readOnly = readOnly;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    /** Opciones disponibles para campos {@link Type#CHOICE}; vacío en otros. */
    public List<String> getOptions() {
        return options;
    }

    /** Valor "marcado" de una casilla {@link Type#CHECKBOX}. */
    public String getOnValue() {
        return onValue;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    /** {@code true} si la casilla está marcada (solo para CHECKBOX). */
    public boolean isChecked() {
        return type == Type.CHECKBOX && onValue != null && onValue.equals(value);
    }
}
