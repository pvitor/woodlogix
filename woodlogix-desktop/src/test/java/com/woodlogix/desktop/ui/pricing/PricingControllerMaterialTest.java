package com.woodlogix.desktop.ui.pricing;

import com.woodlogix.application.edgebanding.EdgeBandingRow;
import com.woodlogix.application.hardware.HardwareRow;
import com.woodlogix.application.material.MaterialRow;
import com.woodlogix.application.sales.SalesProjectRow;
import com.woodlogix.application.workpiece.ProjectWorkpieceRow;
import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.core.model.HardwareUsage;
import com.woodlogix.desktop.service.*;
import com.woodlogix.desktop.ui.JavaFxTestSupport;
import com.woodlogix.desktop.ui.UiTaskRunner;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PricingControllerMaterialTest {
    private final ProjectWorkpieceService pieces = mock(ProjectWorkpieceService.class);
    private final EstimateDraftService drafts = mock(EstimateDraftService.class);
    private UiTaskRunner runner;
    private PricingController controller;
    private final MaterialRow original = new MaterialRow("material", "MDF", "White",
            bd("18"), bd("1000"), bd("1000"), bd("100"));
    private final MaterialRow changed = new MaterialRow("material", "MDF", "White",
            bd("18"), bd("1000"), bd("1000"), bd("250"));
    private final EdgeBandingRow edge = new EdgeBandingRow("edge", "PVC", bd("22"), bd("2.50"));
    private final EdgeBandingRow edgeChanged = new EdgeBandingRow("edge", "PVC", bd("22"), bd("9.00"));

    @BeforeAll
    static void startJavaFx() throws Exception {
        JavaFxTestSupport.start();
    }

    @BeforeEach
    void setUp() throws Exception {
        runner = new UiTaskRunner();
        fx(() -> {
            controller = new PricingController(mock(SalesPipelineService.class), pieces,
                    mock(MaterialService.class), mock(EdgeBandingService.class),
                    mock(HardwareService.class), drafts, runner);
            var loader = new FXMLLoader(getClass().getResource("pricing-view.fxml"),
                    ResourceBundle.getBundle("com.woodlogix.desktop.i18n.messages", Locale.US));
            loader.setControllerFactory(type -> controller);
            loader.load();
            ReflectionTestUtils.setField(controller, "materials", List.of(original));
            materialCombo().getItems().setAll(original);
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        runner.shutdown();
    }

    @Test
    void createsAndEditsStandalonePieceAndKeepsInMemoryMaterialSnapshot() throws Exception {
        fx(() -> {
            materialCombo().getSelectionModel().select(original);
            fillForm();
            ReflectionTestUtils.invokeMethod(controller, "saveWorkpiece");
            var piece = table().getItems().get(0);
            assertNull(piece.getProjectWorkpieceId());
            assertEquals(original, piece.getMaterial());
            var draft = (EstimateDraftData) ReflectionTestUtils.invokeMethod(controller, "buildDraftData");
            assertNotNull(draft);
            assertEquals(original.id(), draft.workpieces().get(0).materialId());
            ReflectionTestUtils.setField(controller, "materials", List.of(changed));
            materialCombo().getItems().setAll(changed);
            ReflectionTestUtils.invokeMethod(controller, "editWorkpiece", piece);
            assertEquals(changed, materialCombo().getValue());
            assertEquals(bd("100"), piece.getMaterial().pricePerSheet());
            var core = (com.woodlogix.core.model.Workpiece)
                    ReflectionTestUtils.invokeMethod(controller, "toCore", piece);
            assertNotNull(core);
            assertEquals(100.0, core.getMaterial().getPricePerSheet());
            return null;
        });
        verify(drafts, timeout(5000).atLeastOnce()).saveActiveDraft(argThat(data ->
                data.projectId() == null && data.workpieces().size() == 1
                        && data.workpieces().get(0).projectWorkpieceId() == null
                        && data.workpieces().get(0).materialId().equals("material")));
    }

    @Test
    void restoresDraftUsingCurrentCatalogAndPreservesProjectAndMaterialIds() throws Exception {
        fx(() -> {
            ReflectionTestUtils.setField(controller, "materials", List.of(changed));
            materialCombo().getItems().setAll(changed);
            var project = new SalesProjectRow("project", "P", "C", null, null, "", bd("0"), SalesStage.CONTACT);
            projectCombo().getItems().setAll(project);
            var saved = new EstimateDraftData("project", bd("1"), bd("2"), bd("3"), bd("4"), bd("5"),
                    List.of(new EstimateDraftData.EstimateDraftWorkpieceData("piece", "Panel", bd("100"),
                            bd("200"), 2, "material", null, false, false, false, false)), List.of());
            ReflectionTestUtils.invokeMethod(controller, "restoreDraft", saved);
            var piece = table().getItems().get(0);
            assertEquals("piece", piece.getProjectWorkpieceId());
            assertEquals(changed, piece.getMaterial());
            var result = (EstimateDraftData) ReflectionTestUtils.invokeMethod(controller, "buildDraftData");
            assertNotNull(result);
            assertEquals("project", result.projectId());
            assertEquals("piece", result.workpieces().get(0).projectWorkpieceId());
            assertEquals("material", result.workpieces().get(0).materialId());
            var standalone = new EstimateDraftData(null, bd("1"), bd("2"), bd("3"), bd("4"), bd("5"),
                    List.of(new EstimateDraftData.EstimateDraftWorkpieceData(null, "Standalone", bd("100"),
                            bd("200"), 1, "material", null, false, false, false, false)), List.of());
            ReflectionTestUtils.invokeMethod(controller, "restoreDraft", standalone);
            assertNull(table().getItems().get(0).getProjectWorkpieceId());
            assertEquals(changed, table().getItems().get(0).getMaterial());
            return null;
        });
    }

    @Test
    void savesProjectPieceAndMapsMaterialOutsideFxThread() throws Exception {
        var projectPiece = new ProjectWorkpieceRow("piece", "project", "Panel", bd("100"),
                bd("200"), 2, original, null, false, false, false, false);
        when(pieces.create(any())).thenAnswer(invocation -> {
            assertFalse(Platform.isFxApplicationThread());
            ProjectWorkpieceData data = invocation.getArgument(0);
            assertEquals("project", data.projectId());
            assertEquals("material", data.materialId());
            return projectPiece;
        });
        fx(() -> {
            ReflectionTestUtils.setField(controller, "restoringDraft", true);
            projectCombo().getItems().setAll(new SalesProjectRow("project", "P", "C", null, null,
                    "", bd("0"), SalesStage.CONTACT));
            projectCombo().getSelectionModel().select(0);
            ReflectionTestUtils.setField(controller, "restoringDraft", false);
            materialCombo().getSelectionModel().select(original);
            fillForm();
            ReflectionTestUtils.invokeMethod(controller, "saveWorkpiece");
            return null;
        });
        verify(drafts, timeout(5000).atLeastOnce()).saveActiveDraft(argThat(data ->
                "project".equals(data.projectId()) && data.workpieces().size() == 1
                        && "piece".equals(data.workpieces().get(0).projectWorkpieceId())
                        && "material".equals(data.workpieces().get(0).materialId())));
        verify(pieces, timeout(5000)).create(any());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (fx(() -> table().getItems().isEmpty()) && System.nanoTime() < deadline) Thread.sleep(20);
        fx(() -> {
            assertEquals(1, table().getItems().size());
            assertEquals("piece", table().getItems().get(0).getProjectWorkpieceId());
            assertEquals(original, table().getItems().get(0).getMaterial());
            var data = (EstimateDraftData) ReflectionTestUtils.invokeMethod(controller, "buildDraftData");
            assertNotNull(data);
            assertEquals("piece", data.workpieces().get(0).projectWorkpieceId());
            assertEquals("material", data.workpieces().get(0).materialId());
            return null;
        });
    }

    @Test
    void keepsEdgeBandingForPiecesWithAndWithoutItAndUsesCurrentCatalogOnRestore() throws Exception {
        fx(() -> {
            ReflectionTestUtils.setField(controller, "edgeBandings", List.of(edge));
            edgeCombo().getItems().setAll(edge);
            materialCombo().getSelectionModel().select(original);
            edgeCombo().getSelectionModel().select(edge);
            check("topEdgeCheckBox").setSelected(true);
            fillForm();
            ReflectionTestUtils.invokeMethod(controller, "saveWorkpiece");
            materialCombo().getSelectionModel().select(original);
            fillForm();
            ReflectionTestUtils.invokeMethod(controller, "saveWorkpiece");
            assertEquals(2, table().getItems().size());
            var withEdge = table().getItems().get(0);
            var withoutEdge = table().getItems().get(1);
            assertEquals(edge, withEdge.getEdgeBanding());
            assertTrue(withEdge.isEdgeTop());
            assertNull(withoutEdge.getEdgeBanding());
            var draft = (EstimateDraftData) ReflectionTestUtils.invokeMethod(controller, "buildDraftData");
            assertNotNull(draft);
            assertEquals("edge", draft.workpieces().get(0).edgeBandingId());
            assertNull(draft.workpieces().get(1).edgeBandingId());

            ReflectionTestUtils.invokeMethod(controller, "editWorkpiece", withEdge);
            assertEquals(edge, edgeCombo().getValue());
            ReflectionTestUtils.invokeMethod(controller, "editWorkpiece", withoutEdge);
            assertNull(edgeCombo().getValue());
            var core = (com.woodlogix.core.model.Workpiece)
                    ReflectionTestUtils.invokeMethod(controller, "toCore", withEdge);
            assertNotNull(core);
            assertEquals(2.5, core.getEdgeBanding().getPricePerLinearMeter());

            ReflectionTestUtils.setField(controller, "edgeBandings", List.of(edgeChanged));
            edgeCombo().getItems().setAll(edgeChanged);
            var saved = new EstimateDraftData(null, bd("1"), bd("2"), bd("3"), bd("4"), bd("5"),
                    List.of(new EstimateDraftData.EstimateDraftWorkpieceData(null, "With", bd("100"),
                                    bd("200"), 1, "material", "edge", true, false, false, false),
                            new EstimateDraftData.EstimateDraftWorkpieceData(null, "Without", bd("100"),
                                    bd("200"), 1, "material", null, false, false, false, false)), List.of());
            ReflectionTestUtils.invokeMethod(controller, "restoreDraft", saved);
            assertEquals(edgeChanged, table().getItems().get(0).getEdgeBanding());
            assertNull(table().getItems().get(1).getEdgeBanding());
            var restored = (EstimateDraftData) ReflectionTestUtils.invokeMethod(controller, "buildDraftData");
            assertNotNull(restored);
            assertEquals("edge", restored.workpieces().get(0).edgeBandingId());
            assertNull(restored.workpieces().get(1).edgeBandingId());
            return null;
        });
    }

    @Test
    void restoresHardwareUsageUsingCurrentCatalogValue() throws Exception {
        fx(() -> {
            var currentHardware = new HardwareRow("hinge", "Current hinge", "PAIR", bd("25"));
            ReflectionTestUtils.setField(controller, "hardwares", List.of(currentHardware));
            var saved = new EstimateDraftData(null, bd("1"), bd("2"), bd("3"), bd("4"), bd("5"),
                    List.of(), List.of(new EstimateDraftData.EstimateDraftHardwareData("hinge", 2)));

            ReflectionTestUtils.invokeMethod(controller, "restoreDraft", saved);

            @SuppressWarnings("unchecked")
            var usages = (List<HardwareUsage>) ReflectionTestUtils.getField(
                    controller, "hardwareUsages");
            assertEquals(1, usages.size());
            assertEquals("Current hinge", usages.get(0).getHardware().getName());
            assertEquals(25.0, usages.get(0).getHardware().getUnitPrice());
            assertEquals(2, usages.get(0).getQuantity());
            return null;
        });
    }

    @Test
    void mapsProjectPieceEdgeBandingOnlyWhenPresent() {
        var withEdge = new ProjectWorkpieceRow("piece", "project", "Panel", bd("100"),
                bd("200"), 1, original, edge, true, false, false, false);
        var withoutEdge = new ProjectWorkpieceRow("piece-2", "project", "Shelf", bd("100"),
                bd("200"), 1, original, null, false, false, false, false);
        assertEquals(edge, PricingWorkpiece.fromProject(withEdge).getEdgeBanding());
        assertNull(PricingWorkpiece.fromProject(withoutEdge).getEdgeBanding());
    }

    private void fillForm() {
        field("nameField").setText("Panel");
        field("lengthField").setText("100");
        field("widthField").setText("200");
        field("quantityField").setText("2");
    }

    private TextField field(String name) { return (TextField) ReflectionTestUtils.getField(controller, name); }
    private CheckBox check(String name) { return (CheckBox) ReflectionTestUtils.getField(controller, name); }
    @SuppressWarnings("unchecked")
    private ComboBox<EdgeBandingRow> edgeCombo() { return (ComboBox<EdgeBandingRow>) ReflectionTestUtils.getField(controller, "edgeBandingComboBox"); }
    @SuppressWarnings("unchecked")
    private ComboBox<MaterialRow> materialCombo() { return (ComboBox<MaterialRow>) ReflectionTestUtils.getField(controller, "materialComboBox"); }
    @SuppressWarnings("unchecked")
    private ComboBox<SalesProjectRow> projectCombo() { return (ComboBox<SalesProjectRow>) ReflectionTestUtils.getField(controller, "projectComboBox"); }
    @SuppressWarnings("unchecked")
    private TableView<PricingWorkpiece> table() { return (TableView<PricingWorkpiece>) ReflectionTestUtils.getField(controller, "workpieceTable"); }
    private static BigDecimal bd(String value) { return new BigDecimal(value); }
    private static <T> T fx(java.util.concurrent.Callable<T> operation) throws Exception {
        var task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }
}
