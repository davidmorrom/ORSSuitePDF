# ADR-004: Conversión a formatos ofimáticos vía LibreOffice

## Estado
Aceptado

## Contexto
Se pide exportar el PDF a formatos de Office (DOCX, PPTX, ODT…). La
conversión PDF → Office de alta fidelidad no es viable con librerías Java
libres y puras: reconstruir la maquetación (columnas, tablas, estilos)
desde un PDF es un problema abierto y las soluciones puramente Java dan
resultados pobres.

## Decisión
Un enfoque en dos niveles:

- **Nativo, siempre disponible y offline** (PDFBox): exportación a **texto
  plano** (`.txt`) y a **imágenes** (`.png` por página).
- **Ofimático mediante LibreOffice** (opcional): si el sistema tiene
  **LibreOffice** instalado, se usa en modo headless
  (`soffice --headless --convert-to <fmt>`) para generar DOCX/PPTX/ODT/RTF.
  Se ejecuta con un perfil temporal (`-env:UserInstallation`) para no chocar
  con una instancia abierta. Si no está instalado, esas opciones aparecen
  deshabilitadas con un aviso.

## Alternativas descartadas
- **Conversión DOCX/PPTX pura en Java** (p. ej. envolver texto/imágenes con
  Apache POI): fidelidad de maquetación insuficiente para un uso profesional.
- **Servicios en la nube**: incompatibles con el requisito offline-first y con
  la confidencialidad de los documentos.

## Consecuencias
- La conversión ofimática introduce una **dependencia externa opcional**
  (LibreOffice), invocada como proceso aparte solo bajo demanda. Es una
  excepción acotada al principio de "un solo proceso" del ADR-001, justificada
  porque no existe alternativa libre equivalente y no afecta al resto de la
  app.
- LibreOffice es software libre; no añade coste ni fricción de licencia.
- La exportación a texto e imágenes permanece 100% integrada y offline.
