package com.orsconsulting.orssuitepdf.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfOperationsTest {

    @TempDir
    Path tempDir;

    /** PDF con {@code pages} páginas etiquetadas y tamaño A4. */
    private Path samplePdf(String name, int pages) throws IOException {
        Path out = tempDir.resolve(name);
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 24);
                    cs.newLineAtOffset(72, 700);
                    cs.showText(name + " p" + (i + 1));
                    cs.endText();
                }
            }
            doc.save(out.toFile());
        }
        return out;
    }

    @Test
    void mergeConcatenatesPageCounts() throws IOException {
        Path a = samplePdf("a.pdf", 2);
        Path b = samplePdf("b.pdf", 3);
        Path merged = tempDir.resolve("merged.pdf");

        PdfOperations.merge(List.of(a, b), merged);

        try (PDDocument doc = Loader.loadPDF(merged.toFile())) {
            assertEquals(5, doc.getNumberOfPages());
        }
    }

    @Test
    void extractRangeCopiesSelectedPages() throws IOException {
        try (PDDocument source = Loader.loadPDF(samplePdf("src.pdf", 5).toFile())) {
            Path out = tempDir.resolve("range.pdf");
            PdfOperations.extractRange(source, 1, 3, out); // páginas 2–4

            assertEquals(5, source.getNumberOfPages(), "el origen no debe mutar");
            try (PDDocument extracted = Loader.loadPDF(out.toFile())) {
                assertEquals(3, extracted.getNumberOfPages());
            }
        }
    }

    @Test
    void rotatePageAccumulatesRotation() throws IOException {
        try (PDDocument doc = Loader.loadPDF(samplePdf("rot.pdf", 1).toFile())) {
            PdfOperations.rotatePage(doc, 0, 90);
            PdfOperations.rotatePage(doc, 0, 90);
            assertEquals(180, doc.getPage(0).getRotation());
        }
    }

    @Test
    void deletePageReducesCountButKeepsAtLeastOne() throws IOException {
        try (PDDocument doc = Loader.loadPDF(samplePdf("del.pdf", 2).toFile())) {
            PdfOperations.deletePage(doc, 0);
            assertEquals(1, doc.getNumberOfPages());
            assertThrows(IllegalStateException.class, () -> PdfOperations.deletePage(doc, 0));
        }
    }

    @Test
    void movePageReordersPages() throws IOException {
        try (PDDocument doc = Loader.loadPDF(samplePdf("mov.pdf", 3).toFile())) {
            PDPage first = doc.getPage(0);
            PdfOperations.movePage(doc, 0, 2); // primera pasa al final
            assertEquals(3, doc.getNumberOfPages());
            assertEquals(first, doc.getPage(2));
        }
    }
}
