package com.woodlogix.desktop.ui;

import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Component
public class UiTaskRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(UiTaskRunner.class);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "woodlogix-ui-worker");
        thread.setDaemon(true);
        return thread;
    });

    public <T> void execute(
            Callable<T> operation,
            ResourceBundle messages,
            Consumer<T> onSuccess,
            Consumer<String> onFailure
    ) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return operation.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> {
            Throwable failure = task.getException();
            LOGGER.error("Background application operation failed", failure);
            String message = localizeFailure(failure, messages);
            onFailure.accept(message);
            showError(message, failure, messages);
        });
        executor.execute(task);
    }

    public static boolean confirm(String message, ResourceBundle messages) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message,
                ButtonType.YES, ButtonType.NO);
        alert.setTitle(messages.getString("dialog.title"));
        alert.setHeaderText(messages.getString("dialog.confirm-delete-header"));
        return alert.showAndWait().filter(ButtonType.YES::equals).isPresent();
    }

    public static void showInformation(String header, String message, ResourceBundle messages) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(messages.getString("dialog.title"));
        alert.setHeaderText(header);
        alert.show();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    private static String localizeFailure(Throwable failure, ResourceBundle messages) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && messages.containsKey(cause.getMessage())) {
                return messages.getString(cause.getMessage());
            }
        }
        return messages.getString("error.operation-failed");
    }

    private static void showError(
            String message, Throwable failure, ResourceBundle messages
    ) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(messages.getString("dialog.title"));
        alert.setHeaderText(messages.getString("dialog.error-header"));
        TextArea messageArea = new TextArea(message);
        messageArea.setEditable(false);
        messageArea.setWrapText(true);
        messageArea.setPrefRowCount(Math.max(2, Math.min(5, message.length() / 55 + 1)));
        messageArea.setPrefColumnCount(54);
        alert.getDialogPane().setContent(new VBox(
                8, new Label(messages.getString("dialog.message-label")), messageArea));
        if (failure != null) {
            TextArea detailsArea = new TextArea(stackTrace(failure));
            detailsArea.setEditable(false);
            detailsArea.setWrapText(false);
            detailsArea.setPrefColumnCount(90);
            detailsArea.setPrefRowCount(16);
            alert.getDialogPane().setExpandableContent(new VBox(
                    6, new Label(messages.getString("dialog.details-label")), detailsArea));
        }
        alert.show();
    }

    private static String stackTrace(Throwable failure) {
        StringWriter writer = new StringWriter();
        failure.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
