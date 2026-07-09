package com.orsconsulting.orssuitepdf.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDComboBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.cos.COSName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcroFormServiceTest {

    @TempDir
    Path tempDir;

    /** PDF con un campo de texto ("nombre") y un desplegable ("pais"). */
    private Path formPdf() throws IOException {
        Path out = tempDir.resolve("form.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDAcroForm acro = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(acro);
            acro.setNeedAppearances(true);
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("Helv"),
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            acro.setDefaultResources(resources);
            acro.setDefaultAppearance("/Helv 0 Tf 0 g");

            PDTextField text = new PDTextField(acro);
            text.setPartialName("nombre");
            addWidget(text, page, 50, 700);
            acro.getFields().add(text);
            text.setValue("inicial");

            PDComboBox combo = new PDComboBox(acro);
            combo.setPartialName("pais");
            combo.setOptions(List.of("España", "Francia", "Italia"));
            addWidget(combo, page, 50, 650);
            acro.getFields().add(combo);
            combo.setValue("España");

            doc.save(out.toFile());
        }
        return out;
    }

    private void addWidget(org.apache.pdfbox.pdmodel.interactive.form.PDField field,
                           PDPage page, float x, float y) throws IOException {
        PDAnnotationWidget widget = field.getWidgets().get(0);
        widget.setRectangle(new PDRectangle(x, y, 200, 20));
        widget.setPage(page);
        page.getAnnotations().add(widget);
    }

    @Test
    void readReturnsFieldsWithTypesAndValues() throws IOException {
        try (PDDocument doc = Loader.loadPDF(formPdf().toFile())) {
            assertTrue(AcroFormService.hasForm(doc));
            List<FormField> fields = AcroFormService.read(doc);
            assertEquals(2, fields.size());

            FormField name = find(fields, "nombre");
            assertEquals(FormField.Type.TEXT, name.getType());
            assertEquals("inicial", name.getValue());

            FormField country = find(fields, "pais");
            assertEquals(FormField.Type.CHOICE, country.getType());
            assertTrue(country.getOptions().contains("Francia"));
        }
    }

    @Test
    void applyUpdatesFieldValues() throws IOException {
        Path file = formPdf();
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            AcroFormService.apply(doc, Map.of("nombre", "Ada Lovelace", "pais", "Italia"));
            doc.save(file.toFile());
        }
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            List<FormField> fields = AcroFormService.read(doc);
            assertEquals("Ada Lovelace", find(fields, "nombre").getValue());
            assertEquals("Italia", find(fields, "pais").getValue());
        }
    }

    @Test
    void flattenRemovesEditableFields() throws IOException {
        try (PDDocument doc = Loader.loadPDF(formPdf().toFile())) {
            AcroFormService.flatten(doc);
            assertFalse(AcroFormService.hasForm(doc));
        }
    }

    private FormField find(List<FormField> fields, String name) {
        Optional<FormField> match = fields.stream()
                .filter(f -> f.getName().equals(name)).findFirst();
        assertTrue(match.isPresent(), "campo no encontrado: " + name);
        return match.get();
    }
}
