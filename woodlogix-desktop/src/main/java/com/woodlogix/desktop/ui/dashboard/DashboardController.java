package com.woodlogix.desktop.ui.dashboard;

import javafx.fxml.FXML;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
public class DashboardController {
    private Consumer<String> navigationHandler = view -> { };

    public void setNavigationHandler(Consumer<String> navigationHandler) {
        this.navigationHandler = navigationHandler;
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
    private void openFurniture() {
        navigationHandler.accept("furniture");
    }

    @FXML
    private void openPipeline() {
        navigationHandler.accept("pipeline");
    }
}
