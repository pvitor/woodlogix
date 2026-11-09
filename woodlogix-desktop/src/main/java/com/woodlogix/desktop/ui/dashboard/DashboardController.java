package com.woodlogix.desktop.ui.dashboard;

import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.application.sales.SalesProjectRow;
import com.woodlogix.desktop.service.CustomerService;
import com.woodlogix.desktop.service.EstimateDraftService;
import com.woodlogix.desktop.service.SalesPipelineService;
import com.woodlogix.desktop.ui.UiTaskRunner;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;

@Component
public class DashboardController implements Initializable {
    private final CustomerService customerService;
    private final SalesPipelineService salesPipelineService;
    private final EstimateDraftService estimateDraftService;
    private final UiTaskRunner taskRunner;

    private Consumer<String> navigationHandler = view -> { };
    private ResourceBundle messages;

    @FXML private Label customerCountLabel;
    @FXML private Label opportunityCountLabel;
    @FXML private Label draftStatusLabel;
    @FXML private Label dashboardStatusLabel;
    @FXML private VBox pipelineStageList;

    public DashboardController(
            CustomerService customerService,
            SalesPipelineService salesPipelineService,
            EstimateDraftService estimateDraftService,
            UiTaskRunner taskRunner
    ) {
        this.customerService = Objects.requireNonNull(customerService);
        this.salesPipelineService = Objects.requireNonNull(salesPipelineService);
        this.estimateDraftService = Objects.requireNonNull(estimateDraftService);
        this.taskRunner = Objects.requireNonNull(taskRunner);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        messages = resources;
    }

    public void setNavigationHandler(Consumer<String> navigationHandler) {
        this.navigationHandler = Objects.requireNonNull(navigationHandler);
    }

    public void refreshData() {
        dashboardStatusLabel.setText(messages.getString("status.loading"));
        taskRunner.execute(() -> {
            int customerCount = customerService.findAll().size();
            Map<SalesStage, List<SalesProjectRow>> pipeline =
                    salesPipelineService.findPipeline();
            boolean hasDraft = estimateDraftService.findActiveDraft().isPresent();
            return new DashboardSnapshot(customerCount, pipeline, hasDraft);
        }, messages, this::showDashboardData,
                message -> dashboardStatusLabel.setText(message));
    }

    @FXML
    private void openCustomers() {
        navigationHandler.accept("customers");
    }

    @FXML
    private void openEnvironments() {
        navigationHandler.accept("environments");
    }

    @FXML
    private void openMaterials() {
        navigationHandler.accept("materials");
    }

    @FXML
    private void openPricing() {
        navigationHandler.accept("pricing");
    }

    @FXML
    private void openPipeline() {
        navigationHandler.accept("pipeline");
    }

    private void showDashboardData(DashboardSnapshot snapshot) {
        customerCountLabel.setText(Integer.toString(snapshot.customerCount()));
        long opportunityCount = snapshot.pipeline().entrySet().stream()
                .filter(entry -> entry.getKey() != SalesStage.CLOSED)
                .mapToLong(entry -> entry.getValue().size())
                .sum();
        opportunityCountLabel.setText(Long.toString(opportunityCount));
        draftStatusLabel.setText(messages.getString(snapshot.hasDraft()
                ? "dashboard.draft-present" : "dashboard.draft-absent"));
        renderPipeline(snapshot.pipeline());
        dashboardStatusLabel.setText(messages.getString("dashboard.updated"));
    }

    private void renderPipeline(Map<SalesStage, List<SalesProjectRow>> pipeline) {
        pipelineStageList.getChildren().clear();
        long projectCount = pipeline.values().stream().mapToLong(List::size).sum();
        if (projectCount == 0) {
            Label emptyState = new Label(messages.getString("dashboard.pipeline-empty"));
            emptyState.getStyleClass().add("dashboard-empty");
            emptyState.setWrapText(true);
            pipelineStageList.getChildren().add(emptyState);
            return;
        }

        for (SalesStage stage : SalesStage.values()) {
            int count = pipeline.getOrDefault(stage, List.of()).size();
            HBox row = new HBox(10);
            row.getStyleClass().add("dashboard-stage-row");

            Label stageName = new Label(messages.getString(stage.getMessageKey()));
            stageName.getStyleClass().add("dashboard-stage-name");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label stageCount = new Label(Integer.toString(count));
            stageCount.getStyleClass().add("dashboard-stage-count");
            row.getChildren().addAll(stageName, spacer, stageCount);
            pipelineStageList.getChildren().add(row);
        }
    }

    private record DashboardSnapshot(
            int customerCount,
            Map<SalesStage, List<SalesProjectRow>> pipeline,
            boolean hasDraft
    ) {
    }
}
