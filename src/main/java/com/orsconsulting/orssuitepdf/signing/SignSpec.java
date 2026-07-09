package com.orsconsulting.orssuitepdf.signing;

import java.nio.file.Path;

/**
 * Parámetros de una firma PAdES, independientes del origen del certificado
 * (fichero, almacén de Windows o tarjeta/DNIe), que se pasa aparte como token.
 *
 * @param pdf      documento a firmar
 * @param output   ruta de salida del PDF firmado
 * @param tsaUrl   URL del servicio de sello de tiempo; si es nula o vacía, se
 *                 firma directamente en nivel B (sin sello)
 * @param reason   motivo de la firma (opcional)
 * @param location lugar de la firma (opcional)
 * @param visible  apariencia visible en el documento; {@code null} para firma
 *                 solo criptográfica (invisible)
 */
public record SignSpec(
        Path pdf,
        Path output,
        String tsaUrl,
        String reason,
        String location,
        VisibleSignature visible) {

    public boolean requestsTimestamp() {
        return tsaUrl != null && !tsaUrl.isBlank();
    }
}
