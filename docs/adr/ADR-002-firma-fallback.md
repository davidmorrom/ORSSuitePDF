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

### TSA con lista de reserva
La TSA no es un único punto de fallo: `PAdESSigner` prueba la TSA
configurada y, si no responde, varias TSA de reserva (DigiCert, Sectigo…)
antes de degradar a PAdES-B. El valor de firma se calcula una sola vez y
se reutiliza entre TSA candidatas, de modo que con DNIe/tarjeta el PIN se
pide una única vez aunque haya que probar más de una.

### Confianza en la validación (LOTL) — segunda excepción online
Para que la validación de firmas cualificadas (FNMT, DNIe, ACCV…) dé un
veredicto de confianza real y no `INDETERMINATE`, se usan las listas de
confianza europeas (EU LOTL) vía `dss-tsl-validation` (`TrustService`):
- **Offline-first:** al arrancar se carga la confianza desde una caché en
  disco (`~/.ors-suite-pdf/tl-cache`) de forma instantánea, y la
  actualización online se hace en segundo plano; si nunca ha habido red y
  no hay caché, la validación sigue funcionando pero sin cadena de
  confianza.
- La revocación en la validación (OCSP/CRL) y el descubrimiento de
  intermedios (AIA) son online, con degradación a validación local si no
  hay red — coherente con el principio offline-first.
- Los certificados públicos del Diario Oficial que firman el LOTL se
  versionan como PEM (`/trust/eu-oj-lotl-signers.pem`); no es clave
  privada. El keystore .p12 se evita a propósito por la regla de
  `.gitignore` que veta material `*.p12`.

### Nota de compatibilidad: XSD de XAdES
DSS 5.11 (y hasta 5.13) compone el esquema de validación de las listas de
confianza con la versión pre-2016 de `XAdES.xsd`, que no define
`SigningCertificateV2`. El LOTL europeo actual está firmado en XAdES-EN y
usa ese elemento, por lo que el parsing falla
(`cvc-complex-type.2.4.a`). Se corrige incluyendo en el classpath, con
prioridad sobre el de DSS, la versión de 2016 del esquema v1.3.2
(`/xsd/XAdES.xsd`, superconjunto compatible); en el fat jar, el
`maven-shade-plugin` excluye el XSD obsoleto de `specs-xades`.

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
