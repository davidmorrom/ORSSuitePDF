/**
 * Firma digital PAdES usando DSS (Comisión Europea).
 * Implementa el fallback online/offline descrito en docs/adr/ADR-003:
 * PAdES-B-T (con timestamp) si hay conexión al TSA, PAdES-B (sin
 * timestamp) si no la hay, sin bloquear el resto de la aplicación.
 * Toda llamada de red de este paquete debe llevar timeout corto (3-5s)
 * y manejo explícito del fallo.
 */
package com.orsconsulting.orssuitepdf.signing;
