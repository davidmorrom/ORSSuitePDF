package com.orsconsulting.orssuitepdf.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecurityServiceTest {

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
    void protectedPdfRequiresPassword() throws IOException {
        Path pdf = samplePdf();
        try (PdfDocument doc = PdfDocument.open(pdf)) {
            SecurityService.protect(doc, "secreto");
            doc.pdbox().save(pdf.toFile());
        }
        assertThrows(InvalidPasswordException.class, () -> Loader.loadPDF(pdf.toFile()));
        try (PdfDocument doc = PdfDocument.open(pdf, "secreto")) {
            assertEquals(1, doc.pageCount());
        }
    }

    @Test
    void removeProtectionYieldsOpenablePdf() throws IOException {
        Path pdf = samplePdf();
        try (PdfDocument doc = PdfDocument.open(pdf)) {
            SecurityService.protect(doc, "clave");
            doc.pdbox().save(pdf.toFile());
        }
        try (PdfDocument doc = PdfDocument.open(pdf, "clave")) {
            SecurityService.removeProtection(doc);
            doc.pdbox().save(pdf.toFile());
        }
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }
}
