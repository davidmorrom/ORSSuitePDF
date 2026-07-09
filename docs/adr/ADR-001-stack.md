# ADR-001: Stack tecnológico base

## Estado
Aceptado (revisado — pivote de Electron/Node a Java)

## Contexto
App de escritorio offline-first para edición profesional de PDF, gratuita
(sin licencias comerciales de SDK), con nivel de ambición cercano a Adobe
Acrobat Pro en el subconjunto de funciones más usadas (unir/dividir,
marcadores, formularios, firma, anotaciones), aceptando limitaciones
conocidas en edición de texto avanzada y OCR frente a motores comerciales.

Una primera iteración de esta decisión planteaba Electron (Node.js) con
dos sidecars externos (Python para texto/OCR, Java para firma PAdES).
Se descarta ese enfoque en favor de un stack Java unificado por experiencia
previa del equipo con Java y por la simplificación arquitectónica que
supone eliminar la comunicación entre procesos.

## Decisión
- **Java 21 + JavaFX** como plataforma única, un solo proceso, sin
  sidecars ni comunicación entre runtimes distintos.
- **AtlantaFX** como capa de estilos sobre JavaFX, para evitar el aspecto
  visual anticuado de JavaFX/Swing por defecto.
- **Apache PDFBox** (Apache 2.0) como motor único para todas las
  operaciones de PDF: estructurales (unir, dividir, rotar, marcadores,
  AcroForms, firma visual) y también edición de texto/redacción, evitando
  así la fricción de licencia AGPL que tenía la alternativa Python
  (PyMuPDF) planteada en la primera iteración.
- **DSS** (Comisión Europea, LGPL) para firma PAdES, en el mismo proceso
  Java — sin necesidad de sidecar.
- **Tess4J** (wrapper de Tesseract, Apache 2.0) para OCR.
- **Maven + jpackage** para compilar a `.exe` (Windows), `.dmg` (macOS) y
  `.deb`/AppImage (Linux) sin coste de licencia.

## Alternativas descartadas
- Electron + sidecars Python/Java: descartado por complejidad operativa
  (tres runtimes distintos) frente al beneficio marginal en UI.
- SDK comercial (Apryse/Foxit): calidad muy superior pero incompatible
  con el requisito de "gratis".

## Consecuencias
- JavaFX requiere más trabajo deliberado de estilos (vía AtlantaFX y CSS
  propio) para no verse anticuado, frente a lo "gratis" que es un
  aspecto moderno en HTML/CSS.
- Se resuelve de raíz la fricción de licencia AGPL que existía con
  PyMuPDF en la iteración anterior.
- Arquitectura de un solo proceso: más simple de depurar y mantener para
  un equipo pequeño.
- Techo de calidad conocido en edición de texto y OCR frente a Acrobat,
  inherente al formato PDF y no resuelto por el cambio de lenguaje.
