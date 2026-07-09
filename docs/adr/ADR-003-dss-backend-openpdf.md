# ADR-003: Backend OpenPDF para la firma PAdES con DSS

## Estado
Aceptado

## Contexto
El resto de la aplicación (visor, unir/dividir, rotar, marcadores,
AcroForms, sello visual, OCR) se construye sobre **Apache PDFBox 3.0.x**
(ver ADR-001). Para la firma PAdES se eligió **DSS** (Comisión Europea).

Al integrar DSS surgió un conflicto de dependencias: el módulo de DSS que
implementa PAdES sobre PDFBox (`dss-pades-pdfbox`, versiones 5.x y 6.1)
está compilado contra **PDFBox 2.x** y llama a API eliminada en PDFBox 3
(`PDDocument.load(InputStream, String)`). Como PDFBox 2 y 3 comparten el
paquete `org.apache.pdfbox`, no pueden coexistir en el classpath: usar el
backend PDFBox de DSS obligaría a degradar toda la app a PDFBox 2 (una
regresión grande) o provocaría `NoSuchMethodError` en tiempo de ejecución.

En el repositorio de DSS no hay, a la fecha, una versión de
`dss-pades-pdfbox` sobre PDFBox 3, y DSS no se publica en Maven Central.

## Decisión
Usar el backend **OpenPDF** de DSS (`dss-pades-openpdf`, versión 5.11) en
lugar del backend PDFBox:

- OpenPDF (`com.github.librepdf:openpdf`, LGPL/MPL) es independiente de
  PDFBox, por lo que DSS firma sin introducir PDFBox 2.x en el classpath.
- El resto de operaciones de PDF siguen sobre **PDFBox 3.0.2** sin cambios.
- El backend de PDF es un detalle interno de DSS: la firma resultante es
  PAdES estándar con independencia de qué biblioteca la genere.
- Se fija `dss.version = 5.11` para todos los módulos DSS
  (`dss-pades-openpdf`, `dss-service`, `dss-token`,
  `dss-utils-apache-commons`, `dss-crl-parser-x509crl`).

La estrategia de fallback online/offline sigue siendo la del ADR-002
(intento PAdES-B-T con timeout corto de TSA → degradación a PAdES-B).

## Alternativas descartadas
- **`dss-pades-pdfbox` (backend PDFBox de DSS):** exige PDFBox 2.x,
  incompatible con el PDFBox 3 del resto de la app.
- **Degradar toda la app a PDFBox 2.x:** regresión amplia y renuncia a
  las mejoras de PDFBox 3.
- **Aislar DSS + PDFBox 2 en un classloader propio:** complejidad
  desproporcionada frente a cambiar de backend.

## Consecuencias
- Sin coste de licencia ni fricción (OpenPDF es open-source permisivo).
- Dos bibliotecas de PDF en el proyecto con roles separados y sin
  conflicto: PDFBox 3 para edición/visualización, OpenPDF (dentro de DSS)
  solo para la firma criptográfica.
- Al actualizar DSS en el futuro, revisar si ya ofrece un backend sobre
  PDFBox 3; de ser así, podría unificarse el backend y retirarse OpenPDF.
- Validar el PDF firmado contra el validador oficial eIDAS/DSS antes de
  darlo por bueno en producción (igual que en ADR-002).
