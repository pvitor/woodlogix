package com.woodlogix.desktop.ui.kanban;

import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.desktop.config.ConfigLoader;
import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.desktop.service.CustomerService;
import com.woodlogix.desktop.service.EnvironmentRow;
import com.woodlogix.desktop.service.ProposalPdfService;
import com.woodlogix.desktop.service.SalesPipelineService;
import com.woodlogix.desktop.service.SalesProjectData;
import com.woodlogix.desktop.service.SalesProjectRow;
import com.woodlogix.desktop.service.event.CustomerChangedEvent;
import com.woodlogix.desktop.service.event.EnvironmentChangedEvent;
import com.woodlogix.desktop.ui.RoomTypeLabels;
import com.woodlogix.desktop.ui.UiTaskRunner;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.UnaryOperator;

@Component
public class KanbanController implements Initializable {
    private static final DataFormat PROJECT_ID_FORMAT =
            new DataFormat("application/x-woodlogix-project-id");
    private final CustomerService customerService;
    private final SalesPipelineService salesPipelineService;
    private final ProposalPdfService proposalPdfService;
    private final UiTaskRunner taskRunner;
    private final Path proposalDirectory;
    private ResourceBundle messages;
    private Locale locale;
    private boolean initialized;

    @FXML private Label statusLabel;
    @FXML private ComboBox<CustomerRow> customerComboBox;
    @FXML private ComboBox<EnvironmentRow> environmentComboBox;
    @FXML private TextField projectNameField;
    @FXML private TextField projectValueField;
    @FXML private Button saveButton;
    @FXML private Label validationLabel;
    @FXML private HBox kanbanColumns;

