package com.orsconsulting.orssuitepdf.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Búsqueda de texto en el documento. Devuelve las coincidencias con su página
 * y el recuadro que ocupan (en puntos PDF, origen superior izquierdo, como los
 * usa el visor) para poder resaltarlas.
 */
public final class SearchService {

    private SearchService() {
    }

    /** Una coincidencia: página (base 0) y recuadro {@code [x, y, w, h]} en puntos. */
    public record Match(int page, double x, double y, double width, double height) {
    }

    public static List<Match> find(PdfDocument document, String query) throws IOException {
        List<Match> matches = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return matches;
        }
        String needle = query.toLowerCase();
        for (int page = 0; page < document.pageCount(); page++) {
            Collector collector = new Collector();
            collector.setStartPage(page + 1);
            collector.setEndPage(page + 1);
            collector.getText(document.pdbox());

            String haystack = collector.text.toString().toLowerCase();
            int from = 0;
            int index;
            while ((index = haystack.indexOf(needle, from)) >= 0) {
                Match box = boundingBox(page, collector.positions, index, needle.length());
                if (box != null) {
                    matches.add(box);
                }
                from = index + needle.length();
            }
        }
        return matches;
    }

    private static Match boundingBox(int page, List<TextPosition> positions, int start, int length) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        boolean any = false;
        for (int i = start; i < start + length && i < positions.size(); i++) {
            TextPosition tp = positions.get(i);
            if (tp == null) {
                continue;
            }
            double x = tp.getXDirAdj();
            double top = tp.getYDirAdj() - tp.getHeightDir();
            minX = Math.min(minX, x);
            minY = Math.min(minY, top);
            maxX = Math.max(maxX, x + tp.getWidthDirAdj());
            maxY = Math.max(maxY, top + tp.getHeightDir());
            any = true;
        }
        if (!any) {
            return null;
        }
        return new Match(page, minX, minY, maxX - minX, maxY - minY);
    }

    /** Acumula el texto y la posición de cada glifo, alineados por índice. */
    private static final class Collector extends PDFTextStripper {

        private final StringBuilder text = new StringBuilder();
        private final List<TextPosition> positions = new ArrayList<>();

        private Collector() throws IOException {
            super();
        }

        @Override
        protected void writeString(String string, List<TextPosition> textPositions) {
            for (TextPosition tp : textPositions) {
                String unicode = tp.getUnicode();
                if (unicode == null || unicode.isEmpty()) {
                    text.append(' ');
                    positions.add(tp);
                } else {
                    text.append(unicode);
                    for (int i = 0; i < unicode.length(); i++) {
                        positions.add(tp);
                    }
                }
            }
        }

        @Override
        protected void writeLineSeparator() {
            text.append('\n');
            positions.add(null);
        }

        @Override
        protected void writeWordSeparator() {
            text.append(' ');
            positions.add(null);
        }
    }
}
