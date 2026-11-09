package com.woodlogix.desktop.service;

import com.woodlogix.application.sales.SalesProjectRow;
import com.woodlogix.core.enums.RoomType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import com.woodlogix.desktop.config.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

@Service
public class ProposalPdfService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProposalPdfService.class);
    private static final float LEFT_MARGIN = 56;
    private static final float TOP_POSITION = 750;
    private static final float LINE_SPACING = 24;

    private final SalesPipelineService salesPipelineService;
    private final Locale locale;
    private final ResourceBundle messages;

    @Autowired
    public ProposalPdfService(SalesPipelineService salesPipelineService) {
        this(salesPipelineService, Locale.forLanguageTag(ConfigLoader.load()
                .getProperty("woodlogix.ui.locale", "pt-BR")));
    }

    ProposalPdfService(SalesPipelineService salesPipelineService, Locale locale) {
        this.salesPipelineService = Objects.requireNonNull(
                salesPipelineService, "salesPipelineService must not be null"
        );
        this.locale = Objects.requireNonNull(locale, "locale must not be null");
        if (locale.getLanguage().isBlank()) {
            throw new IllegalArgumentException("woodlogix.ui.locale must be a valid language tag");
        }
        this.messages = ResourceBundle.getBundle(
                "com.woodlogix.desktop.i18n.messages",
                locale,
                ResourceBundle.Control.getNoFallbackControl(
                        ResourceBundle.Control.FORMAT_DEFAULT)
        );
    }

    public void export(String projectId, java.nio.file.Path destination) throws IOException {
        Objects.requireNonNull(destination, "destination must not be null");
        SalesProjectRow project = salesPipelineService.findById(projectId);
        java.nio.file.Path absolutePath = destination.toAbsolutePath().normalize();
        java.nio.file.Path parent = absolutePath.getParent();
        if (parent != null) {
            java.nio.file.Files.createDirectories(parent);
        }

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = TOP_POSITION;
                y = writeLine(content, text("proposal.title"), LEFT_MARGIN, y, 20, true);
                String date = LocalDate.now().format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale));
                y = writeLine(content, "WoodLogix | " + date, LEFT_MARGIN, y, 11, false);
                y -= LINE_SPACING;
                y = writeLine(content, text("proposal.customer-section"), LEFT_MARGIN, y, 12, true);
                y = writeLine(content, project.customerName(), LEFT_MARGIN, y, 11, false);
                if (hasText(project.customerPhone())) {
                    y = writeLine(content, format("proposal.phone", project.customerPhone()),
                            LEFT_MARGIN, y, 11, false);
                }
                if (hasText(project.customerEmail())) {
                    y = writeLine(content, format("proposal.email", project.customerEmail()),
                            LEFT_MARGIN, y, 11, false);
                }
                y -= LINE_SPACING;
                y = writeLine(content, text("proposal.project-section"), LEFT_MARGIN, y, 12, true);
                y = writeLine(content, project.projectName(), LEFT_MARGIN, y, 11, false);
                if (hasText(project.environmentName())) {
                    y = writeLine(content, format("proposal.environment",
                                    localizedRoomName(project.environmentName())),
                            LEFT_MARGIN, y, 11, false);
                }
                y = writeLine(content, format("proposal.stage",
                                text(project.stage().getMessageKey())),
                        LEFT_MARGIN, y, 11, false);
                y -= LINE_SPACING;
                y = writeLine(content, text("proposal.estimated-value"), LEFT_MARGIN, y, 12, true);
                writeLine(content, formatCurrency(project.totalValue()), LEFT_MARGIN, y, 16, true);
            }
            document.save(absolutePath.toFile());
        }
        LOGGER.info("Sales proposal PDF exported");
    }

    private static float writeLine(
            PDPageContentStream content, String text, float x, float y, int fontSize, boolean bold
    ) throws IOException {
        content.beginText();
        content.setFont(new PDType1Font(bold
                ? Standard14Fonts.FontName.HELVETICA_BOLD
                : Standard14Fonts.FontName.HELVETICA), fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
        return y - LINE_SPACING;
    }

    private String formatCurrency(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(locale).format(amount);
    }

    private String text(String key) {
        return messages.getString(key);
    }

    private String format(String key, Object... arguments) {
        return MessageFormat.format(text(key), arguments);
    }

    private String localizedRoomName(String value) {
        try {
            RoomType roomType = RoomType.valueOf(value);
            return text("room." + roomType.name().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
