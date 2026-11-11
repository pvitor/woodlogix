package com.woodlogix.desktop.ui.dashboard;

import com.woodlogix.desktop.service.CustomerService;
import com.woodlogix.desktop.service.EstimateDraftService;
import com.woodlogix.desktop.service.SalesPipelineService;
import com.woodlogix.desktop.ui.JavaFxTestSupport;
import com.woodlogix.desktop.ui.UiTaskRunner;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardControllerHttpTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        JavaFxTestSupport.start();
    }

    @Test
    void obtainsCustomerCountFromHttpBackedService() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        when(customers.findAll()).thenReturn(List.of(
                new com.woodlogix.application.customer.CustomerRow(
                        "server-1", "Ada", null, null, null),
                new com.woodlogix.application.customer.CustomerRow(
                        "server-2", "Bruno", null, null, null)));
        SalesPipelineService pipelineService = mock(SalesPipelineService.class);
        EstimateDraftService draftService = mock(EstimateDraftService.class);
        when(pipelineService.findPipeline()).thenReturn(Map.of());
        when(draftService.findActiveDraft()).thenReturn(Optional.empty());
        DashboardController controller = loadController(customers, pipelineService, draftService);

        fx(() -> {
            controller.refreshData();
            return null;
        });

        assertEquals("2", label(controller, "customerCountLabel").getText());
    }

    @Test
    void preservesPreviousCountWhenRemoteCustomerLookupFails() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        when(customers.findAll()).thenThrow(new RuntimeException("server unavailable"));
        SalesPipelineService pipelineService = mock(SalesPipelineService.class);
        EstimateDraftService draftService = mock(EstimateDraftService.class);
        DashboardController controller = loadController(customers, pipelineService, draftService);

        fx(() -> {
            label(controller, "customerCountLabel").setText("previous");
            controller.refreshData();
            return null;
        });

        assertEquals("previous", label(controller, "customerCountLabel").getText());
        assertEquals("server unavailable", label(controller, "dashboardStatusLabel").getText());
    }

    private static DashboardController loadController(
            CustomerService customers,
            SalesPipelineService pipelineService,
            EstimateDraftService draftService
    ) throws Exception {
        DashboardController controller = new DashboardController(
                customers,
                pipelineService,
                draftService,
                new SynchronousUiTaskRunner());
        return loadControllerFxml(controller);
    }

    private static DashboardController loadControllerFxml(
            DashboardController controller
    ) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                DashboardControllerHttpTest.class.getResource("dashboard-view.fxml"),
                ResourceBundle.getBundle("com.woodlogix.desktop.i18n.messages"));
        loader.setControllerFactory(type -> controller);
        fx(() -> {
            loader.load();
            return null;
        });
        return controller;
    }

    private static Label label(DashboardController controller, String field) {
        return (Label) ReflectionTestUtils.getField(controller, field);
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
