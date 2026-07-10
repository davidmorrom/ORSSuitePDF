package com.orsconsulting.orssuitepdf.signing;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Comprueba que la carga de las listas de confianza europeas (LOTL) funciona y
 * ancla las CA cualificadas españolas (FNMT), requisito para que la validación
 * de firmas de DNIe/FNMT deje de ser {@code INDETERMINATE}.
 *
 * <p>Requiere conexión (descarga el LOTL la primera vez), por eso solo se
 * ejecuta cuando se pide expresamente con {@code -Dors.online=true}; en CI/red
 * cerrada no debe romper la construcción.</p>
 */
class TrustServiceTest {

    @Test
    @EnabledIfSystemProperty(named = "ors.online", matches = "true")
    void loadsEuLotlAndTrustsFnmt() {
        TrustService.refreshNow();
        var source = TrustService.trustedSource();
        assertNotNull(source, "la fuente de confianza no debe ser nula");

        int total = source.getCertificates().size();
        long fnmt = source.getCertificates().stream()
                .map(c -> c.getCertificate().getSubjectX500Principal().getName())
                .filter(dn -> dn.contains("FNMT"))
                .count();

        assertTrue(total > 1000,
                "el LOTL debe aportar miles de anclas de confianza; obtenidas: " + total);
        assertTrue(fnmt > 0,
                "debe incluir certificados de la FNMT; encontrados: " + fnmt);
    }

    @Test
    void trustedSourceIsNeverNull() {
        // Sin red ni caché, la fuente de confianza debe existir (vacía) en lugar
        // de fallar, para que la validación siga funcionando.
        assertNotNull(TrustService.trustedSource());
    }
}
