package com.woodlogix.desktop.ui.furniture;

import com.woodlogix.core.enums.MaterialType;
import com.woodlogix.core.enums.VisibleEdge;
import com.woodlogix.desktop.persistence.entity.FurniturePartEntity;
import com.woodlogix.desktop.service.EnvironmentRow;
import com.woodlogix.desktop.service.EnvironmentService;
import com.woodlogix.desktop.service.FurniturePartData;
import com.woodlogix.desktop.service.FurniturePartService;
import com.woodlogix.desktop.ui.UiTaskRunner;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
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
import javafx.scene.control.Tooltip;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URL;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@Component
public class FurniturePartController implements Initializable {
    private final EnvironmentService environmentService;
    private final FurniturePartService furniturePartService;
    private final UiTaskRunner taskRunner;
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(250));
    private final Map<VisibleEdge, CheckBox> visibleEdgeCheckBoxes =
            new EnumMap<>(VisibleEdge.class);
    private ResourceBundle messages;
    private String editingPartId;

    @FXML private Label statusLabel;
    @FXML private ComboBox<EnvironmentRow> environmentComboBox;
    @FXML private ComboBox<MaterialType> materialComboBox;
    @FXML private TextField nameField;
    @FXML private TextField lengthField;
    @FXML private TextField widthField;
    @FXML private TextField thicknessField;
    @FXML private TextField searchField;
    @FXML private FlowPane visibleEdgesPane;
    @FXML private Label validationLabel;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private TableView<FurniturePartEntity> partTable;
    @FXML private TableColumn<FurniturePartEntity, String> nameColumn;
    @FXML private TableColumn<FurniturePartEntity, String> lengthColumn;
    @FXML private TableColumn<FurniturePartEntity, String> widthColumn;
    @FXML private TableColumn<FurniturePartEntity, String> thicknessColumn;
    @FXML private TableColumn<FurniturePartEntity, String> materialColumn;
    @FXML private TableColumn<FurniturePartEntity, String> edgesColumn;
    @FXML private TableColumn<FurniturePartEntity, Void> actionsColumn;

    public FurniturePartController(
            EnvironmentService environmentService,
            FurniturePartService furniturePartService,
            UiTaskRunner taskRunner
    ) {
        this.environmentService = Objects.requireNonNull(environmentService);
        this.furniturePartService = Objects.requireNonNull(furniturePartService);
        this.taskRunner = Objects.requireNonNull(taskRunner);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        messages = resources;
        searchField.textProperty().addListener((observable, oldValue, newValue) ->
                searchDebounce.playFromStart());
        searchDebounce.setOnFinished(event -> loadParts());
        environmentComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != newValue) {
                clearForm();
            }
            loadParts();
        });
        materialComboBox.setItems(FXCollections.observableArrayList(MaterialType.values()));
        configureMaterialLabels();
        configureDimensions(lengthField, widthField, thicknessField);
        initializeEdges();
        nameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getName()));
        lengthColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatDimension(cell.getValue().getLengthMm())));
        widthColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatDimension(cell.getValue().getWidthMm())));
        thicknessColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatDimension(cell.getValue().getThicknessMm())));
        materialColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                text("material." + cell.getValue().getMaterial().name()
                        .toLowerCase(Locale.ROOT))));
        edgesColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                formatEdges(cell.getValue().getVisibleEdges())));
        actionsColumn.setCellFactory(column -> createActionsCell(
                this::edit, this::confirmDelete));
        environmentComboBox.setButtonCell(environmentCell());
        environmentComboBox.setCellFactory(list -> environmentCell());
        loadEnvironments();
    }

    @FXML
    private void savePart() {
        EnvironmentRow environment = environmentComboBox.getValue();
        BigDecimal length = parseDimension(lengthField.getText());
        BigDecimal width = parseDimension(widthField.getText());
        BigDecimal thickness = parseDimension(thicknessField.getText());
        MaterialType material = materialComboBox.getValue();
        if (environment == null || isBlank(nameField.getText()) || length == null
                || width == null || thickness == null || material == null) {
            showValidation(text("validation.part-required"));
            return;
        }

        FurniturePartData data = new FurniturePartData(environment.id(), nameField.getText().trim(),
                length, width, thickness, material, selectedEdges());
        String partId = editingPartId;
        taskRunner.execute(() -> partId == null
                        ? furniturePartService.create(data)
                        : furniturePartService.update(partId, data),
                messages, saved -> {
                    clearForm();
                    statusLabel.setText(text(partId == null
                            ? "status.part-created" : "status.part-updated"));
                    loadParts();
                }, message -> statusLabel.setText(message));
    }

    @FXML
    private void cancelEdit() {
        clearForm();
    }

    private void loadEnvironments() {
        taskRunner.execute(environmentService::findAll, messages, environments -> {
            String selectedId = environmentComboBox.getValue() == null
                    ? null : environmentComboBox.getValue().id();
            environmentComboBox.setItems(FXCollections.observableArrayList(environments));
            if (selectedId != null) {
                environments.stream().filter(item -> item.id().equals(selectedId)).findFirst()
                        .ifPresent(environmentComboBox.getSelectionModel()::select);
            }
            if (selectedId == null && !environments.isEmpty()) {
                environmentComboBox.getSelectionModel().selectFirst();
            }
            if (environments.isEmpty()) {
                partTable.getItems().clear();
                statusLabel.setText(text("status.no-environments"));
            }
        }, message -> statusLabel.setText(message));
    }

    private void loadParts() {
        EnvironmentRow environment = environmentComboBox.getValue();
        if (environment == null) {
            partTable.getItems().clear();
            return;
        }
        String query = searchField.getText();
        String environmentId = environment.id();
        taskRunner.execute(() -> furniturePartService.searchByEnvironment(environmentId, query),
                messages, parts -> {
                    if (environmentComboBox.getValue() != null
                            && environmentId.equals(environmentComboBox.getValue().id())
                            && Objects.equals(query, searchField.getText())) {
                        partTable.setItems(FXCollections.observableArrayList(parts));
                        statusLabel.setText(text("status.synced"));
                    }
                }, message -> statusLabel.setText(message));
    }

    private void edit(FurniturePartEntity part) {
        environmentComboBox.getItems().stream()
                .filter(environment -> environment.id()
                        .equals(part.getProjectEnvironment().getId()))
                .findFirst().ifPresent(environmentComboBox.getSelectionModel()::select);
        editingPartId = part.getId();
        nameField.setText(part.getName());
        lengthField.setText(formatDimension(part.getLengthMm()));
        widthField.setText(formatDimension(part.getWidthMm()));
        thicknessField.setText(formatDimension(part.getThicknessMm()));
        materialComboBox.getSelectionModel().select(part.getMaterial());
        visibleEdgeCheckBoxes.forEach((edge, checkBox) ->
                checkBox.setSelected(part.getVisibleEdges().contains(edge)));
        saveButton.setText(text("parts.update-button"));
        cancelButton.setVisible(true);
        cancelButton.setManaged(true);
        nameField.requestFocus();
    }

    private void confirmDelete(FurniturePartEntity part) {
        if (!UiTaskRunner.confirm(java.text.MessageFormat.format(
                text("dialog.confirm-delete-part"), part.getName()), messages)) {
            return;
        }
        taskRunner.execute(() -> {
            furniturePartService.delete(part.getId());
            return null;
        }, messages, ignored -> {
            if (part.getId().equals(editingPartId)) {
                clearForm();
            }
            statusLabel.setText(text("status.part-deleted"));
            loadParts();
        }, message -> statusLabel.setText(message));
    }

    private void initializeEdges() {
        for (VisibleEdge edge : VisibleEdge.values()) {
            CheckBox checkBox = new CheckBox(text("edge." + edge.name().toLowerCase()));
            visibleEdgeCheckBoxes.put(edge, checkBox);
            visibleEdgesPane.getChildren().add(checkBox);
        }
    }

    private void configureMaterialLabels() {
        StringConverter<MaterialType> converter = new StringConverter<>() {
            @Override
            public String toString(MaterialType material) {
                return material == null ? "" : text(
                        "material." + material.name().toLowerCase(Locale.ROOT));
            }

            @Override
            public MaterialType fromString(String value) {
                return null;
            }
        };
        materialComboBox.setConverter(converter);
        materialComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(MaterialType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : converter.toString(item));
            }
        });
        materialComboBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(MaterialType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : converter.toString(item));
            }
        });
    }

    private void configureDimensions(TextField... fields) {
        UnaryOperator<TextFormatter.Change> filter = change ->
                change.getControlNewText().matches("[0-9]{0,8}([.,][0-9]{0,2})?")
                        ? change : null;
        for (TextField field : fields) {
            field.setTextFormatter(new TextFormatter<>(filter));
        }
    }

    private <T> TableCell<T, Void> createActionsCell(
            Consumer<T> editAction, Consumer<T> deleteAction
    ) {
        return new TableCell<>() {
            private final Button editButton = new Button("✏");
            private final Button deleteButton = new Button("🗑");
            private final HBox buttons = new HBox(6, editButton, deleteButton);
            {
                setAlignment(Pos.CENTER);
                editButton.getStyleClass().add("btn-edit");
                deleteButton.getStyleClass().add("btn-delete");
                editButton.setTooltip(new Tooltip(text("tooltip.action-edit")));
                deleteButton.setTooltip(new Tooltip(text("tooltip.action-delete")));
                buttons.setAlignment(Pos.CENTER);
                editButton.setOnAction(event -> {
                    T row = getTableRow().getItem();
                    if (row != null) editAction.accept(row);
                });
                deleteButton.setOnAction(event -> {
                    T row = getTableRow().getItem();
                    if (row != null) deleteAction.accept(row);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : buttons);
            }
        };
    }

    private ListCell<EnvironmentRow> environmentCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(EnvironmentRow item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        };
    }

    private Set<VisibleEdge> selectedEdges() {
        return visibleEdgeCheckBoxes.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String formatEdges(Set<VisibleEdge> edges) {
        return edges.stream()
                .sorted()
                .map(edge -> text("edge." + edge.name().toLowerCase()))
                .collect(Collectors.joining(", "));
    }

    private void clearForm() {
        editingPartId = null;
        nameField.clear();
        lengthField.clear();
        widthField.clear();
        thicknessField.clear();
        materialComboBox.getSelectionModel().clearSelection();
        visibleEdgeCheckBoxes.values().forEach(checkBox -> checkBox.setSelected(false));
        saveButton.setText(text("parts.register-button"));
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        showValidation("");
    }

    private void showValidation(String message) {
        validationLabel.setText(message);
        validationLabel.setVisible(!message.isEmpty());
        validationLabel.setManaged(!message.isEmpty());
    }

    private String formatDimension(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal parseDimension(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            BigDecimal parsed = new BigDecimal(value.trim().replace(',', '.'));
            return parsed.signum() > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String text(String key) {
        return messages.getString(key);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
