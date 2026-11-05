package com.woodlogix.desktop;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;

public class AppLauncher extends Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppLauncher.class);

    private ConfigurableApplicationContext applicationContext;
    private Properties applicationProperties;

    @Override
    public void init() {
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) ->
                LOGGER.error("Uncaught application error on thread {}", thread.getName(), failure));
        try {
            applicationContext = new SpringApplicationBuilder(WoodLogixApplication.class)
                    .headless(false)
                    .run(getParameters().getRaw().toArray(String[]::new));
            applicationProperties = com.woodlogix.desktop.config.ConfigLoader.load();
        } catch (RuntimeException | Error failure) {
            LOGGER.error("WoodLogix failed to initialize", failure);
            throw failure;
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        try {
            startApplicationWindow(primaryStage);
        } catch (Exception failure) {
            LOGGER.error("WoodLogix window failed to load", failure);
            throw failure;
        }
    }

    private void startApplicationWindow(Stage primaryStage) throws Exception {
        Locale locale = Locale.forLanguageTag(
                applicationProperties.getProperty("woodlogix.ui.locale", "pt-BR")
        );
        if (locale.getLanguage().isBlank()) {
            throw new IllegalStateException("woodlogix.ui.locale must be a valid language tag");
        }
        ResourceBundle messages = ResourceBundle.getBundle(
                "com.woodlogix.desktop.i18n.messages",
                locale,
                ResourceBundle.Control.getNoFallbackControl(
                        ResourceBundle.Control.FORMAT_DEFAULT)
        );
        FXMLLoader fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/woodlogix/desktop/ui/main-view.fxml")
        );
        fxmlLoader.setResources(messages);
        fxmlLoader.setControllerFactory(applicationContext::getBean);
        double width = Double.parseDouble(
                applicationProperties.getProperty("woodlogix.ui.window-width")
        );
        double height = Double.parseDouble(
                applicationProperties.getProperty("woodlogix.ui.window-height")
        );
        Scene scene = new Scene(fxmlLoader.load(), width, height);
        scene.getStylesheets().add(getClass()
                .getResource("/com/woodlogix/desktop/ui/main-view.css").toExternalForm());

        primaryStage.setTitle(messages.getString("app.title"));
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

}