package com.orsconsulting.orssuitepdf.signing;

import java.nio.file.Path;

/**
 * Parámetros de una operación de firma PAdES.
 *
 * @param pdf       documento a firmar
 * @param output    ruta de salida del PDF firmado
 * @param keystore  fichero PKCS#12 (.p12/.pfx) con el certificado y la clave
 * @param password  contraseña del keystore (responsabilidad del llamante
 *                  gestionarla; no se persiste)
 * @param tsaUrl    URL del servicio de sello de tiempo (TSA); si es nula o
 *                  vacía, se firma directamente en nivel B (sin sello)
 * @param reason    motivo de la firma (opcional)
 * @param location  lugar de la firma (opcional)
 */
public record SignRequest(
        Path pdf,
        Path output,
        Path keystore,
        char[] password,
        String tsaUrl,
        String reason,
        String location) {

    public boolean requestsTimestamp() {
        return tsaUrl != null && !tsaUrl.isBlank();
    }
}
