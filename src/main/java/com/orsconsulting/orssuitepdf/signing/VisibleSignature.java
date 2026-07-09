package com.orsconsulting.orssuitepdf.signing;

/**
 * Apariencia visible de la firma dentro de una página. Las coordenadas siguen
 * la convención de DSS: origen en la esquina superior izquierda de la página,
 * en puntos PDF.
 *
 * @param page   índice de página en base 0
 * @param x      distancia desde el borde izquierdo (puntos)
 * @param y      distancia desde el borde superior (puntos)
 * @param width  anchura del recuadro (puntos)
 * @param height altura del recuadro (puntos)
 * @param text   texto a mostrar (si es nulo, se genera a partir del certificado)
 */
public record VisibleSignature(int page, float x, float y, float width, float height, String text) {
}
