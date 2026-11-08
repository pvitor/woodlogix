package com.woodlogix.desktop.ui.pricing;

import com.woodlogix.desktop.persistence.entity.EdgeBandingEntity;
import com.woodlogix.application.hardware.HardwareRow;
import com.woodlogix.desktop.persistence.entity.MaterialEntity;
import com.woodlogix.desktop.service.EdgeBandingData;
import com.woodlogix.desktop.service.EdgeBandingService;
import com.woodlogix.desktop.service.HardwareData;
import com.woodlogix.desktop.service.HardwareService;
import com.woodlogix.desktop.service.MaterialData;
import com.woodlogix.desktop.service.MaterialService;
import com.woodlogix.desktop.ui.UiTaskRunner;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URL;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;

@Component
@Lazy
public class MaterialController implements Initializable {
    private final MaterialService materialService;
    private final EdgeBandingService edgeBandingService;
    private final HardwareService hardwareService;
    private final UiTaskRunner taskRunner;
    private ResourceBundle messages;
    private String editingMaterialId;
    private String editingEdgeBandingId;
    private String editingHardwareId;

    @FXML private VBox materialForm;
    @FXML private VBox edgeBandingForm;
    @FXML private VBox hardwareForm;
    @FXML private VBox materialTableHost;
    @FXML private VBox edgeBandingTableHost;
    @FXML private VBox hardwareTableHost;
    @FXML private Label statusLabel;

    private final TextField materialNameField = new TextField();
    private final TextField materialColorField = new TextField();
    private final TextField materialThicknessField = new TextField();
    private final TextField materialLengthField = new TextField();
    private final TextField materialWidthField = new TextField();
    private final TextField materialPriceField = new TextField();
    private final Button materialSaveButton = new Button();
    private final Button materialCancelButton = new Button();
    private final TableView<MaterialEntity> materialTable = new TableView<>();

    private final TextField edgeBandingNameField = new TextField();
    private final TextField edgeBandingWidthField = new TextField();
    private final TextField edgeBandingPriceField = new TextField();
    private final Button edgeBandingSaveButton = new Button();
    private final Button edgeBandingCancelButton = new Button();
    private final TableView<EdgeBandingEntity> edgeBandingTable = new TableView<>();

    private final TextField hardwareNameField = new TextField();
    private final TextField hardwareUnitField = new TextField();
    private final TextField hardwarePriceField = new TextField();
    private final Button hardwareSaveButton = new Button();
    private final Button hardwareCancelButton = new Button();
    private final TableView<HardwareRow> hardwareTable = new TableView<>();

    public MaterialController(
            MaterialService materialService,
            EdgeBandingService edgeBandingService,
            HardwareService hardwareService,
            UiTaskRunner taskRunner
    ) {
        this.materialService = Objects.requireNonNull(materialService);
        this.edgeBandingService = Objects.requireNonNull(edgeBandingService);
        this.hardwareService = Objects.requireNonNull(hardwareService);
        this.taskRunner = Objects.requireNonNull(taskRunner);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        messages = resources;
        configureMaterialForm();
        configureEdgeBandingForm();
        configureHardwareForm();
        configureMaterialTable();
        configureEdgeBandingTable();
        configureHardwareTable();
        materialTableHost.getChildren().setAll(materialTable);
        edgeBandingTableHost.getChildren().setAll(edgeBandingTable);
        hardwareTableHost.getChildren().setAll(hardwareTable);
        VBox.setVgrow(materialTable, Priority.ALWAYS);
        VBox.setVgrow(edgeBandingTable, Priority.ALWAYS);
        VBox.setVgrow(hardwareTable, Priority.ALWAYS);
        loadCatalogs();
    }

