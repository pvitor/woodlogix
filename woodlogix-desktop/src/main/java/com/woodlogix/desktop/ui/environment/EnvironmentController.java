package com.woodlogix.desktop.ui.environment;

import com.woodlogix.core.enums.RoomType;
import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.desktop.service.CustomerService;
import com.woodlogix.desktop.service.EnvironmentRow;
import com.woodlogix.desktop.service.EnvironmentService;
import com.woodlogix.desktop.service.event.CustomerChangedEvent;
import com.woodlogix.desktop.service.event.EnvironmentChangedEvent;
import com.woodlogix.desktop.ui.RoomTypeLabels;
import com.woodlogix.desktop.ui.UiTaskRunner;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.application.Platform;
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
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.shape.SVGPath;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class EnvironmentController implements Initializable {
    private final CustomerService customerService;
    private final EnvironmentService environmentService;
    private final UiTaskRunner taskRunner;
    private final Map<RoomType, CheckBox> roomTypeCheckBoxes = new EnumMap<>(RoomType.class);
    private final Map<String, CheckBox> customRoomCheckBoxes = new LinkedHashMap<>();
    private final PauseTransition customerSearchDebounce =
            new PauseTransition(Duration.millis(250));
    private ResourceBundle messages;
    private String editingCustomerId;
    private boolean initialized;
    private boolean selectingCustomer;

    @FXML private Label statusLabel;
    @FXML private ComboBox<CustomerRow> customerComboBox;
    @FXML private FlowPane roomOptions;
    @FXML private TextField customRoomField;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private Label validationLabel;
    @FXML private TableView<EnvironmentCustomerRow> environmentTable;
    @FXML private TableColumn<EnvironmentCustomerRow, List<String>> typesColumn;
    @FXML private TableColumn<EnvironmentCustomerRow, String> customerColumn;
    @FXML private TableColumn<EnvironmentCustomerRow, Void> actionsColumn;

    public EnvironmentController(
            CustomerService customerService,
            EnvironmentService environmentService,
            UiTaskRunner taskRunner
    ) {
        this.customerService = Objects.requireNonNull(customerService);
        this.environmentService = Objects.requireNonNull(environmentService);
        this.taskRunner = Objects.requireNonNull(taskRunner);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        messages = resources;
        customerComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(CustomerRow customer) {
                return customer == null ? "" : customer.fullName();
            }

            @Override
            public CustomerRow fromString(String value) {
                return customerComboBox.getItems().stream()
                        .filter(customer -> customer.fullName().equalsIgnoreCase(value))
                        .findFirst().orElse(null);
            }
        });
        customerComboBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(CustomerRow customer, boolean empty) {
                super.updateItem(customer, empty);
                setText(empty || customer == null ? null : customer.fullName());
            }
        });
        customerComboBox.getEditor().textProperty().addListener(
                (observable, oldValue, newValue) -> onCustomerSearchChanged(newValue));
        customerSearchDebounce.setOnFinished(event -> searchCustomers());
        customerComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!selectingCustomer && !sameCustomer(oldValue, newValue)) {
                editingCustomerId = null;
                clearRoomSelection();
                removeCustomRoomCheckBoxes();
                customRoomField.clear();
                saveButton.setText(text("environment.register-button"));
                cancelButton.setVisible(false);
                cancelButton.setManaged(false);
            }
            validate();
        });
        customRoomField.textProperty().addListener((observable, oldValue, newValue) -> validate());
        typesColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().environmentTypes()));
        typesColumn.setCellFactory(column -> createEnvironmentTypesCell());
        customerColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().customerName()));
        actionsColumn.setCellFactory(column ->
                createActionsCell(this::edit, this::confirmDeleteEnvironments));
        environmentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        actionsColumn.setMinWidth(88.0);
        actionsColumn.setPrefWidth(100.0);
        initializeRoomTypes();
        loadCustomers();
        loadEnvironments();
        validate();
        initialized = true;
    }

    @FXML
    private void saveEnvironment() {
        CustomerRow customer = customerComboBox.getValue();
        List<RoomType> selectedTypes = selectedRoomTypes();
        String customRoom = blankToNull(customRoomField.getText());
        if (customer == null || (editingCustomerId == null
                && selectedTypes.isEmpty() && customRoom == null)) {
            UiTaskRunner.showInformation(text("dialog.error-header"),
                    text(customer == null ? "error.environment-required" : "error.environment-type"),
                    messages);
            return;
        }

        String customerId = customer.id();
        if (editingCustomerId == null) {
            taskRunner.execute(() -> environmentService.createForCustomer(
                            customerId, selectedTypes, customRoom),
                    messages, created -> {
                        clearForm();
                        statusLabel.setText(text(created.isEmpty()
                                ? "status.environment-no-new-rooms" : "status.environment-created"));
                        loadEnvironments();
                    }, message -> statusLabel.setText(message));
            return;
        }
        List<String> environmentTypes = selectedEnvironmentTypes();
        taskRunner.execute(() -> environmentService.saveOrUpdateCustomerEnvironments(
                        customerId, environmentTypes),
                messages, updated -> {
                    clearForm();
                    statusLabel.setText(text("status.environment-updated"));
                    loadEnvironments();
                }, message -> statusLabel.setText(message));
    }

    @FXML
    private void cancelEdit() {
        clearForm();
    }

    @TransactionalEventListener
    public void onCustomerChanged(CustomerChangedEvent event) {
        if (initialized) {
            Platform.runLater(this::loadCustomers);
        }
    }

    @TransactionalEventListener
    public void onEnvironmentChanged(EnvironmentChangedEvent event) {
        if (initialized) {
            Platform.runLater(this::loadEnvironments);
        }
    }

    private void initializeRoomTypes() {
        for (RoomType type : RoomType.values()) {
            CheckBox checkBox = new CheckBox(text(RoomTypeLabels.messageKey(type)));
            checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> validate());
            roomTypeCheckBoxes.put(type, checkBox);
            roomOptions.getChildren().add(checkBox);
        }
    }

    private void searchCustomers() {
        String query = customerComboBox.getEditor().getText();
        taskRunner.execute(() -> customerService.findByName(query), messages, customers -> {
            if (Objects.equals(query, customerComboBox.getEditor().getText())) {
                customerComboBox.setItems(FXCollections.observableArrayList(customers));
                if (!customers.isEmpty()) {
                    customerComboBox.show();
                }
            }
        }, message -> statusLabel.setText(message));
    }

    private void loadCustomers() {
        String query = customerComboBox.getEditor().getText();
        String selectedCustomerId = customerComboBox.getValue() == null
                ? null : customerComboBox.getValue().id();
        taskRunner.execute(() -> customerService.findByName(query), messages, customers -> {
            if (Objects.equals(query, customerComboBox.getEditor().getText())) {
                customerComboBox.setItems(FXCollections.observableArrayList(customers));
                if (selectedCustomerId != null) {
                    customers.stream()
                            .filter(customer -> customer.id().equals(selectedCustomerId))
                            .findFirst()
                            .ifPresent(customer -> selectCustomer(customer, customer.fullName()));
                }
            }
        }, message -> statusLabel.setText(message));
    }

    private void loadEnvironments() {
        taskRunner.execute(environmentService::findAll, messages, environments -> {
            Map<String, List<EnvironmentRow>> grouped = environments.stream()
                    .collect(Collectors.groupingBy(EnvironmentRow::customerId,
                            LinkedHashMap::new, Collectors.toList()));
            List<EnvironmentCustomerRow> rows = grouped.values().stream()
                    .map(customerEnvironments -> new EnvironmentCustomerRow(
                            customerEnvironments.get(0).customerId(),
                            customerEnvironments.get(0).customerName(),
                            customerEnvironments.stream().map(EnvironmentRow::type).toList()))
                    .toList();
            environmentTable.setItems(FXCollections.observableArrayList(rows));
            statusLabel.setText(text("status.synced"));
        }, message -> statusLabel.setText(message));
    }

    private void edit(EnvironmentCustomerRow customerEnvironments) {
        taskRunner.execute(customerService::findAll, messages, customers -> {
            customerComboBox.setItems(FXCollections.observableArrayList(customers));
            CustomerRow customer = customers.stream()
                    .filter(candidate -> candidate.id().equals(customerEnvironments.customerId()))
                    .findFirst().orElse(null);
            if (customer != null) {
                selectCustomer(customer, customer.fullName());
                editingCustomerId = customer.id();
            }
            clearRoomSelection();
            removeCustomRoomCheckBoxes();
            for (String roomType : customerEnvironments.environmentTypes()) {
                RoomTypeLabels.find(roomType, messages).ifPresentOrElse(type ->
                                roomTypeCheckBoxes.get(type).setSelected(true),
                        () -> addCustomRoomCheckBox(roomType, true));
            }
            saveButton.setText(text("environment.update-button"));
            cancelButton.setVisible(true);
            cancelButton.setManaged(true);
            validate();
        }, message -> statusLabel.setText(message));
    }

    private TableCell<EnvironmentCustomerRow, List<String>> createEnvironmentTypesCell() {
        return new TableCell<>() {
            private final FlowPane chips = new FlowPane(6, 6);

            {
                chips.getStyleClass().add("environment-chips");
                chips.setMaxWidth(Double.MAX_VALUE);
            }

            @Override
            protected void updateItem(List<String> roomTypes, boolean empty) {
                super.updateItem(roomTypes, empty);
                chips.getChildren().clear();
                if (empty || roomTypes == null) {
                    setGraphic(null);
                    return;
                }
                roomTypes.stream()
                        .map(type -> RoomTypeLabels.localized(type, messages))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .map(this::createChip)
                        .forEach(chips.getChildren()::add);
                setGraphic(chips);
            }

            private Label createChip(String roomType) {
                Label chip = new Label(roomType);
                chip.getStyleClass().add("environment-chip");
                chip.setWrapText(true);
                return chip;
            }
        };
    }

    private void confirmDeleteEnvironments(EnvironmentCustomerRow customer) {
        if (!UiTaskRunner.confirm(format("dialog.confirm-delete-customer-environments",
                customer.customerName()), messages)) {
            return;
        }
        taskRunner.execute(() -> {
            environmentService.deleteAllByCustomer(customer.customerId());
            return null;
        }, messages, ignored -> {
            if (customer.customerId().equals(editingCustomerId)) {
                clearForm();
            }
            statusLabel.setText(text("status.customer-environments-deleted"));
            loadEnvironments();
        }, message -> statusLabel.setText(message));
    }

    private TableCell<EnvironmentCustomerRow, Void> createActionsCell(
            Consumer<EnvironmentCustomerRow> editAction,
            Consumer<EnvironmentCustomerRow> deleteAction
    ) {
        return new TableCell<>() {
            private final Button manageButton = actionButton(
                    "tooltip.action-edit",
                    "M3 11 L10.8 3.2 L13.8 6.2 L6 14 H3 Z M9.8 4.2 L12.8 7.2");
            private final Button deleteButton = actionButton(
                    "tooltip.action-delete",
                    "M2 4 H14 M5 4 V2 H11 V4 M4 5 L5 14 H11 L12 5 M6.5 7 V12 M9.5 7 V12");
            private final HBox buttons = new HBox(6, manageButton, deleteButton);

            {
                setAlignment(Pos.CENTER);
                manageButton.getStyleClass().add("btn-edit");
                deleteButton.getStyleClass().add("btn-delete");
                buttons.setAlignment(Pos.CENTER);
                manageButton.setOnAction(event -> {
                    EnvironmentCustomerRow row = getTableRow().getItem();
                    if (row != null) {
                        editAction.accept(row);
                    }
                });
                deleteButton.setOnAction(event -> {
                    EnvironmentCustomerRow row = getTableRow().getItem();
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

    private List<RoomType> selectedRoomTypes() {
        return roomTypeCheckBoxes.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .toList();
    }

    private void validate() {
        validationLabel.setText("");
        validationLabel.setVisible(false);
        validationLabel.setManaged(false);
        saveButton.setDisable(customerComboBox.getValue() == null
                || (editingCustomerId == null && selectedEnvironmentTypes().isEmpty()));
    }

    private void clearRoomSelection() {
        roomTypeCheckBoxes.values().forEach(checkBox -> checkBox.setSelected(false));
        customRoomCheckBoxes.values().forEach(checkBox -> checkBox.setSelected(false));
    }

    private void clearForm() {
        customerSearchDebounce.stop();
        editingCustomerId = null;
        clearRoomSelection();
        removeCustomRoomCheckBoxes();
        customRoomField.clear();
        customerComboBox.getSelectionModel().clearSelection();
        customerComboBox.getEditor().clear();
        saveButton.setText(text("environment.register-button"));
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        validate();
    }

    private String text(String key) {
        return messages.getString(key);
    }

    private String format(String key, Object... arguments) {
        return java.text.MessageFormat.format(text(key), arguments);
    }

    private void onCustomerSearchChanged(String query) {
        CustomerRow selectedCustomer = customerComboBox.getValue();
        if (selectingCustomer || (selectedCustomer != null
                && selectedCustomer.fullName().equals(query))) {
            return;
        }
        customerComboBox.getSelectionModel().clearSelection();
        if (query == null || query.isBlank()) {
            customerSearchDebounce.stop();
            return;
        }
        customerSearchDebounce.playFromStart();
    }

    private void selectCustomer(CustomerRow customer, String editorText) {
        selectingCustomer = true;
        customerComboBox.getSelectionModel().select(customer);
        customerComboBox.getEditor().setText(editorText);
        selectingCustomer = false;
    }

    private void addCustomRoomCheckBox(String roomType, boolean selected) {
        CheckBox checkBox = customRoomCheckBoxes.computeIfAbsent(roomType, type -> {
            CheckBox customCheckBox = new CheckBox(type);
            customCheckBox.selectedProperty().addListener(
                    (observable, oldValue, newValue) -> validate());
            roomOptions.getChildren().add(customCheckBox);
            return customCheckBox;
        });
        checkBox.setSelected(selected);
    }

    private void removeCustomRoomCheckBoxes() {
        roomOptions.getChildren().removeAll(customRoomCheckBoxes.values());
        customRoomCheckBoxes.clear();
    }

    private List<String> selectedEnvironmentTypes() {
        List<String> selected = new ArrayList<>(selectedRoomTypes().stream()
                .map(Enum::name)
                .toList());
        customRoomCheckBoxes.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .forEach(selected::add);
        String newCustomRoom = blankToNull(customRoomField.getText());
        if (newCustomRoom != null) {
            selected.add(newCustomRoom);
        }
        return selected;
    }

    private static boolean sameCustomer(CustomerRow first, CustomerRow second) {
        return first == second || first != null && second != null
                && first.id().equals(second.id());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
