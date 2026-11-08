package com.woodlogix.desktop.ui.pricing;

import com.woodlogix.core.model.EdgeBanding;
import com.woodlogix.core.model.Hardware;
import com.woodlogix.core.model.HardwareUsage;
import com.woodlogix.core.model.Material;
import com.woodlogix.core.model.Workpiece;
import com.woodlogix.core.service.PricingEngine;
import com.woodlogix.desktop.persistence.entity.EdgeBandingEntity;
import com.woodlogix.desktop.persistence.entity.EstimateDraftWorkpieceEntity;
import com.woodlogix.application.hardware.HardwareRow;
import com.woodlogix.desktop.persistence.entity.MaterialEntity;
import com.woodlogix.desktop.persistence.entity.ProjectWorkpieceEntity;
import com.woodlogix.desktop.service.EdgeBandingService;
import com.woodlogix.desktop.service.EstimateDraftData;
import com.woodlogix.desktop.service.EstimateDraftService;
import com.woodlogix.desktop.service.HardwareService;
import com.woodlogix.desktop.service.MaterialService;
import com.woodlogix.desktop.service.ProjectWorkpieceData;
import com.woodlogix.desktop.service.ProjectWorkpieceService;
import com.woodlogix.desktop.service.SalesPipelineService;
import com.woodlogix.desktop.service.SalesProjectRow;
import com.woodlogix.desktop.ui.UiTaskRunner;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.StringConverter;
import javafx.util.Duration;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URL;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;

@Component
@Lazy
public class PricingController implements Initializable {
    private static final String EDIT_ICON_PATH =
            "M3 11.5 L10.8 3.7 C11.5 3 12.6 3 13.3 3.7 C14 4.4 14 5.5 "
                    + "13.3 6.2 L5.5 14 H3 Z M9.8 4.7 L12.3 7.2";
    private static final String DELETE_ICON_PATH =
            "M2 4 H14 M6 4 V2 H10 V4 M4 4 L4.8 14 H11.2 L12 4 "
                    + "M6.5 6 V12 M9.5 6 V12";

    private final SalesPipelineService salesPipelineService;
    private final ProjectWorkpieceService workpieceService;
    private final MaterialService materialService;
    private final EdgeBandingService edgeBandingService;
    private final HardwareService hardwareService;
    private final EstimateDraftService estimateDraftService;
    private final UiTaskRunner taskRunner;
    private final PricingEngine pricingEngine = new PricingEngine();
    private final List<HardwareUsage> hardwareUsages = new ArrayList<>();
    private final PauseTransition draftSaveTimer = new PauseTransition(Duration.millis(400));

    private ResourceBundle messages;
    private String editingWorkpieceId;
    private boolean restoringDraft;
    private boolean draftLoaded;
    private boolean closeSaveInProgress;
    private boolean closeRequested;
    private boolean projectWorkpiecesLoading;
    private Stage hostWindow;
    private List<MaterialEntity> materials = List.of();
    private List<EdgeBandingEntity> edgeBandings = List.of();
    private List<HardwareRow> hardwares = List.of();

    @FXML private ComboBox<SalesProjectRow> projectComboBox;
    @FXML private ComboBox<MaterialEntity> materialComboBox;
    @FXML private ComboBox<EdgeBandingEntity> edgeBandingComboBox;
    @FXML private ComboBox<HardwareRow> hardwareComboBox;
    @FXML private TextField nameField;
    @FXML private TextField lengthField;
    @FXML private TextField widthField;
    @FXML private TextField quantityField;
    @FXML private TextField wasteField;
    @FXML private TextField hoursField;
    @FXML private TextField laborRateField;
    @FXML private TextField machineRateField;
    @FXML private TextField marginField;
    @FXML private TextField hardwareQuantityField;
    @FXML private CheckBox topEdgeCheckBox;
    @FXML private CheckBox bottomEdgeCheckBox;
    @FXML private CheckBox leftEdgeCheckBox;
    @FXML private CheckBox rightEdgeCheckBox;
    @FXML private Button cancelEditButton;
    @FXML private Button addWorkpieceButton;
    @FXML private VBox workpieceForm;
    @FXML private Label editingIndicator;
    @FXML private TableView<EstimateDraftWorkpieceEntity> workpieceTable;
    @FXML private TableColumn<EstimateDraftWorkpieceEntity, String> workpieceNameColumn;
    @FXML private TableColumn<EstimateDraftWorkpieceEntity, String> dimensionsColumn;
    @FXML private TableColumn<EstimateDraftWorkpieceEntity, String> quantityColumn;
    @FXML private TableColumn<EstimateDraftWorkpieceEntity, String> materialColumn;
    @FXML private TableColumn<EstimateDraftWorkpieceEntity, Void> actionsColumn;
    @FXML private VBox hardwareSummary;
    @FXML private Label sheetCountLabel;
    @FXML private Label edgeMetersLabel;
    @FXML private Label materialCostLabel;
    @FXML private Label edgeCostLabel;
    @FXML private Label hardwareCostLabel;
    @FXML private Label laborCostLabel;
    @FXML private Label baseCostLabel;
    @FXML private Label suggestedPriceLabel;
    @FXML private Label profitLabel;
    @FXML private Label statusLabel;