    private void configureMaterialForm() {
        materialNameField.setPromptText(text("materials.name-prompt"));
        materialColorField.setPromptText(text("materials.color-prompt"));
        materialThicknessField.setPromptText("18");
        materialLengthField.setPromptText("2750");
        materialWidthField.setPromptText("1840");
        materialPriceField.setPromptText("0,00");
        styleNumericFields(materialThicknessField, materialLengthField, materialWidthField);
        styleCurrencyField(materialPriceField);
        materialSaveButton.setText(text("materials.add-sheet"));
        materialSaveButton.getStyleClass().add("primary-button");
        materialSaveButton.setOnAction(event -> saveMaterial());
        materialCancelButton.setText(text("action.cancel"));
        materialCancelButton.getStyleClass().add("secondary-button");
        materialCancelButton.setVisible(false);
        materialCancelButton.setManaged(false);
        materialCancelButton.setOnAction(event -> clearMaterialForm());
        materialForm.getChildren().setAll(
                fieldRow(
                        field("materials.name", materialNameField),
                        field("materials.color", materialColorField)),
                fieldRow(
                        field("materials.thickness", materialThicknessField),
                        field("materials.length", materialLengthField),
                        field("materials.sheet-width", materialWidthField),
                        field("materials.sheet-price", materialPriceField)),
                formActions(materialCancelButton, materialSaveButton));
    }

    private void configureEdgeBandingForm() {
        edgeBandingNameField.setPromptText(text("materials.edgebanding-name-prompt"));
        edgeBandingWidthField.setPromptText("22");
        edgeBandingPriceField.setPromptText("0,00");
        styleNumericFields(edgeBandingWidthField);
        styleCurrencyField(edgeBandingPriceField);
        edgeBandingSaveButton.setText(text("materials.add-edgebanding"));
        edgeBandingSaveButton.getStyleClass().add("primary-button");
        edgeBandingSaveButton.setOnAction(event -> saveEdgeBanding());
        edgeBandingCancelButton.setText(text("action.cancel"));
        edgeBandingCancelButton.getStyleClass().add("secondary-button");
        edgeBandingCancelButton.setVisible(false);
        edgeBandingCancelButton.setManaged(false);
        edgeBandingCancelButton.setOnAction(event -> clearEdgeBandingForm());
        edgeBandingForm.getChildren().setAll(
                field("materials.name", edgeBandingNameField),
                fieldRow(
                        field("materials.width", edgeBandingWidthField),
                        field("materials.price-per-meter", edgeBandingPriceField)),
                formActions(edgeBandingCancelButton, edgeBandingSaveButton));
    }

    private void configureHardwareForm() {
        hardwareNameField.setPromptText(text("materials.hardware-name-prompt"));
        hardwareUnitField.setPromptText(text("materials.unit-prompt"));
        hardwarePriceField.setPromptText("0,00");
        styleCurrencyField(hardwarePriceField);
        hardwareSaveButton.setText(text("materials.add-hardware"));
        hardwareSaveButton.getStyleClass().add("primary-button");
        hardwareSaveButton.setOnAction(event -> saveHardware());
        hardwareCancelButton.setText(text("action.cancel"));
        hardwareCancelButton.getStyleClass().add("secondary-button");
        hardwareCancelButton.setVisible(false);
        hardwareCancelButton.setManaged(false);
        hardwareCancelButton.setOnAction(event -> clearHardwareForm());
        hardwareForm.getChildren().setAll(
                fieldRow(
                        field("materials.name", hardwareNameField),
                        field("materials.unit", hardwareUnitField),
                        field("materials.unit-price", hardwarePriceField)),
                formActions(hardwareCancelButton, hardwareSaveButton));
    }

    private void configureMaterialTable() {
        TableColumn<MaterialEntity, String> name = column(
                "materials.name", item -> item.getName());
        TableColumn<MaterialEntity, String> color = column(
                "materials.color", item -> item.getColor());
        TableColumn<MaterialEntity, String> thickness = column(
                "materials.thickness", item -> format(item.getThicknessMm()));
        TableColumn<MaterialEntity, String> dimensions = column(
                "materials.sheet-size", item -> format(item.getLengthMm()) + " × "
                        + format(item.getWidthMm()));
        TableColumn<MaterialEntity, String> price = column(
                "materials.sheet-price", item -> currency(item.getPricePerSheet()));
        TableColumn<MaterialEntity, Void> actions = actionColumn(
                this::editMaterial, this::deleteMaterial);
        materialTable.getColumns().setAll(name, color, thickness, dimensions, price, actions);
        materialTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        materialTable.setPlaceholder(emptyState("materials.empty-sheets"));
        actions.setMinWidth(78);
        actions.setPrefWidth(78);
    }

