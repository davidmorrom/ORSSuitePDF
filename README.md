# ORS Suite PDF

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
- Maven 3.9+
- Para compilar el instalador en Windows: WiX Toolset

## Desarrollo
```
mvn clean javafx:run
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

## Roadmap sugerido
1. MVP: visor PDF + unir/dividir + marcadores (PDFBox + JavaFX)
2. Formularios (AcroForms) + firma visual
3. OCR + edición de texto básica (Tess4J + PDFBox)
4. Firma PAdES con fallback offline (DSS)
5. Empaquetado a instalador (.exe/.dmg/.deb) con jpackage