    public KanbanController(
            CustomerService customerService,
            SalesPipelineService salesPipelineService,
            ProposalPdfService proposalPdfService,
            UiTaskRunner taskRunner
    ) {
        this.customerService = Objects.requireNonNull(customerService);
        this.salesPipelineService = Objects.requireNonNull(salesPipelineService);
        this.proposalPdfService = Objects.requireNonNull(proposalPdfService);
        this.taskRunner = Objects.requireNonNull(taskRunner);
        this.proposalDirectory = Path.of(ConfigLoader.load()
                .getProperty("woodlogix.proposals.directory", "propostas"))
                .toAbsolutePath().normalize();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        messages = resources;
        locale = Locale.forLanguageTag(ConfigLoader.load()
                .getProperty("woodlogix.ui.locale", "pt-BR"));
        projectValueField.setTextFormatter(new javafx.scene.control.TextFormatter<>(moneyFilter()));
        projectNameField.textProperty().addListener((observable, oldValue, newValue) -> validate());
        projectValueField.textProperty().addListener((observable, oldValue, newValue) -> validate());
        customerComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            loadEnvironments(newValue);
            validate();
        });
        environmentComboBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(EnvironmentRow item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : RoomTypeLabels.localized(item.type(), messages)
                        + " — " + item.customerName());
            }
        });
        environmentComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(EnvironmentRow item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : RoomTypeLabels.localized(item.type(), messages)
                        + " — " + item.customerName());
            }
        });
        customerComboBox.setItems(FXCollections.observableArrayList());
        environmentComboBox.setItems(FXCollections.observableArrayList());
        validate();
        loadCustomers();
        loadPipeline();
        initialized = true;
    }

    @FXML
    private void saveProject() {
        CustomerRow customer = customerComboBox.getValue();
        if (customer == null || isBlank(projectNameField.getText())) {
            UiTaskRunner.showInformation(text("dialog.error-header"),
                    text("error.project-required"), messages);
            return;
        }
        BigDecimal value;
        try {
            value = parseCurrency(projectValueField.getText());
        } catch (NumberFormatException exception) {
            UiTaskRunner.showInformation(text("dialog.error-header"),
                    text("error.invalid-value"), messages);
            return;
        }
        EnvironmentRow environment = environmentComboBox.getValue();
        SalesProjectData data = new SalesProjectData(customer.id(),
                environment == null ? null : environment.id(),
                projectNameField.getText().trim(), value);
        taskRunner.execute(() -> salesPipelineService.create(data), messages, project -> {
            projectNameField.clear();
            projectValueField.clear();
            statusLabel.setText(text("status.project-created"));
            loadPipeline();
        }, message -> statusLabel.setText(message));
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
            Platform.runLater(() -> loadEnvironments(customerComboBox.getValue()));
        }
    }

    private void loadCustomers() {
        String selectedCustomerId = customerComboBox.getValue() == null
                ? null : customerComboBox.getValue().id();
        taskRunner.execute(customerService::findAll, messages, customers -> {
            customerComboBox.setItems(FXCollections.observableArrayList(customers));
            if (selectedCustomerId != null) {
                customerComboBox.getSelectionModel().select(customers.stream()
                        .filter(customer -> customer.id().equals(selectedCustomerId))
                        .findFirst().orElse(null));
            }
        }, message -> statusLabel.setText(message));
    }

    private void loadEnvironments(CustomerRow customer) {
        if (customer == null) {
            environmentComboBox.getItems().clear();
            return;
        }
        String selectedEnvironmentId = environmentComboBox.getValue() == null
                ? null : environmentComboBox.getValue().id();
        taskRunner.execute(() -> salesPipelineService.findEnvironments(customer.id()),
                messages, environments -> {
                    environmentComboBox.setItems(FXCollections.observableArrayList(environments));
                    if (selectedEnvironmentId != null) {
                        environmentComboBox.getSelectionModel().select(environments.stream()
                                .filter(environment -> environment.id()
                                        .equals(selectedEnvironmentId))
                                .findFirst().orElse(null));
                    }
                }, message -> statusLabel.setText(message));
    }

    private void loadPipeline() {
        taskRunner.execute(salesPipelineService::findPipeline, messages, pipeline -> {
            renderPipeline(pipeline);
            statusLabel.setText(text("status.synced"));
        }, message -> statusLabel.setText(message));
    }

    private void renderPipeline(Map<SalesStage, List<SalesProjectRow>> pipeline) {
        kanbanColumns.getChildren().clear();
        for (SalesStage stage : SalesStage.values()) {
            VBox column = new VBox(10);
            column.getStyleClass().addAll("kanban-column",
                    "stage-" + stage.name().toLowerCase(Locale.ROOT).replace('_', '-'));
            column.setMinWidth(236);
            column.setPrefWidth(248);

            List<SalesProjectRow> projects = pipeline.getOrDefault(stage, List.of());
            HBox headingRow = new HBox(8);
            headingRow.getStyleClass().add("kanban-heading-row");
            Label heading = new Label(text(stage.getMessageKey()));
            heading.getStyleClass().add("kanban-heading");
            heading.setWrapText(true);
            heading.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(heading, javafx.scene.layout.Priority.ALWAYS);
            Label count = new Label(Integer.toString(projects.size()));
            count.getStyleClass().add("kanban-count");
            headingRow.getChildren().addAll(heading, count);
            column.getChildren().add(headingRow);
            column.setOnDragOver(event -> {
                if (event.getGestureSource() != column
                        && event.getDragboard().hasContent(PROJECT_ID_FORMAT)) {
                    event.acceptTransferModes(TransferMode.MOVE);
                }
                event.consume();
            });
            column.setOnDragDropped(event -> {
                boolean completed = false;
                if (event.getDragboard().hasContent(PROJECT_ID_FORMAT)) {
                    String projectId = (String) event.getDragboard()
                            .getContent(PROJECT_ID_FORMAT);
                    taskRunner.execute(() -> {
                        salesPipelineService.move(projectId, stage);
                        return null;
                    }, messages, ignored -> {
                        statusLabel.setText(text("status.stage-updated"));
                        loadPipeline();
                    }, message -> statusLabel.setText(message));
                    completed = true;
                }
                event.setDropCompleted(completed);
                event.consume();
            });
            if (projects.isEmpty()) {
                Label emptyState = new Label(text("pipeline.empty-stage"));
                emptyState.getStyleClass().add("kanban-empty-state");
                emptyState.setWrapText(true);
                column.getChildren().add(emptyState);
            }
            for (SalesProjectRow project : projects) {
                column.getChildren().add(createProjectCard(project));
            }
            kanbanColumns.getChildren().add(column);
        }
    }

    private Node createProjectCard(SalesProjectRow project) {
        VBox card = new VBox(7);
        card.getStyleClass().add("project-card");
        Label name = new Label(project.projectName());
        name.getStyleClass().add("project-card-title");
        name.setWrapText(true);
        Label customer = new Label(project.customerName());
        customer.getStyleClass().add("project-card-customer");
        customer.setWrapText(true);
        Label environment = new Label(project.environmentName().isBlank()
                ? text("pipeline.undefined-environment")
                : RoomTypeLabels.localized(project.environmentName(), messages));
        environment.getStyleClass().add("project-card-environment");
        environment.setWrapText(true);
        Label value = new Label(java.text.NumberFormat.getCurrencyInstance(locale)
                .format(project.totalValue()));
        value.getStyleClass().add("project-card-value");
        Button exportButton = new Button(text("proposal.export-button"));
        exportButton.getStyleClass().add("secondary-button");
        exportButton.setMaxWidth(Double.MAX_VALUE);
        exportButton.setOnAction(event -> exportProposal(project));
        card.getChildren().addAll(name, customer, environment, value, exportButton);
        card.setOnDragDetected(event -> {
            ClipboardContent content = new ClipboardContent();
            content.put(PROJECT_ID_FORMAT, project.id());
            card.startDragAndDrop(TransferMode.MOVE).setContent(content);
            event.consume();
        });
        return card;
    }

    private void exportProposal(SalesProjectRow project) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(text("proposal.save-title"));
        chooser.setInitialFileName(MessageFormat.format(text("proposal.filename"),
                safeFileName(project.projectName())));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(text("proposal.filter"), "*.pdf"));
        if (java.nio.file.Files.isDirectory(proposalDirectory)) {
            chooser.setInitialDirectory(proposalDirectory.toFile());
        }
        Window window = kanbanColumns.getScene().getWindow();
        java.io.File selectedFile = chooser.showSaveDialog(window);
        if (selectedFile == null) {
            return;
        }
        Path destination = selectedFile.toPath();
        taskRunner.execute(() -> {
            proposalPdfService.export(project.id(), destination);
            return destination;
        }, messages, path -> statusLabel.setText(MessageFormat.format(
                text("status.proposal-exported"), path)),
                message -> statusLabel.setText(message));
    }

    private void validate() {
        boolean validAmount = isBlank(projectValueField.getText())
                || isValidCurrencyInput(projectValueField.getText().trim());
        validationLabel.setText(validAmount ? "" : text("validation.amount"));
        validationLabel.setVisible(!validAmount);
        validationLabel.setManaged(!validAmount);
        saveButton.setDisable(customerComboBox.getValue() == null
                || isBlank(projectNameField.getText()) || !validAmount);
    }

    private BigDecimal parseCurrency(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) return BigDecimal.ZERO;
        if (!isValidCurrencyInput(normalized)) {
            throw new NumberFormatException("Invalid localized amount");
        }
        return new BigDecimal(normalized.replace(',', '.'));
    }

    private static boolean isValidCurrencyInput(String value) {
        if (value.isEmpty()) return true;
        if (!value.matches("[0-9]*[.,]?[0-9]{0,2}") || !value.matches(".*\\d.*")) {
            return false;
        }
        try {
            new BigDecimal(value.replace(',', '.'));
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static UnaryOperator<javafx.scene.control.TextFormatter.Change> moneyFilter() {
        return change -> change.getControlNewText().matches("[0-9]*[.,]?[0-9]{0,2}")
                ? change : null;
    }

    private String text(String key) {
        return messages.getString(key);
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