    private void configureEdgeBandingTable() {
        TableColumn<EdgeBandingEntity, String> name = column(
                "materials.name", item -> item.getName());
        TableColumn<EdgeBandingEntity, String> width = column(
                "materials.width", item -> format(item.getWidthMm()));
        TableColumn<EdgeBandingEntity, String> price = column(
                "materials.price-per-meter",
                item -> currency(item.getPricePerMeter()) + " / m");
        TableColumn<EdgeBandingEntity, Void> actions = actionColumn(
                this::editEdgeBanding, this::deleteEdgeBanding);
        edgeBandingTable.getColumns().setAll(name, width, price, actions);
        edgeBandingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        edgeBandingTable.setPlaceholder(emptyState("materials.empty-edgebandings"));
        actions.setMinWidth(78);
        actions.setPrefWidth(78);
    }

    private void configureHardwareTable() {
        TableColumn<HardwareRow, String> name = column(
                "materials.name", item -> item.name());
        TableColumn<HardwareRow, String> unit = column(
                "materials.unit", item -> item.unit());
        TableColumn<HardwareRow, String> price = column(
                "materials.unit-price", item -> currency(item.unitPrice()));
        TableColumn<HardwareRow, Void> actions = actionColumn(
                this::editHardware, this::deleteHardware);
        hardwareTable.getColumns().setAll(name, unit, price, actions);
        hardwareTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        hardwareTable.setPlaceholder(emptyState("materials.empty-hardware"));
        actions.setMinWidth(78);
        actions.setPrefWidth(78);
    }

