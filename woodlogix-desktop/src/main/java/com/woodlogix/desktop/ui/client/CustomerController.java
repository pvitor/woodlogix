package com.woodlogix.desktop.ui.client;

import com.woodlogix.desktop.service.CustomerData;
import com.woodlogix.desktop.service.CustomerRow;
import com.woodlogix.desktop.service.CustomerService;
import com.woodlogix.desktop.service.event.CustomerChangedEvent;
import com.woodlogix.desktop.ui.UiTaskRunner;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import javafx.util.Duration;

import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

@Component
public class CustomerController implements Initializable {
    private final CustomerService customerService;
    private final UiTaskRunner taskRunner;
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(250));
    private ResourceBundle messages;
    private String editingCustomerId;
    private boolean initialized;

    @FXML private Label statusLabel;
    @FXML private TextField searchField;
    @FXML private TextField nameField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextField addressField;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private Label validationLabel;
    @FXML private TableView<CustomerRow> customerTable;
    @FXML private TableColumn<CustomerRow, String> nameColumn;
    @FXML private TableColumn<CustomerRow, String> phoneColumn;
    @FXML private TableColumn<CustomerRow, String> emailColumn;
    @FXML private TableColumn<CustomerRow, String> addressColumn;
    @FXML private TableColumn<CustomerRow, Void> actionsColumn;

    public CustomerController(CustomerService customerService, UiTaskRunner taskRunner) {
        this.customerService = Objects.requireNonNull(customerService);
        this.taskRunner = Objects.requireNonNull(taskRunner);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        messages = resources;
        searchField.textProperty().addListener((observable, oldValue, newValue) ->
                searchDebounce.playFromStart());
        searchDebounce.setOnFinished(event -> searchCustomers());
        phoneField.setTextFormatter(new javafx.scene.control.TextFormatter<>(phoneFilter()));
        nameField.textProperty().addListener((observable, oldValue, newValue) -> validate());
        phoneField.textProperty().addListener((observable, oldValue, newValue) -> validate());
        emailField.textProperty().addListener((observable, oldValue, newValue) -> validate());
        nameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().fullName()));
        phoneColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().phone()));
        emailColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().email()));
        addressColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().address()));
        actionsColumn.setCellFactory(column -> createActionsCell(
                this::edit, this::confirmDelete));
        validate();
        initialized = true;
        loadCustomers();
    }

    @FXML
    private void saveCustomer() {
        String customerId = editingCustomerId;
        CustomerData data = new CustomerData(nameField.getText().trim(),
                blankToNull(phoneField.getText()), blankToNull(emailField.getText()),
                blankToNull(addressField.getText()));
        taskRunner.execute(() -> customerId == null
                        ? customerService.create(data) : customerService.update(customerId, data),
                messages, customer -> {
                    clearForm();
                    statusLabel.setText(text(customerId == null
                            ? "status.customer-created" : "status.customer-updated"));
                    searchCustomers();
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

    private void loadCustomers() {
        String query = searchField.getText();
        taskRunner.execute(() -> customerService.search(query), messages, customers -> {
            if (Objects.equals(query, searchField.getText())) {
                customerTable.setItems(FXCollections.observableArrayList(customers));
                statusLabel.setText(text("status.synced"));
            }
        }, message -> statusLabel.setText(message));
    }

    private void searchCustomers() {
        loadCustomers();
    }

    private void edit(CustomerRow customer) {
        editingCustomerId = customer.id();
        nameField.setText(customer.fullName());
        phoneField.setText(customer.phone());
        emailField.setText(customer.email());
        addressField.setText(customer.address());
        saveButton.setText(text("customer.update-button"));
        cancelButton.setVisible(true);
        cancelButton.setManaged(true);
        nameField.requestFocus();
    }

    private void confirmDelete(CustomerRow customer) {
        if (!UiTaskRunner.confirm(format("dialog.confirm-delete-customer", customer.fullName()),
                messages)) {
            return;
        }
        taskRunner.execute(() -> {
            customerService.delete(customer.id());
            return null;
        }, messages, ignored -> {
            if (customer.id().equals(editingCustomerId)) {
                clearForm();
            }
            statusLabel.setText(text("status.customer-deleted"));
            searchCustomers();
            UiTaskRunner.showInformation(text("dialog.deleted-header"),
                    text("status.customer-deleted"), messages);
        }, message -> statusLabel.setText(message));
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

    private void validate() {
        String phone = phoneField.getText();
        String email = emailField.getText();
        String message = "";
        if (!isBlank(phone) && !phone.matches("[0-9()\\-]{6,20}")
                || !isBlank(phone) && phone.chars().filter(Character::isDigit).count() < 6) {
            message = text("validation.phone");
        }
        if (!isBlank(email) && !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            message = message.isEmpty() ? text("validation.email")
                    : message + " " + text("validation.email");
        }
        validationLabel.setText(message);
        validationLabel.setVisible(!message.isEmpty());
        validationLabel.setManaged(!message.isEmpty());
        saveButton.setDisable(isBlank(nameField.getText())
                || (!isBlank(phone) && (!phone.matches("[0-9()\\-]{6,20}")
                || phone.chars().filter(Character::isDigit).count() < 6))
                || (!isBlank(email) && !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")));
    }

    private void clearForm() {
        editingCustomerId = null;
        nameField.clear();
        phoneField.clear();
        emailField.clear();
        addressField.clear();
        saveButton.setText(text("customer.register-button"));
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
    }

    private String text(String key) {
        return messages.getString(key);
    }

    private String format(String key, Object... args) {
        return java.text.MessageFormat.format(text(key), args);
    }

    private static UnaryOperator<javafx.scene.control.TextFormatter.Change> phoneFilter() {
        return change -> change.getControlNewText().matches("[0-9()\\-]{0,20}")
                ? change : null;
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
