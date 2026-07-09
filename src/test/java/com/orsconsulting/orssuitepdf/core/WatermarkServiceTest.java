package com.orsconsulting.orssuitepdf.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WatermarkServiceTest {

    @TempDir
    Path tempDir;

    private Path samplePdf(int pages) throws IOException {
        Path out = tempDir.resolve("doc.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage(PDRectangle.A4));
            }
            doc.save(out.toFile());
        }
        return out;
    }

    @Test
    void watermarkAndNumberingAreApplied() throws IOException {
        Path pdf = samplePdf(2);
        try (PdfDocument doc = PdfDocument.open(pdf)) {
            WatermarkService.watermark(doc, "CONFIDENCIAL");
            WatermarkService.numberPages(doc);
            doc.pdbox().save(pdf.toFile());
        }
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            String text = new PDFTextStripper().getText(doc);
            // El texto rotado de la marca de agua puede extraerse con espacios
            // entre glifos, por eso se normaliza antes de comprobar.
            String noSpaces = text.replaceAll("\\s", "");
            assertTrue(noSpaces.contains("CONFIDENCIAL"), "la marca de agua debe estar presente");
            assertTrue(noSpaces.contains("1/2"), "la numeración debe estar presente");
        }
    }
}
