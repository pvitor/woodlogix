package com.woodlogix.desktop.ui.client;

import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.desktop.service.CustomerData;
import com.woodlogix.desktop.service.CustomerService;
import com.woodlogix.desktop.ui.JavaFxTestSupport;
import com.woodlogix.desktop.ui.UiTaskRunner;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerControllerHttpTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        JavaFxTestSupport.start();
    }

    @Test
    void refreshesTableAfterSuccessfulHttpCreate() throws Exception {
        CustomerService service = mock(CustomerService.class);
        CustomerRow created = new CustomerRow(
                "server-id", "Ada Lovelace", null, null, null);
        when(service.search("")).thenReturn(List.of(created));
        when(service.create(new CustomerData("Ada Lovelace", null, null, null)))
                .thenReturn(created);
        SynchronousUiTaskRunner runner = new SynchronousUiTaskRunner();
        CustomerController controller = loadController(service, runner);

        fx(() -> {
            textField(controller, "nameField").setText("Ada Lovelace");
            ReflectionTestUtils.invokeMethod(controller, "saveCustomer");
            return null;
        });

        verify(service).create(new CustomerData("Ada Lovelace", null, null, null));
        assertEquals(List.of(created), table(controller).getItems());
    }

    @Test
    void reportsHttpFailureWithoutShowingSuccess() throws Exception {
        CustomerService service = mock(CustomerService.class);
        when(service.search("")).thenReturn(List.of());
        when(service.create(new CustomerData("Ada Lovelace", null, null, null)))
                .thenThrow(new RuntimeException("server unavailable"));
        CustomerController controller = loadController(service, new SynchronousUiTaskRunner());

        fx(() -> {
            textField(controller, "nameField").setText("Ada Lovelace");
            ReflectionTestUtils.invokeMethod(controller, "saveCustomer");
            return null;
        });

        assertEquals("server unavailable", status(controller).getText());
    }

    private static CustomerController loadController(
            CustomerService service,
            UiTaskRunner runner
    ) throws Exception {
        CustomerController controller = new CustomerController(service, runner);
        FXMLLoader loader = new FXMLLoader(
                CustomerControllerHttpTest.class.getResource("customer-view.fxml"),
                ResourceBundle.getBundle(
                        "com.woodlogix.desktop.i18n.messages", Locale.US));
        loader.setControllerFactory(type -> controller);
        fx(() -> {
            loader.load();
            return null;
        });
        return controller;
    }

    @SuppressWarnings("unchecked")
    private static javafx.scene.control.TextField textField(
            CustomerController controller,
            String field
    ) {
        return (javafx.scene.control.TextField) ReflectionTestUtils.getField(controller, field);
    }

    @SuppressWarnings("unchecked")
    private static TableView<CustomerRow> table(CustomerController controller) {
        return (TableView<CustomerRow>) ReflectionTestUtils.getField(controller, "customerTable");
    }

    private static Label status(CustomerController controller) {
        return (Label) ReflectionTestUtils.getField(controller, "statusLabel");
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }

    private static final class SynchronousUiTaskRunner extends UiTaskRunner {
        @Override
        public <T> void execute(
                Callable<T> operation,
                ResourceBundle messages,
                Consumer<T> onSuccess,
                Consumer<String> onFailure
        ) {
            try {
                onSuccess.accept(operation.call());
            } catch (Exception exception) {
                onFailure.accept(exception.getMessage());
            }
        }
    }
}
