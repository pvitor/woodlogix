package com.woodlogix.desktop.ui.furniture;

import com.woodlogix.desktop.persistence.entity.FurnitureItemEntity;
import com.woodlogix.desktop.service.CustomerRow;
import com.woodlogix.desktop.service.CustomerService;
import com.woodlogix.desktop.service.EnvironmentRow;
import com.woodlogix.desktop.service.EnvironmentService;
import com.woodlogix.desktop.service.FurnitureItemData;
import com.woodlogix.desktop.service.FurnitureItemService;
import com.woodlogix.desktop.ui.UiTaskRunner;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

@Component
public class FurnitureItemController implements Initializable {
    private final CustomerService customerService;
    private final EnvironmentService environmentService;
    private final FurnitureItemService furnitureItemService;
    private final UiTaskRunner taskRunner;
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(250));
    private ResourceBundle messages;
    private String editingItemId;
    @FXML private Label statusLabel;
    @FXML private Label validationLabel;
    @FXML private ComboBox<CustomerRow> customerComboBox;
    @FXML private ComboBox<EnvironmentRow> environmentComboBox;
    @FXML private TextField nameField;
    @FXML private TextField typeField;
    @FXML private TextField woodField;
    @FXML private TextField lengthField;
    @FXML private TextField heightField;
    @FXML private TextField depthField;
    @FXML private TextField searchField;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private TableView<FurnitureItemEntity> itemTable;
    @FXML private TableColumn<FurnitureItemEntity, String> nameColumn;
    @FXML private TableColumn<FurnitureItemEntity, String> typeColumn;
    @FXML private TableColumn<FurnitureItemEntity, String> woodColumn;
    @FXML private TableColumn<FurnitureItemEntity, String> lengthColumn;
    @FXML private TableColumn<FurnitureItemEntity, String> heightColumn;
    @FXML private TableColumn<FurnitureItemEntity, String> depthColumn;
    @FXML private TableColumn<FurnitureItemEntity, String> environmentColumn;
    @FXML private TableColumn<FurnitureItemEntity, Void> actionsColumn;

    public FurnitureItemController(
            CustomerService customerService,
            EnvironmentService environmentService,
            FurnitureItemService furnitureItemService,
            UiTaskRunner taskRunner
    ) {
        this.customerService = Objects.requireNonNull(customerService);
        this.environmentService = Objects.requireNonNull(environmentService);
        this.furnitureItemService = Objects.requireNonNull(furnitureItemService);
        this.taskRunner = Objects.requireNonNull(taskRunner);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        messages = resources;
        searchField.textProperty().addListener((observable, oldValue, newValue) ->
                searchDebounce.playFromStart());
        searchDebounce.setOnFinished(event -> loadItems());
        customerComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || !Objects.equals(
                    oldValue == null ? null : oldValue.id(), newValue.id())) {
                clearForm();
                loadEnvironments(newValue == null ? null : newValue.id(), null);
                loadItems();
            }
        });
        environmentComboBox.setCellFactory(list -> environmentCell());
        environmentComboBox.setButtonCell(environmentCell());
        customerComboBox.setCellFactory(list -> customerCell());
        customerComboBox.setButtonCell(customerCell());
        configureDimensionFields();
        nameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getName()));
        typeColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getFurnitureType()));
        woodColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getWoodType()));
        lengthColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatDimension(cell.getValue().getLengthMm())));
        heightColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatDimension(cell.getValue().getHeightMm())));
        depthColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatDimension(cell.getValue().getDepthMm())));
        environmentColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getEnvironment() == null
                        ? "" : cell.getValue().getEnvironment().getType()));
        actionsColumn.setCellFactory(column -> createActionsCell(
                this::edit, this::confirmDelete));
        loadCustomers();
    }

    @FXML
    private void saveItem() {
        CustomerRow customer = customerComboBox.getValue();
        BigDecimal length = parseDimension(lengthField.getText());
        BigDecimal height = parseDimension(heightField.getText());
        BigDecimal depth = parseDimension(depthField.getText());
        if (customer == null || isBlank(nameField.getText()) || isBlank(typeField.getText())
                || isBlank(woodField.getText()) || length == null || height == null
                || depth == null) {
            showValidation(text("validation.furniture-item-required"));
            return;
        }
        EnvironmentRow environment = environmentComboBox.getValue();
        FurnitureItemData data = new FurnitureItemData(
                customer.id(), environment == null ? null : environment.id(),
                nameField.getText().trim(), typeField.getText().trim(), woodField.getText().trim(),
                length, height, depth
        );
        String itemId = editingItemId;
        taskRunner.execute(() -> itemId == null
                        ? furnitureItemService.create(data)
                        : furnitureItemService.update(itemId, data),
                messages, saved -> {
                    clearForm();
                    statusLabel.setText(text(itemId == null
                            ? "status.furniture-item-created"
                            : "status.furniture-item-updated"));
                    loadItems();
                }, message -> statusLabel.setText(message));
    }

    @FXML
    private void cancelEdit() {
        clearForm();
    }

    private void loadCustomers() {
        taskRunner.execute(customerService::findAll, messages, customers -> {
            customerComboBox.setItems(FXCollections.observableArrayList(customers));
            if (!customers.isEmpty()) {
                customerComboBox.getSelectionModel().selectFirst();
            } else {
                itemTable.getItems().clear();
                statusLabel.setText(text("status.no-customers"));
            }
        }, message -> statusLabel.setText(message));
    }

    private void loadEnvironments(String customerId, String selectedEnvironmentId) {
        if (customerId == null) {
            environmentComboBox.setItems(FXCollections.observableArrayList());
            return;
        }
        taskRunner.execute(() -> environmentService.findByCustomer(customerId), messages,
                environments -> {
                    if (customerComboBox.getValue() == null
                            || !customerId.equals(customerComboBox.getValue().id())) {
                        return;
                    }
                    environmentComboBox.setItems(FXCollections.observableArrayList(environments));
                    if (selectedEnvironmentId != null) {
                        environments.stream()
                                .filter(item -> item.id().equals(selectedEnvironmentId))
                                .findFirst().ifPresent(environmentComboBox.getSelectionModel()::select);
                    }
                }, message -> statusLabel.setText(message));
    }

    private void loadItems() {
        CustomerRow customer = customerComboBox.getValue();
        if (customer == null) {
            itemTable.getItems().clear();
            return;
        }
        String customerId = customer.id();
        String query = searchField.getText();
        taskRunner.execute(() -> furnitureItemService.searchByCustomer(customerId, query),
                messages, items -> {
                    if (customerComboBox.getValue() != null
                            && customerId.equals(customerComboBox.getValue().id())
                            && Objects.equals(query, searchField.getText())) {
                        itemTable.setItems(FXCollections.observableArrayList(items));
                        statusLabel.setText(text("status.synced"));
                    }
                }, message -> statusLabel.setText(message));
    }

    private void edit(FurnitureItemEntity item) {
        CustomerRow customer = customerComboBox.getItems().stream()
                .filter(row -> row.id().equals(item.getCustomer().getId()))
                .findFirst().orElse(null);
        if (customer == null) {
            return;
        }
        customerComboBox.getSelectionModel().select(customer);
        editingItemId = item.getId();
        String environmentId =
                item.getEnvironment() == null ? null : item.getEnvironment().getId();
        loadEnvironments(customer.id(), environmentId);
        nameField.setText(item.getName());
        typeField.setText(item.getFurnitureType());
        woodField.setText(item.getWoodType());
        lengthField.setText(formatDimension(item.getLengthMm()));
        heightField.setText(formatDimension(item.getHeightMm()));
        depthField.setText(formatDimension(item.getDepthMm()));
        saveButton.setText(text("furniture-items.update-button"));
        cancelButton.setVisible(true);
        cancelButton.setManaged(true);
        nameField.requestFocus();
    }

    private void confirmDelete(FurnitureItemEntity item) {
        if (!UiTaskRunner.confirm(java.text.MessageFormat.format(
                text("dialog.confirm-delete-furniture-item"), item.getName()), messages)) {
            return;
        }
        taskRunner.execute(() -> {
            furnitureItemService.delete(item.getId());
            return null;
        }, messages, ignored -> {
            if (item.getId().equals(editingItemId)) {
                clearForm();
            }
            statusLabel.setText(text("status.furniture-item-deleted"));
            loadItems();
        }, message -> statusLabel.setText(message));
    }

    private void configureDimensionFields() {
        UnaryOperator<TextFormatter.Change> filter = change ->
                change.getControlNewText().matches("[0-9]{0,8}([.,][0-9]{0,2})?")
                        ? change : null;
        for (TextField field : List.of(lengthField, heightField, depthField)) {
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

    private ListCell<CustomerRow> customerCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(CustomerRow item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.fullName());
            }
        };
    }

    private ListCell<EnvironmentRow> environmentCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(EnvironmentRow item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.type());
            }
        };
    }

    private void clearForm() {
        editingItemId = null;
        nameField.clear();
        typeField.clear();
        woodField.clear();
        lengthField.clear();
        heightField.clear();
        depthField.clear();
        environmentComboBox.getSelectionModel().clearSelection();
        saveButton.setText(text("furniture-items.register-button"));
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
