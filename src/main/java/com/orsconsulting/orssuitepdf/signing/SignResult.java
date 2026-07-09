package com.orsconsulting.orssuitepdf.signing;

import java.nio.file.Path;

/**
 * Resultado de una firma: ruta del PDF firmado y nivel obtenido.
 *
 * @param output      PDF firmado
 * @param timestamped {@code true} si se logró PAdES-B-T (con sello de tiempo);
 *                    {@code false} si se cayó al modo offline PAdES-B
 */
public record SignResult(Path output, boolean timestamped) {

    /** Descripción legible del nivel de firma alcanzado. */
    public String levelDescription() {
        return timestamped
                ? "PAdES-B-T (con sello de tiempo)"
                : "PAdES-B (sin sello de tiempo)";
    }
}
