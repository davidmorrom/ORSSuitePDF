package com.orsconsulting.orssuitepdf.ocr;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.orsconsulting.orssuitepdf.core.PdfDocument;

class OcrServiceTest {

    @TempDir
    Path tempDir;

    private Path textPdf(String text) throws IOException {
        Path out = tempDir.resolve("text.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 48);
                cs.newLineAtOffset(60, 650);
                cs.showText(text);
                cs.endText();
            }
            doc.save(out.toFile());
        }
        return out;
    }

    @Test
    void ocrReadsTextFromRenderedPage() throws IOException {
        Path dataPath = OcrService.defaultDataPath();
        assumeTrue(OcrService.isDataAvailable(dataPath),
                "tessdata no disponible en " + dataPath + "; se omite la prueba de OCR");

        try (PdfDocument doc = PdfDocument.open(textPdf("HELLO OCR"))) {
            String text = OcrService.ocrPage(doc, 0, "eng", dataPath);
            assertTrue(text.toUpperCase().contains("HELLO"),
                    "El OCR debería reconocer 'HELLO'; obtenido: " + text);
        }
    }
}
