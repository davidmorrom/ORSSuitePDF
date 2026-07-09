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
    void freehandAndArrowDoNotFail() throws IOException {
        Path pdf = samplePdf();
        try (PdfDocument doc = PdfDocument.open(pdf)) {
            AnnotationService.freehand(doc, 0,
                    java.util.List.of(new double[]{50, 50}, new double[]{80, 90}, new double[]{120, 60}),
                    new float[]{0.85f, 0.15f, 0.15f}, 2f);
            AnnotationService.arrow(doc, 0, 200, 200, 300, 260,
                    new float[]{0.15f, 0.3f, 0.85f}, 2f);
            doc.pdbox().save(pdf.toFile());
        }
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertTrue(doc.getNumberOfPages() == 1);
        }
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
