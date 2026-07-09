package com.orsconsulting.orssuitepdf.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportServiceTest {

    @TempDir
    Path tempDir;

    private Path samplePdf(int pages, String text) throws IOException {
        Path out = tempDir.resolve("doc.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 18);
                    cs.newLineAtOffset(72, 700);
                    cs.showText(text + " " + (i + 1));
                    cs.endText();
                }
            }
            doc.save(out.toFile());
        }
        return out;
    }

    @Test
    void exportTextWritesExtractedContent() throws IOException {
        try (PdfDocument doc = PdfDocument.open(samplePdf(1, "Hola ORS"))) {
            Path txt = tempDir.resolve("out.txt");
            ExportService.exportText(doc, txt);
            String content = Files.readString(txt);
            assertTrue(content.contains("Hola ORS"), "el texto exportado debe contener el contenido");
        }
    }

    @Test
    void exportImagesWritesOnePngPerPage() throws IOException {
        try (PdfDocument doc = PdfDocument.open(samplePdf(3, "Pag"))) {
            Path dir = tempDir.resolve("imgs");
            int count = ExportService.exportImages(doc, dir, "png", 96f, "pagina");
            assertEquals(3, count);
            assertTrue(Files.exists(dir.resolve("pagina-1.png")));
            assertTrue(Files.exists(dir.resolve("pagina-3.png")));
        }
    }
}
