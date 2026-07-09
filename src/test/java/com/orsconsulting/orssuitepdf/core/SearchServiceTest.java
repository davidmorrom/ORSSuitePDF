package com.orsconsulting.orssuitepdf.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchServiceTest {

    @TempDir
    Path tempDir;

    private Path pdfWith(String... linesPerPage) throws IOException {
        Path out = tempDir.resolve("doc.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (String line : linesPerPage) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 20);
                    cs.newLineAtOffset(72, 700);
                    cs.showText(line);
                    cs.endText();
                }
            }
            doc.save(out.toFile());
        }
        return out;
    }

    @Test
    void findsMatchesWithBoxesOnCorrectPages() throws IOException {
        try (PdfDocument doc = PdfDocument.open(pdfWith("hola mundo", "otra cosa", "hola de nuevo"))) {
            List<SearchService.Match> matches = SearchService.find(doc, "hola");
            assertEquals(2, matches.size());
            assertEquals(0, matches.get(0).page());
            assertEquals(2, matches.get(1).page());
            assertTrue(matches.get(0).width() > 0 && matches.get(0).height() > 0,
                    "la coincidencia debe tener un recuadro");
        }
    }

    @Test
    void noMatchesForAbsentText() throws IOException {
        try (PdfDocument doc = PdfDocument.open(pdfWith("hola mundo"))) {
            assertTrue(SearchService.find(doc, "inexistente").isEmpty());
        }
    }
}
