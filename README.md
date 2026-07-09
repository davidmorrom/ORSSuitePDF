# ORS Suite PDF

## Copyright
© 2026 David — ORS Consulting. Todos los derechos reservados.

Este repositorio se publica con fines de portfolio/demostración. No se
concede ninguna licencia de uso, copia, modificación o distribución sobre
este código. Ver un repositorio público en GitHub no implica autorización
para reutilizarlo fuera de la propia plataforma. Si quieres usar parte de
este código, contacta antes con el autor.

---

Editor de PDF profesional para escritorio, offline-first, gratuito.

Unir, dividir, rotar, marcadores, formularios, anotaciones, firma digital
con validez legal (PAdES), edición de texto existente y OCR.

## Coste: 100% gratuito
Todas las librerías del stack (Java, JavaFX, AtlantaFX, Apache PDFBox,
DSS, Tess4J, Maven, jpackage) son open-source y gratuitas, sin coste de
licencia. La única excepción, totalmente opcional, es un certificado de
firma de código (~€100-300/año) para evitar el aviso de Windows
SmartScreen al distribuir el instalador — no es necesario para
desarrollar ni usar la app.

## Requisitos
- JDK 21+
- Maven 3.9+ (opcional: el proyecto incluye Maven Wrapper, `./mvnw`)
- Para compilar el instalador en Windows: WiX Toolset

## Desarrollo
```
./mvnw clean javafx:run     # o mvn clean javafx:run si Maven está instalado
./mvnw test                 # ejecuta las pruebas
```

## Compilar a .exe (u otros instaladores)
```
mvn clean package
jpackage --input target/ ^
  --main-jar ors-suite-pdf-0.1.0.jar ^
  --main-class com.orsconsulting.orssuitepdf.core.Main ^
  --name "ORS Suite PDF" ^
  --type exe ^
  --icon build/icon.ico
```
(En macOS/Linux, sustituir `--type exe` por `dmg`/`deb` y adaptar el icono.)

Nota: sin certificado de firma de código, Windows SmartScreen puede
mostrar un aviso al ejecutar el instalador por primera vez — no afecta a
la instalación ni al uso de la app una vez instalada.

## Estructura
Ver las decisiones de arquitectura documentadas en `docs/adr/`.

## Roadmap
1. ✅ MVP: visor PDF (navegación + zoom), unir/extraer, rotar/mover/eliminar
   páginas y editor de marcadores (PDFBox + JavaFX)
2. ⬜ Formularios (AcroForms) + firma visual
3. ⬜ OCR + edición de texto básica (Tess4J + PDFBox)
4. ⬜ Firma PAdES con fallback offline (DSS)
5. ⬜ Empaquetado a instalador (.exe/.dmg/.deb) con jpackage

Las fases 2–4 reincorporarán las dependencias de firma (DSS) y OCR
(Tess4J) cuando se aborden; el MVP solo depende de JavaFX, AtlantaFX y
PDFBox.
