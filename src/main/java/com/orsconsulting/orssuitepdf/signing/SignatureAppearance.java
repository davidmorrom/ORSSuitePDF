package com.orsconsulting.orssuitepdf.signing;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Opciones de apariencia de la firma visible: qué información se muestra y cómo.
 * A partir de estas opciones y de los datos del certificado se genera el texto
 * que se estampa en el documento.
 */
public record SignatureAppearance(
        boolean showHeading, String heading,
        boolean showName, boolean surnameFirst,
        boolean showNif,
        boolean showReason,
        boolean showLocation,
        boolean showDate, boolean showTime) {

    /** Apariencia por defecto: encabezado + nombre + NIF + fecha y hora. */
    public static SignatureAppearance defaults() {
        return new SignatureAppearance(
                true, "Firmado digitalmente por",
                true, false,
                true,
                false,
                false,
                true, true);
    }

    /**
     * Construye las líneas de texto de la firma a partir del certificado y del
     * contexto (motivo, ubicación, fecha/hora).
     */
    public List<String> buildLines(CertificateInfo info, String reason, String location,
                                   LocalDateTime when) {
        List<String> lines = new ArrayList<>();
        if (showHeading && heading != null && !heading.isBlank()) {
            lines.add(heading);
        }
        if (showName) {
            lines.add(info.fullName(surnameFirst));
        }
        if (showNif && info.hasNif()) {
            lines.add("NIF: " + info.nif());
        }
        if (showReason && reason != null && !reason.isBlank()) {
            lines.add("Motivo: " + reason);
        }
        if (showLocation && location != null && !location.isBlank()) {
            lines.add("Lugar: " + location);
        }
        if (showDate || showTime) {
            String pattern = showDate && showTime ? "yyyy.MM.dd HH:mm:ss"
                    : showDate ? "yyyy.MM.dd" : "HH:mm:ss";
            lines.add("Fecha: " + when.format(DateTimeFormatter.ofPattern(pattern)));
        }
        return lines;
    }

    /** Texto multilínea de la firma. */
    public String buildText(CertificateInfo info, String reason, String location,
                            LocalDateTime when) {
        return String.join("\n", buildLines(info, reason, location, when));
    }
}
