package com.woodlogix.desktop.ui.kanban;

import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.application.environment.EnvironmentRow;
import com.woodlogix.application.sales.SalesProjectRow;
import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.desktop.service.CustomerService;
import com.woodlogix.desktop.service.ProposalPdfService;
import com.woodlogix.desktop.service.RemoteEnvironmentService;
import com.woodlogix.desktop.service.RemoteSalesPipelineService;
import com.woodlogix.desktop.service.SalesProjectData;
import com.woodlogix.desktop.ui.JavaFxTestSupport;
import com.woodlogix.desktop.ui.UiTaskRunner;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KanbanControllerHttpTest {
    private static final CustomerRow CUSTOMER =
            new CustomerRow("server-customer", "Ada Lovelace", null, null, null);
    private static final EnvironmentRow ENVIRONMENT =
            new EnvironmentRow("server-environment", "server-customer",
                    "Ada Lovelace", "Kitchen");
    private static final CustomerRow SECOND_CUSTOMER =
            new CustomerRow("second-customer", "Grace Hopper", null, null, null);
    private static final EnvironmentRow SECOND_ENVIRONMENT =
            new EnvironmentRow("second-environment", "second-customer",
                    "Grace Hopper", "Office");
    private static final SalesProjectRow PROJECT =
            new SalesProjectRow("server-project", "Kitchen", "Ada Lovelace",
                    null, null, "Kitchen", BigDecimal.TEN, SalesStage.CONTACT);

    @BeforeAll
    static void startJavaFx() throws Exception {
        JavaFxTestSupport.start();
    }

    @Test
    void loadsCustomersEnvironmentsAndPipelineFromRemoteServices() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        RemoteEnvironmentService environments = mock(RemoteEnvironmentService.class);
        RemoteSalesPipelineService pipeline = mock(RemoteSalesPipelineService.class);
        when(customers.findAll()).thenReturn(List.of(CUSTOMER));
        when(environments.findByCustomer("server-customer")).thenReturn(List.of(ENVIRONMENT));
        when(pipeline.findPipeline()).thenReturn(Map.of(SalesStage.CONTACT, List.of(PROJECT)));
        KanbanController controller = loadController(customers, environments, pipeline,
                mock(ProposalPdfService.class));

        assertEquals("server-customer", customerCombo(controller).getItems().get(0).id());
        fx(() -> {
            customerCombo(controller).getSelectionModel().select(CUSTOMER);
            return null;
        });
        assertEquals("server-environment",
                environmentCombo(controller).getItems().get(0).id());
        assertTrue(columns(controller).getChildren().size() > 0);
        verify(customers).findAll();
        verify(environments).findByCustomer("server-customer");
        verify(pipeline).findPipeline();
    }

    @Test
    void createsRemoteProjectWithServerIds() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        RemoteEnvironmentService environments = mock(RemoteEnvironmentService.class);
        RemoteSalesPipelineService pipeline = mock(RemoteSalesPipelineService.class);
        when(customers.findAll()).thenReturn(List.of(CUSTOMER));
        when(environments.findByCustomer("server-customer")).thenReturn(List.of());
        when(pipeline.findPipeline()).thenReturn(Map.of());
        when(pipeline.create(new SalesProjectData(
                "server-customer", "server-environment", "New kitchen", BigDecimal.TEN)))
                .thenReturn(PROJECT);
        KanbanController controller = loadController(customers, environments, pipeline,
                mock(ProposalPdfService.class));

        fx(() -> {
            customerCombo(controller).getSelectionModel().select(CUSTOMER);
            environmentCombo(controller).getItems().add(ENVIRONMENT);
            environmentCombo(controller).getSelectionModel().select(ENVIRONMENT);
            projectName(controller).setText("New kitchen");
            projectValue(controller).setText("10");
            ReflectionTestUtils.invokeMethod(controller, "saveProject");
            return null;
        });

        verify(pipeline).create(new SalesProjectData(
                "server-customer", "server-environment", "New kitchen", BigDecimal.TEN));
        assertTrue(!status(controller).getText().isBlank());
    }

    @Test
    void movesRemoteProjectAndExportsLoadedProjectWithoutLocalLookup() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        RemoteEnvironmentService environments = mock(RemoteEnvironmentService.class);
        RemoteSalesPipelineService pipeline = mock(RemoteSalesPipelineService.class);
        ProposalPdfService pdf = mock(ProposalPdfService.class);
        when(customers.findAll()).thenReturn(List.of(CUSTOMER));
        when(pipeline.findPipeline()).thenReturn(Map.of());
        KanbanController controller = loadController(customers, environments, pipeline, pdf);
        Path destination = Path.of("target", "kanban-proposal.pdf");

        fx(() -> {
            ReflectionTestUtils.invokeMethod(controller, "moveProject",
                    "server-project", SalesStage.NEGOTIATION);
            ReflectionTestUtils.invokeMethod(controller, "exportProposal", PROJECT, destination);
            return null;
        });

        verify(pipeline).move("server-project", SalesStage.NEGOTIATION);
        verify(pdf).export(PROJECT, destination);
    }

    @Test
    void preservesVisiblePipelineWhenRemoteLoadFails() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        RemoteEnvironmentService environments = mock(RemoteEnvironmentService.class);
        RemoteSalesPipelineService pipeline = mock(RemoteSalesPipelineService.class);
        when(customers.findAll()).thenReturn(List.of(CUSTOMER));
        RuntimeException failure = new RuntimeException("server unavailable");
        when(pipeline.findPipeline()).thenThrow(failure);
        KanbanController controller = loadController(customers, environments, pipeline,
                mock(ProposalPdfService.class));

        assertEquals("server unavailable", status(controller).getText());
        assertTrue(columns(controller).getChildren().isEmpty());
    }

    @Test
    void clearsPreviousEnvironmentsImmediatelyWhenCustomerChanges() throws Exception {
        DeferredUiTaskRunner runner = new DeferredUiTaskRunner();
        KanbanController controller = loadController(mock(CustomerService.class),
                mock(RemoteEnvironmentService.class), mock(RemoteSalesPipelineService.class),
                mock(ProposalPdfService.class), runner);
        fx(() -> {
            customerCombo(controller).setItems(
                    javafx.collections.FXCollections.observableArrayList(
                            CUSTOMER, SECOND_CUSTOMER));
            customerCombo(controller).getSelectionModel().select(CUSTOMER);
            return null;
        });
        fx(() -> {
            runner.completeLast(List.of(ENVIRONMENT));
            return null;
        });
        assertEquals(List.of(ENVIRONMENT), environmentCombo(controller).getItems());

        fx(() -> {
            customerCombo(controller).getSelectionModel().select(SECOND_CUSTOMER);
            return null;
        });

        assertTrue(environmentCombo(controller).getItems().isEmpty());
        assertNull(environmentCombo(controller).getValue());
    }

    @Test
    void keepsEnvironmentsEmptyWhenCurrentCustomerRequestFails() throws Exception {
        DeferredUiTaskRunner runner = new DeferredUiTaskRunner();
        KanbanController controller = loadController(mock(CustomerService.class),
                mock(RemoteEnvironmentService.class), mock(RemoteSalesPipelineService.class),
                mock(ProposalPdfService.class), runner);
        fx(() -> {
            customerCombo(controller).setItems(
                    javafx.collections.FXCollections.observableArrayList(
                            CUSTOMER, SECOND_CUSTOMER));
            customerCombo(controller).getSelectionModel().select(CUSTOMER);
            return null;
        });
        fx(() -> {
            runner.completeLast(List.of(ENVIRONMENT));
            return null;
        });
        fx(() -> {
            customerCombo(controller).getSelectionModel().select(SECOND_CUSTOMER);
            runner.failLast("server unavailable");
            return null;
        });

        assertTrue(environmentCombo(controller).getItems().isEmpty());
        assertNull(environmentCombo(controller).getValue());
    }

    @Test
    void ignoresOutOfOrderEnvironmentResponsesForPreviousCustomer() throws Exception {
        DeferredUiTaskRunner runner = new DeferredUiTaskRunner();
        KanbanController controller = loadController(mock(CustomerService.class),
                mock(RemoteEnvironmentService.class), mock(RemoteSalesPipelineService.class),
                mock(ProposalPdfService.class), runner);
        fx(() -> {
            customerCombo(controller).setItems(
                    javafx.collections.FXCollections.observableArrayList(
                            CUSTOMER, SECOND_CUSTOMER));
            customerCombo(controller).getSelectionModel().select(CUSTOMER);
            return null;
        });
        int firstRequest = runner.size() - 1;
        fx(() -> {
            customerCombo(controller).getSelectionModel().select(SECOND_CUSTOMER);
            return null;
        });
        int secondRequest = runner.size() - 1;

        fx(() -> {
            runner.complete(secondRequest, List.of(SECOND_ENVIRONMENT));
            return null;
        });
        fx(() -> {
            runner.complete(firstRequest, List.of(ENVIRONMENT));
            return null;
        });

        assertEquals(List.of(SECOND_ENVIRONMENT), environmentCombo(controller).getItems());
    }

    private static KanbanController loadController(
            CustomerService customers,
            RemoteEnvironmentService environments,
            RemoteSalesPipelineService pipeline,
            ProposalPdfService pdf
    ) throws Exception {
        return loadController(customers, environments, pipeline, pdf,
                new SynchronousUiTaskRunner());
    }

    private static KanbanController loadController(
            CustomerService customers,
            RemoteEnvironmentService environments,
            RemoteSalesPipelineService pipeline,
            ProposalPdfService pdf,
            UiTaskRunner taskRunner
    ) throws Exception {
        KanbanController controller = new KanbanController(
                customers, environments, pipeline, pdf, taskRunner);
        FXMLLoader loader = new FXMLLoader(
                KanbanControllerHttpTest.class.getResource(
                        "/com/woodlogix/desktop/ui/kanban/kanban-view.fxml"),
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
    private static ComboBox<CustomerRow> customerCombo(KanbanController controller) {
        return (ComboBox<CustomerRow>) ReflectionTestUtils.getField(
                controller, "customerComboBox");
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<EnvironmentRow> environmentCombo(KanbanController controller) {
        return (ComboBox<EnvironmentRow>) ReflectionTestUtils.getField(
                controller, "environmentComboBox");
    }

    private static TextField projectName(KanbanController controller) {
        return (TextField) ReflectionTestUtils.getField(controller, "projectNameField");
    }

    private static TextField projectValue(KanbanController controller) {
        return (TextField) ReflectionTestUtils.getField(controller, "projectValueField");
    }

    private static HBox columns(KanbanController controller) {
        return (HBox) ReflectionTestUtils.getField(controller, "kanbanColumns");
    }

    private static Label status(KanbanController controller) {
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

    private static final class DeferredUiTaskRunner extends UiTaskRunner {
        private final List<Pending> pending = new ArrayList<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> void execute(
                Callable<T> operation,
                ResourceBundle messages,
                Consumer<T> onSuccess,
                Consumer<String> onFailure
        ) {
            pending.add(new Pending((Consumer<Object>) onSuccess, onFailure));
        }

        int size() {
            return pending.size();
        }

        void complete(int index, Object value) {
            pending.get(index).success.accept(value);
        }

        void completeLast(Object value) {
            complete(pending.size() - 1, value);
        }

        void failLast(String message) {
            pending.get(pending.size() - 1).failure.accept(message);
        }

        private record Pending(Consumer<Object> success, Consumer<String> failure) {
        }
    }
}
