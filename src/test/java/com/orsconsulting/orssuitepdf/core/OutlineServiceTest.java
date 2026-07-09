package com.orsconsulting.orssuitepdf.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutlineServiceTest {

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
    void readReturnsEmptyWhenNoOutline() throws IOException {
        try (PDDocument doc = Loader.loadPDF(samplePdf(3).toFile())) {
            assertTrue(OutlineService.read(doc).isEmpty());
        }
    }

    @Test
    void writeThenReadRoundTripsBookmarks() throws IOException {
        Path file = samplePdf(4);
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            Bookmark intro = new Bookmark("Introducción", 0);
            Bookmark chapter = new Bookmark("Capítulo 1", 1);
            chapter.getChildren().add(new Bookmark("Sección 1.1", 2));
            OutlineService.write(doc, List.of(intro, chapter));
            doc.save(file.toFile());
        }

        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            List<Bookmark> roots = OutlineService.read(doc);
            assertEquals(2, roots.size());
            assertEquals("Introducción", roots.get(0).getTitle());
            assertEquals(0, roots.get(0).getPageIndex());
            assertEquals("Capítulo 1", roots.get(1).getTitle());
            assertEquals(1, roots.get(1).getChildren().size());
            assertEquals("Sección 1.1", roots.get(1).getChildren().get(0).getTitle());
            assertEquals(2, roots.get(1).getChildren().get(0).getPageIndex());
        }
    }
}
