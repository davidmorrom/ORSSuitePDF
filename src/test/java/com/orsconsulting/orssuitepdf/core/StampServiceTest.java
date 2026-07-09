package com.orsconsulting.orssuitepdf.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StampServiceTest {

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

    private Path sampleImage() throws IOException {
        Path out = tempDir.resolve("stamp.png");
        BufferedImage image = new BufferedImage(120, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 120, 60);
        g.dispose();
        ImageIO.write(image, "png", out.toFile());
        return out;
    }

    private boolean hasXObject(PDDocument doc) {
        PDResources resources = doc.getPage(0).getResources();
        if (resources == null) {
            return false;
        }
        for (COSName name : resources.getXObjectNames()) {
            if (name != null) {
                return true;
            }
        }
        return false;
    }

    @Test
    void stampAddsImageXObjectToPage() throws IOException {
        Path pdf = samplePdf();
        Path image = sampleImage();
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertFalse(hasXObject(doc), "la página no debería tener XObjects al inicio");
            StampService.stampImage(doc, 0, image, 100, 100, 150, 75);
            doc.save(pdf.toFile());
        }
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertTrue(hasXObject(doc), "la página debería contener la imagen tras el sellado");
        }
    }

    @Test
    void rejectsInvalidPageAndSize() throws IOException {
        Path image = sampleImage();
        try (PDDocument doc = Loader.loadPDF(samplePdf().toFile())) {
            assertThrows(IndexOutOfBoundsException.class,
                    () -> StampService.stampImage(doc, 5, image, 0, 0, 100, 100));
            assertThrows(IllegalArgumentException.class,
                    () -> StampService.stampImage(doc, 0, image, 0, 0, 0, 100));
        }
    }
}
