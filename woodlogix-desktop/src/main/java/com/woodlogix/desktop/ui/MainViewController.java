package com.woodlogix.desktop.ui;

import com.woodlogix.desktop.ui.dashboard.DashboardController;
import com.woodlogix.desktop.ui.pricing.PricingController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

@Component
public class MainViewController implements Initializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MainViewController.class);

    private final ConfigurableApplicationContext applicationContext;
    private final Map<String, Node> viewCache = new HashMap<>();
    private final Map<String, Runnable> viewRefreshers = new HashMap<>();
    private ResourceBundle messages;

    @FXML private StackPane contentHost;
    @FXML private Label sectionLabel;
    @FXML private Button dashboardButton;
    @FXML private Button customersButton;
    @FXML private Button environmentsButton;
    @FXML private Button materialsButton;
    @FXML private Button pricingButton;
    @FXML private Button pipelineButton;

    public MainViewController(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = Objects.requireNonNull(applicationContext);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        messages = resources;
        showDashboard();
    }

    @FXML
    private void showDashboard() {
        navigate("dashboard/dashboard-view.fxml", "nav.dashboard", dashboardButton);
    }

    @FXML
    private void showCustomers() {
        navigate("client/customer-view.fxml", "tab.customers", customersButton);
    }

    @FXML
    private void showEnvironments() {
        navigate("environment/environment-view.fxml", "tab.environments", environmentsButton);
    }

    @FXML
    private void showMaterials() {
        navigate("pricing/material-view.fxml", "tab.materials", materialsButton);
    }

    @FXML
    private void showPricing() {
        navigate("pricing/pricing-view.fxml", "tab.pricing", pricingButton);
    }

    @FXML
    private void showPipeline() {
        navigate("kanban/kanban-view.fxml", "tab.pipeline", pipelineButton);
    }

    private void navigate(String viewPath, String titleKey, Button selectedButton) {
        try {
            Node view = viewCache.computeIfAbsent(viewPath, this::loadView);
            contentHost.getChildren().setAll(view);
            viewRefreshers.getOrDefault(viewPath, () -> { }).run();
            sectionLabel.setText(messages.getString(titleKey));
            updateNavigationSelection(selectedButton);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not load application view {}", viewPath, exception);
            Label error = new Label(messages.getString("error.view-load"));
            error.getStyleClass().add("validation-error");
            contentHost.getChildren().setAll(error);
            sectionLabel.setText(messages.getString(titleKey));
        }
    }

    private Node loadView(String viewPath) {
        try {
            URL viewUrl = getClass().getResource("/com/woodlogix/desktop/ui/" + viewPath);
            if (viewUrl == null) {
                throw new IOException("View resource was not found: " + viewPath);
            }
            FXMLLoader loader = new FXMLLoader(viewUrl, messages);
            loader.setControllerFactory(applicationContext::getBean);
            Node view = loader.load();
            if (loader.getController() instanceof DashboardController dashboardController) {
                dashboardController.setNavigationHandler(this::navigateFromDashboard);
                viewRefreshers.put(viewPath, dashboardController::refreshData);
            }
            if (loader.getController() instanceof PricingController pricingController) {
                viewRefreshers.put(viewPath, pricingController::refreshCatalogs);
            }
            return view;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load view " + viewPath, exception);
        }
    }

    private void navigateFromDashboard(String destination) {
        switch (destination) {
            case "customers" -> showCustomers();
            case "environments" -> showEnvironments();
            case "pricing" -> showPricing();
            case "materials" -> showMaterials();
            case "pipeline" -> showPipeline();
            default -> LOGGER.warn("Ignoring unknown dashboard destination");
        }
    }

    private void updateNavigationSelection(Button selectedButton) {
        List.of(dashboardButton, customersButton, environmentsButton,
                        materialsButton, pricingButton, pipelineButton)
                .forEach(button -> button.getStyleClass().remove("navigation-button-selected"));
        selectedButton.getStyleClass().add("navigation-button-selected");
    }
}
