package com.woodlogix.desktop.ui.environment;

import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.application.environment.EnvironmentRow;
import com.woodlogix.core.enums.RoomType;
import com.woodlogix.desktop.service.CustomerService;
import com.woodlogix.desktop.service.RemoteEnvironmentService;
import com.woodlogix.desktop.ui.JavaFxTestSupport;
import com.woodlogix.desktop.ui.UiTaskRunner;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnvironmentControllerHttpTest {
    private static final CustomerRow CUSTOMER =
            new CustomerRow("server-customer", "Ada Lovelace", null, null, null);
    private static final EnvironmentRow ENVIRONMENT =
            new EnvironmentRow("server-environment", "server-customer",
                    "Ada Lovelace", "Kitchen");

    @BeforeAll
    static void startJavaFx() throws Exception {
        JavaFxTestSupport.start();
    }

    @Test
    void loadsCustomersAndEnvironmentsFromRemoteServices() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        RemoteEnvironmentService environments = mock(RemoteEnvironmentService.class);
        when(customers.findByName("")).thenReturn(List.of(CUSTOMER));
        when(environments.findAll()).thenReturn(List.of(ENVIRONMENT));
        EnvironmentController controller = loadController(customers, environments);

        assertEquals("server-customer", combo(controller).getItems().get(0).id());
        assertEquals(List.of("server-customer"),
                table(controller).getItems().stream()
                        .map(EnvironmentCustomerRow::customerId).toList());
        verify(customers).findByName("");
        verify(environments).findAll();
    }

    @Test
    void createsAndReplacesUsingRemoteCustomerAndEnvironmentIds() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        RemoteEnvironmentService environments = mock(RemoteEnvironmentService.class);
        when(customers.findByName("")).thenReturn(List.of(CUSTOMER));
        when(environments.findAll()).thenReturn(List.of(ENVIRONMENT));
        when(environments.createForCustomer("server-customer",
                List.of(RoomType.KITCHEN), null)).thenReturn(List.of(ENVIRONMENT));
        when(environments.replaceCustomerEnvironments("server-customer",
                List.of("KITCHEN"))).thenReturn(List.of(ENVIRONMENT));
        EnvironmentController controller = loadController(customers, environments);

        fx(() -> {
            combo(controller).getSelectionModel().select(CUSTOMER);
            checkboxes(controller).get(RoomType.KITCHEN).setSelected(true);
            ReflectionTestUtils.invokeMethod(controller, "saveEnvironment");
            combo(controller).getSelectionModel().select(CUSTOMER);
            ReflectionTestUtils.setField(controller, "editingCustomerId", "server-customer");
            checkboxes(controller).get(RoomType.KITCHEN).setSelected(true);
            ReflectionTestUtils.invokeMethod(controller, "saveEnvironment");
            return null;
        });

        verify(environments).createForCustomer("server-customer",
                List.of(RoomType.KITCHEN), null);
        verify(environments).replaceCustomerEnvironments("server-customer",
                List.of("KITCHEN"));
        assertTrue(!status(controller).getText().isBlank());
    }

    @Test
    void preservesVisibleRowsWhenRemoteCreationFails() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        RemoteEnvironmentService environments = mock(RemoteEnvironmentService.class);
        when(customers.findByName("")).thenReturn(List.of(CUSTOMER));
        when(environments.findAll()).thenReturn(List.of(ENVIRONMENT));
        when(environments.createForCustomer("server-customer",
                List.of(RoomType.KITCHEN), null))
                .thenThrow(new RuntimeException("server unavailable"));
        EnvironmentController controller = loadController(customers, environments);

        List<EnvironmentCustomerRow> before = List.copyOf(table(controller).getItems());
        fx(() -> {
            combo(controller).getSelectionModel().select(CUSTOMER);
            checkboxes(controller).get(RoomType.KITCHEN).setSelected(true);
            ReflectionTestUtils.invokeMethod(controller, "saveEnvironment");
            return null;
        });

        assertEquals(before, table(controller).getItems());
        assertEquals("server unavailable", status(controller).getText());
    }

    private static EnvironmentController loadController(
            CustomerService customers,
            RemoteEnvironmentService environments
    ) throws Exception {
        EnvironmentController controller = new EnvironmentController(
                customers, environments, new SynchronousUiTaskRunner());
        FXMLLoader loader = new FXMLLoader(
                EnvironmentControllerHttpTest.class.getResource("environment-view.fxml"),
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
    private static ComboBox<CustomerRow> combo(EnvironmentController controller) {
        return (ComboBox<CustomerRow>) ReflectionTestUtils.getField(
                controller, "customerComboBox");
    }

    @SuppressWarnings("unchecked")
    private static TableView<EnvironmentCustomerRow> table(
            EnvironmentController controller
    ) {
        return (TableView<EnvironmentCustomerRow>) ReflectionTestUtils.getField(
                controller, "environmentTable");
    }

    @SuppressWarnings("unchecked")
    private static Map<RoomType, CheckBox> checkboxes(EnvironmentController controller) {
        return (Map<RoomType, CheckBox>) ReflectionTestUtils.getField(
                controller, "roomTypeCheckBoxes");
    }

    private static Label status(EnvironmentController controller) {
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
