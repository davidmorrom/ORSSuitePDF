package com.orsconsulting.orssuitepdf.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnnotationServiceTest {

    @TempDir
    Path tempDir;

    private Path samplePdf() throws IOException {
        Path out = tempDir.resolve("doc.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            doc.save(out.toFile());
        }
        return out;
    }

    @Test
    void annotationsArePersisted() throws IOException {
        Path pdf = samplePdf();
        try (PdfDocument doc = PdfDocument.open(pdf)) {
            AnnotationService.highlight(doc, 0, 50, 50, 200, 30);
            AnnotationService.rectangle(doc, 0, 50, 120, 200, 60);
            AnnotationService.note(doc, 0, 300, 50, "Revisar esto");
            doc.pdbox().save(pdf.toFile());
        }
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertTrue(doc.getPage(0).getAnnotations().size() >= 3,
                    "deberían persistir las tres anotaciones");
        }
    }
}
