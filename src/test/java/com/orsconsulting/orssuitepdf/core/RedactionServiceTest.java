package com.orsconsulting.orssuitepdf.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RedactionServiceTest {

    @TempDir
    Path tempDir;

    private Path secretPdf() throws IOException {
        Path out = tempDir.resolve("secret.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 24);
                cs.newLineAtOffset(72, 700);
                cs.showText("SECRETO CONFIDENCIAL");
                cs.endText();
            }
            doc.save(out.toFile());
        }
        return out;
    }

    @Test
    void redactionRemovesUnderlyingText() throws IOException {
        Path pdf = secretPdf();
        try (PdfDocument doc = PdfDocument.open(pdf)) {
            // Rectángulo que cubre toda la página A4 (595 x 842 pt).
            RedactionService.redact(doc, 0, List.of(new double[]{0, 0, 595, 842}));
            doc.pdbox().save(pdf.toFile());
        }
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertEquals(1, doc.getNumberOfPages());
            String text = new PDFTextStripper().getText(doc);
            assertFalse(text.contains("SECRETO"),
                    "el texto redactado no debe poder extraerse; encontrado: " + text.strip());
        }
    }
}
