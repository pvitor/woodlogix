package com.woodlogix.desktop.ui.furniture;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
public class FurnitureWorkspaceController implements Initializable {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(FurnitureWorkspaceController.class);

    private final ConfigurableApplicationContext applicationContext;
    private final Map<String, Node> moduleCache = new HashMap<>();
    private ResourceBundle messages;

    @FXML private StackPane moduleContent;
    @FXML private Button itemsButton;
    @FXML private Button partsButton;

    public FurnitureWorkspaceController(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = Objects.requireNonNull(applicationContext);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        messages = resources;
        showItems();
    }

    @FXML
    private void showItems() {
        loadModule("furniture-item-view.fxml", itemsButton);
    }

    @FXML
    private void showParts() {
        loadModule("furniture-part-view.fxml", partsButton);
    }

    private void loadModule(String resource, Button selectedButton) {
        try {
            Node view = moduleCache.computeIfAbsent(resource, this::loadModuleView);
            moduleContent.getChildren().setAll(view);
            List.of(itemsButton, partsButton).forEach(button ->
                    button.getStyleClass().remove("module-switch-button-selected"));
            selectedButton.getStyleClass().add("module-switch-button-selected");
        } catch (RuntimeException exception) {
            LOGGER.error("Could not load furniture module {}", resource, exception);
            moduleContent.getChildren().setAll(
                    new javafx.scene.control.Label(messages.getString("error.view-load")));
        }
    }

    private Node loadModuleView(String resource) {
        try {
            URL viewUrl = getClass().getResource(
                    "/com/woodlogix/desktop/ui/furniture/" + resource);
            if (viewUrl == null) {
                throw new IOException("Furniture module resource was not found: " + resource);
            }
            FXMLLoader loader = new FXMLLoader(viewUrl, messages);
            loader.setControllerFactory(applicationContext::getBean);
            return loader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load furniture module " + resource, exception);
        }
    }
}