    private <T> TableColumn<T, Void> actionColumn(
            Consumer<T> editAction,
            Consumer<T> deleteAction
    ) {
        TableColumn<T, Void> column = new TableColumn<>(text("column.actions"));
        column.setSortable(false);
        column.setCellFactory(ignored -> new TableCell<>() {
            private final Button editButton = actionButton(
                    "tooltip.action-edit",
                    "M3 11 L10.8 3.2 L13.8 6.2 L6 14 H3 Z M9.8 4.2 L12.8 7.2");
            private final Button deleteButton = actionButton(
                    "tooltip.action-delete",
                    "M2 4 H14 M5 4 V2 H11 V4 M4 5 L5 14 H11 L12 5 M6.5 7 V12 M9.5 7 V12");
            private final HBox buttons = new HBox(5, editButton, deleteButton);

            {
                editButton.getStyleClass().add("btn-edit");
                deleteButton.getStyleClass().add("btn-delete");
                buttons.setAlignment(Pos.CENTER);
                editButton.setOnAction(event -> {
                    T row = getTableRow().getItem();
                    if (row != null) {
                        editAction.accept(row);
                    }
                });
                deleteButton.setOnAction(event -> {
                    T row = getTableRow().getItem();
                    if (row != null) {
                        deleteAction.accept(row);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : buttons);
            }
        });
        return column;
    }

    private void saveMaterial() {
        BigDecimal thickness = decimal(materialThicknessField);
        BigDecimal length = decimal(materialLengthField);
        BigDecimal width = decimal(materialWidthField);
        BigDecimal price = decimal(materialPriceField);
        if (isBlank(materialNameField.getText()) || isBlank(materialColorField.getText())
                || thickness == null || length == null || width == null || price == null
                || thickness.signum() <= 0 || length.signum() <= 0 || width.signum() <= 0
                || price.signum() < 0) {
            showValidation("validation.material-required");
            return;
        }
        MaterialData data = new MaterialData(materialNameField.getText().trim(),
                materialColorField.getText().trim(), thickness, length, width, price);
        String id = editingMaterialId;
        taskRunner.execute(() -> id == null ? materialService.create(data)
                        : materialService.update(id, data),
                messages, saved -> {
                    clearMaterialForm();
                    statusLabel.setText(text(id == null
                            ? "status.material-created" : "status.material-updated"));
                    loadMaterials();
                }, message -> statusLabel.setText(message));
    }

    private void saveEdgeBanding() {
        BigDecimal width = decimal(edgeBandingWidthField);
        BigDecimal price = decimal(edgeBandingPriceField);
        if (isBlank(edgeBandingNameField.getText()) || width == null || price == null
                || width.signum() <= 0 || price.signum() < 0) {
            showValidation("validation.edgebanding-required");
            return;
        }
        EdgeBandingData data = new EdgeBandingData(edgeBandingNameField.getText().trim(),
                width, price);
        String id = editingEdgeBandingId;
        taskRunner.execute(() -> id == null ? edgeBandingService.create(data)
                        : edgeBandingService.update(id, data),
                messages, saved -> {
                    clearEdgeBandingForm();
                    statusLabel.setText(text(id == null
                            ? "status.edgebanding-created" : "status.edgebanding-updated"));
                    loadEdgeBandings();
                }, message -> statusLabel.setText(message));
    }

    private void saveHardware() {
        BigDecimal price = decimal(hardwarePriceField);
        if (isBlank(hardwareNameField.getText()) || isBlank(hardwareUnitField.getText())
                || price == null || price.signum() < 0) {
            showValidation("validation.hardware-required");
            return;
        }
        HardwareData data = new HardwareData(hardwareNameField.getText().trim(),
                hardwareUnitField.getText().trim(), price);
        String id = editingHardwareId;
        taskRunner.execute(() -> id == null ? hardwareService.create(data)
                        : hardwareService.update(id, data),
                messages, saved -> {
                    clearHardwareForm();
                    statusLabel.setText(text(id == null
                            ? "status.hardware-created" : "status.hardware-updated"));
                    loadHardware();
                }, message -> statusLabel.setText(message));
    }

    private void editMaterial(MaterialEntity material) {
        editingMaterialId = material.getId();
        materialNameField.setText(material.getName());
        materialColorField.setText(material.getColor());
        materialThicknessField.setText(format(material.getThicknessMm()));
        materialLengthField.setText(format(material.getLengthMm()));
        materialWidthField.setText(format(material.getWidthMm()));
        materialPriceField.setText(format(material.getPricePerSheet()));
        materialSaveButton.setText(text("action.save"));
        materialCancelButton.setVisible(true);
        materialCancelButton.setManaged(true);
    }

    private void editEdgeBanding(EdgeBandingEntity edgeBanding) {
        editingEdgeBandingId = edgeBanding.getId();
        edgeBandingNameField.setText(edgeBanding.getName());
        edgeBandingWidthField.setText(format(edgeBanding.getWidthMm()));
        edgeBandingPriceField.setText(format(edgeBanding.getPricePerMeter()));
        edgeBandingSaveButton.setText(text("action.save"));
        edgeBandingCancelButton.setVisible(true);
        edgeBandingCancelButton.setManaged(true);
    }

    private void editHardware(HardwareRow hardware) {
        editingHardwareId = hardware.id();
        hardwareNameField.setText(hardware.name());
        hardwareUnitField.setText(hardware.unit());
        hardwarePriceField.setText(format(hardware.unitPrice()));
        hardwareSaveButton.setText(text("action.save"));
        hardwareCancelButton.setVisible(true);
        hardwareCancelButton.setManaged(true);
    }

    private void deleteMaterial(MaterialEntity material) {
        if (!UiTaskRunner.confirm(format("dialog.confirm-delete-material", material.getName()),
                messages)) {
            return;
        }
        taskRunner.execute(() -> {
            materialService.delete(material.getId());
            return null;
        }, messages, ignored -> {
            statusLabel.setText(text("status.material-deleted"));
            loadMaterials();
        }, message -> statusLabel.setText(message));
    }

    private void deleteEdgeBanding(EdgeBandingEntity edgeBanding) {
        if (!UiTaskRunner.confirm(format("dialog.confirm-delete-edgebanding",
                edgeBanding.getName()), messages)) {
            return;
        }
        taskRunner.execute(() -> {
            edgeBandingService.delete(edgeBanding.getId());
            return null;
        }, messages, ignored -> {
            statusLabel.setText(text("status.edgebanding-deleted"));
            loadEdgeBandings();
        }, message -> statusLabel.setText(message));
    }

    private void deleteHardware(HardwareRow hardware) {
        if (!UiTaskRunner.confirm(format("dialog.confirm-delete-hardware", hardware.name()),
                messages)) {
            return;
        }
        taskRunner.execute(() -> {
            hardwareService.delete(hardware.id());
            return null;
        }, messages, ignored -> {
            statusLabel.setText(text("status.hardware-deleted"));
            loadHardware();
        }, message -> statusLabel.setText(message));
    }

    private void loadCatalogs() {
        loadMaterials();
        loadEdgeBandings();
        loadHardware();
    }

    private void loadMaterials() {
        taskRunner.execute(materialService::findAll, messages,
                items -> {
                    materialTable.setItems(FXCollections.observableArrayList(items));
                    statusLabel.setText(text("status.synced"));
                },
                message -> statusLabel.setText(message));
    }

    private void loadEdgeBandings() {
        taskRunner.execute(edgeBandingService::findAll, messages,
                items -> {
                    edgeBandingTable.setItems(FXCollections.observableArrayList(items));
                    statusLabel.setText(text("status.synced"));
                },
                message -> statusLabel.setText(message));
    }

    private void loadHardware() {
        taskRunner.execute(hardwareService::findAll, messages,
                items -> {
                    hardwareTable.setItems(FXCollections.observableArrayList(items));
                    statusLabel.setText(text("status.synced"));
                },
                message -> statusLabel.setText(message));
    }

    private void clearMaterialForm() {
        editingMaterialId = null;
        materialNameField.clear();
        materialColorField.clear();
        materialThicknessField.clear();
        materialLengthField.clear();
        materialWidthField.clear();
        materialPriceField.clear();
        materialSaveButton.setText(text("materials.add-sheet"));
        materialCancelButton.setVisible(false);
        materialCancelButton.setManaged(false);
    }

    private void clearEdgeBandingForm() {
        editingEdgeBandingId = null;
        edgeBandingNameField.clear();
        edgeBandingWidthField.clear();
        edgeBandingPriceField.clear();
        edgeBandingSaveButton.setText(text("materials.add-edgebanding"));
        edgeBandingCancelButton.setVisible(false);
        edgeBandingCancelButton.setManaged(false);
    }

    private void clearHardwareForm() {
        editingHardwareId = null;
        hardwareNameField.clear();
        hardwareUnitField.clear();
        hardwarePriceField.clear();
        hardwareSaveButton.setText(text("materials.add-hardware"));
        hardwareCancelButton.setVisible(false);
        hardwareCancelButton.setManaged(false);
    }

    private VBox field(String labelKey, TextField input) {
        Label label = new Label(text(labelKey));
        label.getStyleClass().add("field-label");
        VBox field = new VBox(6, label, input);
        field.getStyleClass().add("catalog-field");
        HBox.setHgrow(field, Priority.ALWAYS);
        input.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    private HBox fieldRow(VBox... fields) {
        HBox row = new HBox(12, fields);
        row.getStyleClass().add("catalog-field-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void styleNumericFields(TextField... fields) {
        for (TextField field : fields) {
            field.getStyleClass().add("catalog-numeric-field");
        }
    }

    private void styleCurrencyField(TextField field) {
        field.getStyleClass().add("catalog-numeric-field");
        field.getStyleClass().add("catalog-currency-field");
    }

    private Label emptyState(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("empty-state");
        label.setWrapText(true);
        return label;
    }

    private Button actionButton(String accessibleTextKey, String iconPath) {
        Button button = new Button();
        SVGPath icon = new SVGPath();
        icon.setContent(iconPath);
        icon.getStyleClass().add("table-action-icon");
        button.setGraphic(icon);
        button.setAccessibleText(text(accessibleTextKey));
        button.setTooltip(new Tooltip(text(accessibleTextKey)));
        return button;
    }

    private HBox formActions(Button cancel, Button save) {
        HBox actions = new HBox(8, cancel, save);
        actions.setAlignment(Pos.CENTER_RIGHT);
        return actions;
    }

    private <T> TableColumn<T, String> column(
            String titleKey,
            java.util.function.Function<T, String> valueFactory
    ) {
        TableColumn<T, String> column = new TableColumn<>(text(titleKey));
        column.setCellValueFactory(cell ->
                new javafx.beans.property.ReadOnlyStringWrapper(
                        valueFactory.apply(cell.getValue())));
        return column;
    }

    private void showValidation(String key) {
        UiTaskRunner.showInformation(text("dialog.error-header"), text(key), messages);
    }

    private String currency(BigDecimal amount) {
        return java.text.NumberFormat.getCurrencyInstance(locale()).format(amount);
    }

    private String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private BigDecimal decimal(TextField field) {
        try {
            return new BigDecimal(field.getText().trim().replace(',', '.'));
        } catch (NumberFormatException | NullPointerException exception) {
            return null;
        }
    }

    private String text(String key) {
        return messages.getString(key);
    }

    private String format(String key, Object... arguments) {
        return MessageFormat.format(text(key), arguments);
    }

    private Locale locale() {
        return messages.getLocale();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
