package com.woodlogix.desktop.service;

import com.woodlogix.application.sales.SalesProjectRow;
import com.woodlogix.core.enums.SalesStage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProposalPdfServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsEnglishMessagesForPipelineStages() {
        ResourceBundle messages = ResourceBundle.getBundle(
                "com.woodlogix.desktop.i18n.messages", Locale.US
        );

        assertTrue(messages.getString("sales.stage.contact").equals("Contact"));
        assertTrue(messages.getString("sales.stage.measurement_scheduled")
                .equals("Measurement Scheduled"));
        assertTrue(messages.getString("tab.pipeline").equals("Sales pipeline"));
    }

    @Test
    void loadsSpanishMessagesForPipelineStages() {
        ResourceBundle messages = ResourceBundle.getBundle(
                "com.woodlogix.desktop.i18n.messages", Locale.forLanguageTag("es-ES")
        );

        assertTrue(messages.getString("sales.stage.contact").equals("Contacto"));
        assertTrue(messages.getString("sales.stage.quote_sent").equals("Presupuesto enviado"));
        assertTrue(messages.getString("tab.pipeline").equals("Embudo de ventas"));
    }

    @Test
    void exportsProposalUsingRequestedLanguageAndCurrency() throws Exception {
        SalesPipelineService pipelineService = mock(SalesPipelineService.class);
        SalesProjectRow project = new SalesProjectRow(
                "project-1",
                "Custom kitchen",
                "Test Customer",
                "customer@example.test",
                "+1-555-0100",
                "Kitchen",
                new BigDecimal("12500.00"),
                SalesStage.QUOTE_SENT
        );
        when(pipelineService.findById("project-1")).thenReturn(project);
        ProposalPdfService pdfService = new ProposalPdfService(pipelineService, Locale.US);
        Path destination = temporaryDirectory.resolve("proposal-en.pdf");

        pdfService.export("project-1", destination);

        try (var document = Loader.loadPDF(destination.toFile())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("SALES PROPOSAL"));
            assertTrue(text.contains("CUSTOMER"));
            assertTrue(text.contains("Custom kitchen"));
            assertTrue(text.contains("Quote Sent"));
            assertTrue(text.contains("$12,500.00"));
        }
    }

    @Test
    void exportsProposalFromLoadedProjectWithoutConsultingPipeline() throws Exception {
        SalesPipelineService pipelineService = mock(SalesPipelineService.class);
        SalesProjectRow project = new SalesProjectRow(
                "project-remote",
                "Remote kitchen",
                "Remote Customer",
                "customer@example.test",
                "+1-555-0100",
                "Kitchen",
                new BigDecimal("12500.00"),
                SalesStage.QUOTE_SENT
        );
        ProposalPdfService pdfService = new ProposalPdfService(pipelineService, Locale.US);
        Path destination = temporaryDirectory.resolve("proposal-loaded.pdf");

        pdfService.export(project, destination);

        verifyNoInteractions(pipelineService);
        try (var document = Loader.loadPDF(destination.toFile())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Remote kitchen"));
            assertTrue(text.contains("Remote Customer"));
            assertTrue(text.contains("Quote Sent"));
            assertTrue(text.contains("$12,500.00"));
        }
    }

    @Test
    void exportsProposalInSpanishUsingEuroCurrency() throws Exception {
        SalesPipelineService pipelineService = mock(SalesPipelineService.class);
        when(pipelineService.findById("project-es")).thenReturn(new SalesProjectRow(
                "project-es",
                "Cocina a medida",
                "Cliente de prueba",
                "cliente@example.test",
                "+34-600-000-000",
                "Cocina",
                new BigDecimal("12500.00"),
                SalesStage.QUOTE_SENT
        ));
        ProposalPdfService pdfService = new ProposalPdfService(
                pipelineService, Locale.forLanguageTag("es-ES"));
        Path destination = temporaryDirectory.resolve("propuesta-es.pdf");

        pdfService.export("project-es", destination);

        try (var document = Loader.loadPDF(destination.toFile())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("PROPUESTA COMERCIAL"));
            assertTrue(text.contains("CLIENTE"));
            assertTrue(text.contains("Presupuesto enviado"));
            assertTrue(text.contains("12.500,00"));
            assertTrue(text.contains("€"));
        }
    }
}