    public PricingController(
            SalesPipelineService salesPipelineService,
            ProjectWorkpieceService workpieceService,
            MaterialService materialService,
            EdgeBandingService edgeBandingService,
            HardwareService hardwareService,
            EstimateDraftService estimateDraftService,
            UiTaskRunner taskRunner
    ) {
        this.salesPipelineService = Objects.requireNonNull(salesPipelineService);
        this.workpieceService = Objects.requireNonNull(workpieceService);
        this.materialService = Objects.requireNonNull(materialService);
        this.edgeBandingService = Objects.requireNonNull(edgeBandingService);
        this.hardwareService = Objects.requireNonNull(hardwareService);
        this.estimateDraftService = Objects.requireNonNull(estimateDraftService);
        this.taskRunner = Objects.requireNonNull(taskRunner);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        messages = resources;
        configureComboBoxes();
        configureWorkpieceTable();
        projectComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!restoringDraft && !sameProject(oldValue, newValue)) {
                clearForm();
                hardwareUsages.clear();
                renderHardwareSummary();
                if (newValue == null) {
                    projectWorkpiecesLoading = false;
                    workpieceTable.setItems(FXCollections.observableArrayList(
                            workpieceTable.getItems().stream()
                                    .map(item -> new EstimateDraftWorkpieceEntity(null,
                                            item.getName(), item.getLengthMm(),
                                            item.getWidthMm(), item.getQuantity(),
                                            item.getMaterial(), item.getEdgeBanding(),
                                            item.isEdgeTop(), item.isEdgeBottom(),
                                            item.isEdgeLeft(), item.isEdgeRight()))
                                    .toList()));
                    saveDraft();
                } else {
                    loadWorkpieces(newValue);
                }
                updateSummary();
            }
        });
        setDefault(wasteField, "15");
        setDefault(hoursField, "0");
        setDefault(laborRateField, "0");
        setDefault(machineRateField, "0");
        setDefault(marginField, "30");
        for (TextField field : List.of(wasteField, hoursField, laborRateField,
                machineRateField, marginField)) {
            field.textProperty().addListener((observable, oldValue, newValue) -> {
                updateSummary();
                scheduleDraftSave();
            });
        }
        draftSaveTimer.setOnFinished(event -> saveDraft());
        updateSummary();
        Platform.runLater(this::installCloseHandler);
    }

    public void refreshCatalogs() {
        boolean restoreSavedDraft = !draftLoaded;
        if (draftLoaded) {
            draftSaveTimer.stop();
            saveDraft();
        }
        String selectedProjectId = projectComboBox.getValue() == null
                ? null : projectComboBox.getValue().id();
        taskRunner.execute(() -> new PricingViewData(
                        salesPipelineService.findPipeline().values().stream()
                                .flatMap(List::stream).toList(),
                        materialService.findAll(),
                        edgeBandingService.findAll(),
                        hardwareService.findAll(),
                        estimateDraftService.findActiveDraft()),
                messages, data -> applyViewData(data, selectedProjectId, restoreSavedDraft),
                message -> statusLabel.setText(message));
    }

    @FXML
    private void clearProjectLink() {
        clearForm();
        List<EstimateDraftWorkpieceEntity> standaloneWorkpieces =
                workpieceTable.getItems().stream()
                        .map(item -> new EstimateDraftWorkpieceEntity(null, item.getName(),
                                item.getLengthMm(), item.getWidthMm(), item.getQuantity(),
                                item.getMaterial(), item.getEdgeBanding(), item.isEdgeTop(),
                                item.isEdgeBottom(), item.isEdgeLeft(), item.isEdgeRight()))
                        .toList();
        restoringDraft = true;
        try {
            projectComboBox.getSelectionModel().clearSelection();
            workpieceTable.setItems(FXCollections.observableArrayList(standaloneWorkpieces));
        } finally {
            restoringDraft = false;
        }
        projectWorkpiecesLoading = false;
        saveDraft();
        updateSummary();
    }

    private void configureComboBoxes() {
        projectComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(SalesProjectRow project) {
                return project == null ? "" : project.projectName() + " · "
                        + project.customerName();
            }

            @Override
            public SalesProjectRow fromString(String value) {
                return projectComboBox.getItems().stream()
                        .filter(project -> toString(project).equalsIgnoreCase(value))
                        .findFirst().orElse(null);
            }
        });
        projectComboBox.setCellFactory(list -> cell(projectComboBox.getConverter()));
        projectComboBox.setButtonCell(cell(projectComboBox.getConverter()));
        materialComboBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(MaterialEntity item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : materialName(item));
            }
        });
        materialComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(MaterialEntity item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : materialName(item));
            }
        });
        edgeBandingComboBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(EdgeBandingEntity item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : edgeBandingName(item));
            }
        });
        edgeBandingComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(EdgeBandingEntity item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : edgeBandingName(item));
            }
        });
        hardwareComboBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(HardwareRow item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : hardwareName(item));
            }
        });
        hardwareComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(HardwareRow item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : hardwareName(item));
            }
        });
    }

    private void configureWorkpieceTable() {
        workpieceNameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getName()));
        dimensionsColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                formatDimension(cell.getValue().getLengthMm()) + " × "
                        + formatDimension(cell.getValue().getWidthMm()) + " mm"));
        quantityColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(Integer.toString(cell.getValue().getQuantity())));
        materialColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                materialName(cell.getValue().getMaterial())));
        actionsColumn.setCellFactory(column -> createActionsCell(
                this::editWorkpiece, this::confirmDeleteWorkpiece));
    }

    @FXML
    private void saveWorkpiece() {
        SalesProjectRow project = projectComboBox.getValue();
        BigDecimal length = decimal(lengthField);
        BigDecimal width = decimal(widthField);
        Integer quantity = integer(quantityField);
        MaterialEntity material = materialComboBox.getValue();
        boolean hasEdges = topEdgeCheckBox.isSelected() || bottomEdgeCheckBox.isSelected()
                || leftEdgeCheckBox.isSelected() || rightEdgeCheckBox.isSelected();
        EdgeBandingEntity edgeBanding = edgeBandingComboBox.getValue();
        if (isBlank(nameField.getText()) || length == null || width == null
                || length.signum() <= 0 || width.signum() <= 0
                || quantity == null || quantity <= 0 || material == null
                || hasEdges && edgeBanding == null) {
            showValidation("validation.workpiece-required");
            return;
        }
        EstimateDraftWorkpieceEntity editing = workpieceTable.getItems().stream()
                .filter(item -> item.getId().equals(editingWorkpieceId))
                .findFirst().orElse(null);
        if (project == null || editing != null && editing.getProjectWorkpieceId() == null) {
            EstimateDraftWorkpieceEntity saved = new EstimateDraftWorkpieceEntity(null,
                    nameField.getText().trim(), length, width, quantity, material,
                    edgeBanding, topEdgeCheckBox.isSelected(), bottomEdgeCheckBox.isSelected(),
                    leftEdgeCheckBox.isSelected(), rightEdgeCheckBox.isSelected());
            if (editing == null) {
                workpieceTable.getItems().add(saved);
            } else {
                int index = workpieceTable.getItems().indexOf(editing);
                workpieceTable.getItems().set(index, saved);
            }
            clearForm();
            saveDraft();
            updateSummary();
            statusLabel.setText(text(editing == null
                    ? "status.workpiece-created" : "status.workpiece-updated"));
            return;
        }

        String projectWorkpieceId = editing == null
                ? null : editing.getProjectWorkpieceId();
        ProjectWorkpieceData data = new ProjectWorkpieceData(
                project.id(), nameField.getText().trim(), length, width, quantity,
                material.getId(), edgeBanding == null ? null : edgeBanding.getId(),
                topEdgeCheckBox.isSelected(), bottomEdgeCheckBox.isSelected(),
                leftEdgeCheckBox.isSelected(), rightEdgeCheckBox.isSelected());
        taskRunner.execute(() -> projectWorkpieceId == null
                        ? workpieceService.create(data)
                        : workpieceService.update(projectWorkpieceId, data),
                messages, saved -> {
                    EstimateDraftWorkpieceEntity draftWorkpiece = toDraftWorkpiece(saved);
                    if (editing == null) {
                        workpieceTable.getItems().add(draftWorkpiece);
                    } else {
                        int index = workpieceTable.getItems().indexOf(editing);
                        workpieceTable.getItems().set(index, draftWorkpiece);
                    }
                    clearForm();
                    saveDraft();
                    updateSummary();
                    statusLabel.setText(text(editing == null
                            ? "status.workpiece-created" : "status.workpiece-updated"));
                }, message -> statusLabel.setText(message));
    }

    @FXML
    private void cancelEdit() {
        clearForm();
    }

    @FXML
    private void addHardware() {
        HardwareRow selectedHardware = hardwareComboBox.getValue();
        Integer selectedQuantity = integer(hardwareQuantityField);
        if (selectedHardware == null || selectedQuantity == null || selectedQuantity <= 0) {
            showValidation("validation.hardware-usage-required");
            return;
        }
        Hardware selected = toCore(selectedHardware);
        HardwareUsage existing = hardwareUsages.stream()
                .filter(usage -> usage.getHardware().getId().equals(selected.getId()))
                .findFirst().orElse(null);
        if (existing == null) {
            hardwareUsages.add(new HardwareUsage(selected, selectedQuantity));
        } else {
            existing.setQuantity(existing.getQuantity() + selectedQuantity);
        }
        hardwareQuantityField.clear();
        hardwareComboBox.getSelectionModel().clearSelection();
        renderHardwareSummary();
        saveDraft();
        updateSummary();
    }

    private void applyViewData(
            PricingViewData data,
            String selectedProjectId,
            boolean restoreSavedDraft
    ) {
        String selectedMaterialId = materialComboBox.getValue() == null
                ? null : materialComboBox.getValue().getId();
        String selectedEdgeBandingId = edgeBandingComboBox.getValue() == null
                ? null : edgeBandingComboBox.getValue().getId();
        String selectedHardwareId = hardwareComboBox.getValue() == null
                ? null : hardwareComboBox.getValue().id();
        restoringDraft = true;
        try {
            projectComboBox.setItems(FXCollections.observableArrayList(data.projects()));
            projectComboBox.getItems().stream()
                    .filter(project -> project.id().equals(selectedProjectId))
                    .findFirst().ifPresent(projectComboBox.getSelectionModel()::select);
            materials = List.copyOf(data.materials());
            materialComboBox.setItems(FXCollections.observableArrayList(materials));
            selectById(materials, selectedMaterialId, MaterialEntity::getId,
                    materialComboBox);
            edgeBandings = List.copyOf(data.edgeBandings());
            edgeBandingComboBox.setItems(FXCollections.observableArrayList(edgeBandings));
            selectById(edgeBandings, selectedEdgeBandingId, EdgeBandingEntity::getId,
                    edgeBandingComboBox);
            hardwares = List.copyOf(data.hardwares());
            hardwareComboBox.setItems(FXCollections.observableArrayList(hardwares));
            selectById(hardwares, selectedHardwareId, HardwareRow::id,
                    hardwareComboBox);
        } finally {
            restoringDraft = false;
        }

        if (restoreSavedDraft && data.draft().isPresent()) {
            restoreDraft(data.draft().orElseThrow());
            draftLoaded = true;
        } else if (restoreSavedDraft && projectComboBox.getValue() != null) {
            loadWorkpieces(projectComboBox.getValue());
            draftLoaded = true;
        } else if (restoreSavedDraft) {
            workpieceTable.getItems().clear();
            updateSummary();
            if (data.projects().isEmpty()) {
                statusLabel.setText(text("status.no-projects"));
            }
            draftLoaded = true;
        } else {
            updateSummary();
        }
    }

    private <T> void selectById(
            List<T> items,
            String selectedId,
            java.util.function.Function<T, String> idFunction,
            ComboBox<T> comboBox
    ) {
        items.stream().filter(item -> Objects.equals(idFunction.apply(item), selectedId))
                .findFirst().ifPresent(comboBox.getSelectionModel()::select);
    }

    private void restoreDraft(EstimateDraftData draft) {
        restoringDraft = true;
        try {
            projectComboBox.getSelectionModel().clearSelection();
            if (draft.projectId() != null) {
                projectComboBox.getItems().stream()
                        .filter(project -> project.id().equals(draft.projectId()))
                        .findFirst().ifPresent(projectComboBox.getSelectionModel()::select);
            }
            wasteField.setText(draft.wasteFactor().toPlainString());
            hoursField.setText(draft.estimatedHours().toPlainString());
            laborRateField.setText(draft.hourlyRate().toPlainString());
            machineRateField.setText(draft.machineRate().toPlainString());
            marginField.setText(draft.profitMargin().toPlainString());

            List<EstimateDraftWorkpieceEntity> workpieces = draft.workpieces().stream()
                    .map(item -> new EstimateDraftWorkpieceEntity(
                            item.projectWorkpieceId(), item.name(), item.lengthMm(),
                            item.widthMm(), item.quantity(), findMaterial(item.materialId()),
                            findEdgeBanding(item.edgeBandingId()), item.edgeTop(),
                            item.edgeBottom(), item.edgeLeft(), item.edgeRight()))
                    .toList();
            workpieceTable.setItems(FXCollections.observableArrayList(workpieces));

            hardwareUsages.clear();
            draft.hardwares().forEach(item -> {
                HardwareRow hardware = hardwares.stream()
                        .filter(candidate -> candidate.id().equals(item.hardwareId()))
                        .findFirst().orElseThrow(() ->
                                new IllegalStateException("Draft hardware is no longer available"));
                hardwareUsages.add(new HardwareUsage(toCore(hardware), item.quantity()));
            });
            renderHardwareSummary();
            updateSummary();
            statusLabel.setText(text("status.draft-restored"));
        } finally {
            restoringDraft = false;
        }
    }

    private void scheduleDraftSave() {
        if (!restoringDraft) {
            draftSaveTimer.playFromStart();
        }
    }

    @FXML
    private void saveDraftNow() {
        draftSaveTimer.stop();
        saveDraft(null, null, true);
    }

    private void saveDraft() {
        saveDraft(null, null, false);
    }

    private void saveDraft(Runnable onSuccess, Runnable onFailure, boolean reportInvalidData) {
        if (restoringDraft) {
            return;
        }
        EstimateDraftData data = buildDraftData();
        if (data == null) {
            if (reportInvalidData) {
                statusLabel.setText(text("error.pricing-input"));
            }
            if (onFailure != null) {
                onFailure.run();
            }
            return;
        }
        taskRunner.execute(() -> {
            estimateDraftService.saveActiveDraft(data);
            return null;
        }, messages, ignored -> {
            statusLabel.setText(text("status.draft-saved"));
            if (onSuccess != null) {
                onSuccess.run();
            }
        }, message -> {
            statusLabel.setText(message);
            if (onFailure != null) {
                onFailure.run();
            }
        });
    }

    private EstimateDraftData buildDraftData() {
        BigDecimal waste = decimal(wasteField);
        BigDecimal hours = decimal(hoursField);
        BigDecimal laborRate = decimal(laborRateField);
        BigDecimal machineRate = decimal(machineRateField);
        BigDecimal margin = decimal(marginField);
        if (!isNonNegative(waste) || !isNonNegative(hours) || !isNonNegative(laborRate)
                || !isNonNegative(machineRate) || !isNonNegative(margin)
                || margin.compareTo(new BigDecimal("100")) >= 0) {
            return null;
        }
        return new EstimateDraftData(
                projectComboBox.getValue() == null ? null : projectComboBox.getValue().id(),
                waste, hours, laborRate, machineRate, margin,
                workpieceTable.getItems().stream().map(item ->
                        new EstimateDraftData.EstimateDraftWorkpieceData(
                                item.getProjectWorkpieceId(), item.getName(),
                                item.getLengthMm(), item.getWidthMm(), item.getQuantity(),
                                item.getMaterial().getId(),
                                item.getEdgeBanding() == null ? null
                                        : item.getEdgeBanding().getId(),
                                item.isEdgeTop(), item.isEdgeBottom(),
                                item.isEdgeLeft(), item.isEdgeRight()))
                        .toList(),
                hardwareUsages.stream().map(usage ->
                        new EstimateDraftData.EstimateDraftHardwareData(
                                usage.getHardware().getId(), usage.getQuantity()))
                        .toList());
    }

    private void installCloseHandler() {
        if (hostWindow != null || workpieceTable.getScene() == null
                || !(workpieceTable.getScene().getWindow() instanceof Stage stage)) {
            return;
        }
        hostWindow = stage;
        hostWindow.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, this::saveBeforeClose);
    }

    private void saveBeforeClose(WindowEvent event) {
        event.consume();
        if (closeSaveInProgress) {
            return;
        }
        closeSaveInProgress = true;
        closeRequested = true;
        draftSaveTimer.stop();
        if (projectWorkpiecesLoading) {
            return;
        }
        finishCloseSave();
    }

    private void finishCloseSave() {
        closeSaveInProgress = true;
        saveDraft(() -> {
            closeRequested = false;
            hostWindow.hide();
        }, () -> {
            closeRequested = false;
            closeSaveInProgress = false;
        }, true);
    }

    private MaterialEntity findMaterial(String id) {
        return materials.stream().filter(material -> material.getId().equals(id))
                .findFirst().orElseThrow(() ->
                        new IllegalStateException("Draft material is no longer available"));
    }

    private EdgeBandingEntity findEdgeBanding(String id) {
        if (id == null) {
            return null;
        }
        return edgeBandings.stream().filter(edge -> edge.getId().equals(id))
                .findFirst().orElseThrow(() ->
                        new IllegalStateException("Draft edge banding is no longer available"));
    }

    private static boolean isNonNegative(BigDecimal value) {
        return value != null && value.signum() >= 0;
    }

    private static void setDefault(TextField field, String value) {
        if (field.getText() == null || field.getText().isBlank()) {
            field.setText(value);
        }
    }

    private record PricingViewData(
            List<SalesProjectRow> projects,
            List<MaterialEntity> materials,
            List<EdgeBandingEntity> edgeBandings,
            List<HardwareRow> hardwares,
            java.util.Optional<EstimateDraftData> draft
    ) {
    }

    private void loadWorkpieces(SalesProjectRow project) {
        if (project == null) {
            projectWorkpiecesLoading = false;
            workpieceTable.getItems().clear();
            saveDraft();
            return;
        }
        projectWorkpiecesLoading = true;
        taskRunner.execute(() -> workpieceService.findByProject(project.id()), messages,
                workpieces -> {
                    if (projectComboBox.getValue() != null
                            && project.id().equals(projectComboBox.getValue().id())) {
                        projectWorkpiecesLoading = false;
                        workpieceTable.setItems(FXCollections.observableArrayList(workpieces
                                .stream().map(this::toDraftWorkpiece).toList()));
                        statusLabel.setText(text("status.synced"));
                        saveDraft();
                        updateSummary();
                        if (closeRequested) {
                            finishCloseSave();
                        }
                    }
                }, message -> {
                    statusLabel.setText(message);
                    if (projectComboBox.getValue() != null
                            && project.id().equals(projectComboBox.getValue().id())) {
                        projectWorkpiecesLoading = false;
                        if (closeRequested) {
                            finishCloseSave();
                        }
                    }
                });
    }

    private void editWorkpiece(EstimateDraftWorkpieceEntity workpiece) {
        editingWorkpieceId = workpiece.getId();
        if (!workpieceForm.getStyleClass().contains("editing")) {
            workpieceForm.getStyleClass().add("editing");
        }
        editingIndicator.setVisible(true);
        editingIndicator.setManaged(true);
        nameField.setText(workpiece.getName());
        lengthField.setText(formatDimension(workpiece.getLengthMm()));
        widthField.setText(formatDimension(workpiece.getWidthMm()));
        quantityField.setText(Integer.toString(workpiece.getQuantity()));
        materials.stream()
                .filter(material -> material.getId().equals(workpiece.getMaterial().getId()))
                .findFirst().ifPresent(materialComboBox.getSelectionModel()::select);
        if (workpiece.getEdgeBanding() == null) {
            edgeBandingComboBox.getSelectionModel().clearSelection();
        } else {
            edgeBandings.stream()
                    .filter(edge -> edge.getId().equals(workpiece.getEdgeBanding().getId()))
                    .findFirst().ifPresent(edgeBandingComboBox.getSelectionModel()::select);
        }
        topEdgeCheckBox.setSelected(workpiece.isEdgeTop());
        bottomEdgeCheckBox.setSelected(workpiece.isEdgeBottom());
        leftEdgeCheckBox.setSelected(workpiece.isEdgeLeft());
        rightEdgeCheckBox.setSelected(workpiece.isEdgeRight());
        addWorkpieceButton.setText(text("pricing.update-workpiece"));
        cancelEditButton.setVisible(true);
        cancelEditButton.setManaged(true);
    }

    private void confirmDeleteWorkpiece(EstimateDraftWorkpieceEntity workpiece) {
        if (!UiTaskRunner.confirm(format("dialog.confirm-delete-workpiece", workpiece.getName()),
                messages)) {
            return;
        }
        if (workpiece.getProjectWorkpieceId() == null) {
            workpieceTable.getItems().remove(workpiece);
            saveDraft();
            updateSummary();
            statusLabel.setText(text("status.workpiece-deleted"));
            return;
        }
        taskRunner.execute(() -> {
            workpieceService.delete(workpiece.getProjectWorkpieceId());
            return null;
        }, messages, ignored -> {
            workpieceTable.getItems().remove(workpiece);
            saveDraft();
            updateSummary();
            statusLabel.setText(text("status.workpiece-deleted"));
        }, message -> statusLabel.setText(message));
    }

    private void updateSummary() {
        List<Workpiece> workpieces = workpieceTable.getItems().stream()
                .map(this::toCore)
                .toList();
        Double waste = nonNegative(wasteField);
        Double hours = nonNegative(hoursField);
        Double laborRate = nonNegative(laborRateField);
        Double machineRate = nonNegative(machineRateField);
        Double margin = nonNegative(marginField);
        if (waste == null || hours == null || laborRate == null
                || machineRate == null || margin == null || margin >= 100) {
            clearTotals();
            if (margin != null && margin >= 100) {
                statusLabel.setText(text("validation.margin-range"));
            }
            return;
        }
        try {
            long sheetCount = pricingEngine.calculateSheetCount(workpieces, waste);
            double materialCost = pricingEngine.calculateSheetConsumption(workpieces, waste);
            double edgeMeters = workpieces.stream()
                    .mapToDouble(Workpiece::getEdgeBandingLinearMeters).sum();
            double edgeCost = pricingEngine.calculateEdgeBandingCost(workpieces);
            double hardwareCost = hardwareUsages.stream()
                    .mapToDouble(usage -> usage.getHardware().getUnitPrice()
                            * usage.getQuantity())
                    .sum();
            double laborCost = hours * (laborRate + machineRate);
            double baseCost = pricingEngine.calculateBaseProjectCost(
                    workpieces, hardwareUsages, hours, laborRate, machineRate, waste);
            double suggestedPrice = pricingEngine.calculateTotalProjectCost(
                    workpieces, hardwareUsages, hours, laborRate, machineRate, margin, waste);
            sheetCountLabel.setText(Long.toString(sheetCount));
            edgeMetersLabel.setText(formatNumber(edgeMeters) + " m");
            materialCostLabel.setText(currency(materialCost));
            edgeCostLabel.setText(currency(edgeCost));
            hardwareCostLabel.setText(currency(hardwareCost));
            laborCostLabel.setText(currency(laborCost));
            baseCostLabel.setText(currency(baseCost));
            suggestedPriceLabel.setText(currency(suggestedPrice));
            profitLabel.setText(currency(suggestedPrice - baseCost));
        } catch (IllegalArgumentException exception) {
            clearTotals();
            statusLabel.setText(text("error.pricing-input"));
        }
    }

    private void renderHardwareSummary() {
        hardwareSummary.getChildren().clear();
        for (HardwareUsage usage : hardwareUsages) {
            Label description = new Label(usage.getHardware().getName() + " × "
                    + usage.getQuantity());
            HBox.setHgrow(description, javafx.scene.layout.Priority.ALWAYS);
            Button removeButton = new Button("×");
            removeButton.getStyleClass().add("icon-button");
            removeButton.setTooltip(new Tooltip(text("tooltip.remove-hardware")));
            removeButton.setOnAction(event -> {
                hardwareUsages.remove(usage);
                renderHardwareSummary();
                saveDraft();
                updateSummary();
            });
            HBox row = new HBox(6, description, removeButton);
            row.setAlignment(Pos.CENTER_LEFT);
            hardwareSummary.getChildren().add(row);
        }
    }

    private TableCell<EstimateDraftWorkpieceEntity, Void> createActionsCell(
            Consumer<EstimateDraftWorkpieceEntity> editAction,
            Consumer<EstimateDraftWorkpieceEntity> deleteAction
    ) {
        return new TableCell<>() {
            private final Button editButton = actionButton("tooltip.action-edit",
                    EDIT_ICON_PATH);
            private final Button deleteButton = actionButton("tooltip.action-delete",
                    DELETE_ICON_PATH);
            private final HBox buttons = new HBox(5, editButton, deleteButton);

            {
                setAlignment(Pos.CENTER);
                buttons.setAlignment(Pos.CENTER);
                editButton.getStyleClass().add("btn-edit");
                deleteButton.getStyleClass().add("btn-delete");
                editButton.setOnAction(event -> {
                    EstimateDraftWorkpieceEntity row = getTableRow().getItem();
                    if (row != null) {
                        editAction.accept(row);
                    }
                });
                deleteButton.setOnAction(event -> {
                    EstimateDraftWorkpieceEntity row = getTableRow().getItem();
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
        };
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

    private Workpiece toCore(EstimateDraftWorkpieceEntity entity) {
        Material material = toCore(entity.getMaterial());
        EdgeBanding edgeBanding = entity.getEdgeBanding() == null
                ? null : toCore(entity.getEdgeBanding());
        return new Workpiece(entity.getProjectWorkpieceId() == null
                ? entity.getId() : entity.getProjectWorkpieceId(), entity.getName(),
                entity.getLengthMm().doubleValue(), entity.getWidthMm().doubleValue(),
                entity.getQuantity(), material, edgeBanding, entity.isEdgeTop(),
                entity.isEdgeBottom(), entity.isEdgeLeft(), entity.isEdgeRight());
    }

    private EstimateDraftWorkpieceEntity toDraftWorkpiece(ProjectWorkpieceEntity entity) {
        return new EstimateDraftWorkpieceEntity(entity.getId(), entity.getName(),
                entity.getLengthMm(), entity.getWidthMm(), entity.getQuantity(),
                entity.getMaterial(), entity.getEdgeBanding(), entity.isEdgeTop(),
                entity.isEdgeBottom(), entity.isEdgeLeft(), entity.isEdgeRight());
    }

    private Material toCore(MaterialEntity entity) {
        return new Material(entity.getId(), entity.getName(), entity.getColor(),
                entity.getThicknessMm().doubleValue(), entity.getLengthMm().doubleValue(),
                entity.getWidthMm().doubleValue(), entity.getPricePerSheet().doubleValue());
    }

    private EdgeBanding toCore(EdgeBandingEntity entity) {
        return new EdgeBanding(entity.getId(), entity.getName(),
                entity.getWidthMm().doubleValue(), entity.getPricePerMeter().doubleValue());
    }

    private Hardware toCore(HardwareRow hardware) {
        return new Hardware(hardware.id(), hardware.name(), hardware.unit(),
                hardware.unitPrice().doubleValue());
    }

    private void clearForm() {
        editingWorkpieceId = null;
        workpieceForm.getStyleClass().remove("editing");
        editingIndicator.setVisible(false);
        editingIndicator.setManaged(false);
        nameField.clear();
        lengthField.clear();
        widthField.clear();
        quantityField.clear();
        materialComboBox.getSelectionModel().clearSelection();
        edgeBandingComboBox.getSelectionModel().clearSelection();
        topEdgeCheckBox.setSelected(false);
        bottomEdgeCheckBox.setSelected(false);
        leftEdgeCheckBox.setSelected(false);
        rightEdgeCheckBox.setSelected(false);
        addWorkpieceButton.setText(text("pricing.add-workpiece"));
        cancelEditButton.setVisible(false);
        cancelEditButton.setManaged(false);
    }

    private void clearTotals() {
        sheetCountLabel.setText("—");
        edgeMetersLabel.setText("—");
        materialCostLabel.setText("—");
        edgeCostLabel.setText("—");
        hardwareCostLabel.setText("—");
        laborCostLabel.setText("—");
        baseCostLabel.setText("—");
        suggestedPriceLabel.setText("—");
        profitLabel.setText("—");
    }

    private void showValidation(String key) {
        UiTaskRunner.showInformation(text("dialog.error-header"), text(key), messages);
    }

    private String materialName(MaterialEntity material) {
        return material.getName() + " · " + material.getColor() + " · "
                + formatDimension(material.getThicknessMm()) + " mm";
    }

    private String edgeBandingName(EdgeBandingEntity edgeBanding) {
        return edgeBanding.getName() + " · " + formatDimension(edgeBanding.getWidthMm())
                + " mm";
    }

    private String hardwareName(HardwareRow hardware) {
        return hardware.name() + " · " + currency(hardware.unitPrice())
                + " / " + hardware.unit();
    }

    private String currency(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(locale()).format(amount);
    }

    private String currency(double amount) {
        return NumberFormat.getCurrencyInstance(locale()).format(amount);
    }

    private String formatDimension(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String formatNumber(double value) {
        return NumberFormat.getNumberInstance(locale()).format(value);
    }

    private Double nonNegative(TextField field) {
        BigDecimal value = decimal(field);
        return value == null || value.signum() < 0 ? null : value.doubleValue();
    }

    private BigDecimal decimal(TextField field) {
        try {
            return new BigDecimal(field.getText().trim().replace(',', '.'));
        } catch (NumberFormatException | NullPointerException exception) {
            return null;
        }
    }

    private Integer integer(TextField field) {
        try {
            return Integer.valueOf(field.getText().trim());
        } catch (NumberFormatException | NullPointerException exception) {
            return null;
        }
    }

    private <T> ListCell<T> cell(StringConverter<T> converter) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : converter.toString(item));
            }
        };
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

    private static boolean sameProject(SalesProjectRow first, SalesProjectRow second) {
        return first == second || first != null && second != null
                && first.id().equals(second.id());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
