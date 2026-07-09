# ADR-002: Firma digital PAdES con fallback offline

## Estado
Aceptado

## Contexto
La app es offline-first, pero la firma digital con validez legal robusta
(PAdES-B-T) requiere sello de tiempo (TSA, RFC 3161) y comprobación de
revocación (OCSP), ambos servicios online. No se quiere que la ausencia
de red bloquee la firma ni el resto de la app. Con el stack Java (DSS en
el mismo proceso, ver ADR-001) esto se implementa directamente como
lógica de la aplicación, sin necesidad de comunicación entre procesos.

## Decisión
- El paquete `signing` es el único punto de la app que hace llamadas de
  red.
- Antes de firmar: intento de conexión al TSA con timeout corto (3-5s).
- **Con conexión:** firma PAdES-B-T completa (certificado local del
  usuario + timestamp + OCSP). Nivel de validez legal máximo.
- **Sin conexión:** firma PAdES-B (sin timestamp), sigue siendo firma
  electrónica avanzada válida. Se informa claramente al usuario del nivel
  obtenido (indicador en UI: con/sin sello de tiempo).
- Se guarda localmente qué documentos quedaron firmados sin timestamp,
  para permitir "re-timbrar" (añadir un DocTimeStamp posterior) cuando
  haya conexión, sin repetir el proceso de firma completo.
- Ningún otro paquete (core, ui, ocr) depende de red.

## Consecuencias
- Necesita un pequeño almacén local (ej. SQLite embebido o fichero JSON)
  para la cola de documentos pendientes de re-timbrado.
- La UI necesita un estado visual claro de nivel de firma obtenido.
- Toda excepción de red dentro de `signing` debe capturarse ahí mismo y
  resolverse con el fallback, nunca propagarse como error no controlado
  al resto de la aplicación.
- Antes de dar por buena la firma en producción, validar el PDF firmado
  contra el validador oficial de la Comisión Europea (DSS demo webapp /
  validador eIDAS) para confirmar conformidad PAdES real.
