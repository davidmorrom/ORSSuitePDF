package com.orsconsulting.orssuitepdf.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javafx.scene.image.Image;

class PdfDocumentTest {

    @TempDir
    Path tempDir;

    /** Genera un PDF de {@code pages} páginas con un texto por página. */
    private Path samplePdf(int pages) throws IOException {
        Path out = tempDir.resolve("sample.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 24);
                    cs.newLineAtOffset(72, 700);
                    cs.showText("Página " + (i + 1));
                    cs.endText();
                }
            }
            doc.save(out.toFile());
        }
        return out;
    }

    @Test
    void openReportsPageCount() throws IOException {
        try (PdfDocument doc = PdfDocument.open(samplePdf(3))) {
            assertEquals(3, doc.pageCount());
        }
    }

    @Test
    void renderPageProducesNonEmptyImage() throws IOException {
        try (PdfDocument doc = PdfDocument.open(samplePdf(1))) {
            Image image = doc.renderPage(0, 96f);
            assertTrue(image.getWidth() > 0, "la imagen renderizada debe tener anchura");
            assertTrue(image.getHeight() > 0, "la imagen renderizada debe tener altura");
        }
    }
}
