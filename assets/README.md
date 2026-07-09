# ORS Suite PDF — Iconos y logo

Simbolo: pagina con contenido de pixeles ORS y esquina que se disuelve (opcion 1c).
Paleta: #1A5EA8 (primario), #1C3C72 (profundo), #4A90D9 (medio), #C8DEF5 (claro).

## Contenido
- svg/symbol.svg — simbolo azul, fondo transparente (uso general, favicon SVG)
- svg/symbol-white.svg — simbolo blanco, para fondos azules/oscuros
- svg/tile.svg — tile de app (cuadrado azul redondeado + simbolo blanco)
- png/icon-{16..512}.png — simbolo transparente en 7 tamanos
- png/tile-{16..512}.png — tile azul en 7 tamanos
- png/apple-touch-icon-180.png — icono opaco 180px para touch/web
- app.ico — icono Windows multi-tamano (16-256), fondo transparente. Usar con jpackage: --icon app.ico
- app-tile.ico — variante tile azul multi-tamano
- favicon.ico — favicon web (16/32/48)
- logo-horizontal.png / logo-horizontal-white.png — lockup "ORS Suite PDF"
- splash.png — pantalla splash / banner instalador (1200x600)

## Uso web
<link rel="icon" href="/favicon.ico" sizes="any">
<link rel="icon" type="image/svg+xml" href="/symbol.svg">
<link rel="apple-touch-icon" href="/apple-touch-icon-180.png">

## Nota tipografica
El lockup usa Gill Sans (fallback Hind). Los PNG ya van rasterizados; si se
regenera el lockup, cargar la fuente con licencia.