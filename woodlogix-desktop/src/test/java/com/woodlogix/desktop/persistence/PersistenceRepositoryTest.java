package com.woodlogix.desktop.persistence;

import com.woodlogix.core.enums.MaterialType;
import com.woodlogix.core.enums.RoomType;
import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.core.enums.VisibleEdge;
import com.woodlogix.desktop.WoodLogixApplication;
import com.woodlogix.desktop.persistence.entity.CustomerEntity;
import com.woodlogix.desktop.persistence.entity.FurniturePartEntity;
import com.woodlogix.desktop.persistence.entity.FurnitureItemEntity;
import com.woodlogix.desktop.persistence.entity.EdgeBandingEntity;
import com.woodlogix.desktop.persistence.entity.EstimateDraftEntity;
import com.woodlogix.desktop.persistence.entity.HardwareEntity;
import com.woodlogix.desktop.persistence.entity.LocalDateTimeText;
import com.woodlogix.desktop.persistence.entity.MaterialEntity;
import com.woodlogix.desktop.persistence.entity.MaterialTypeConverter;
import com.woodlogix.desktop.persistence.entity.ProjectWorkpieceEntity;
import com.woodlogix.desktop.persistence.entity.ProjectEnvironmentEntity;
import com.woodlogix.desktop.persistence.repository.CustomerRepository;
import com.woodlogix.desktop.persistence.repository.FurnitureItemRepository;
import com.woodlogix.desktop.persistence.repository.FurniturePartRepository;
import com.woodlogix.desktop.persistence.repository.ProjectEnvironmentRepository;
import com.woodlogix.desktop.persistence.repository.EdgeBandingRepository;
import com.woodlogix.desktop.persistence.repository.EstimateDraftRepository;
import com.woodlogix.desktop.persistence.repository.HardwareRepository;
import com.woodlogix.desktop.persistence.repository.MaterialRepository;
import com.woodlogix.desktop.persistence.repository.ProjectWorkpieceRepository;
import com.woodlogix.desktop.service.ProposalPdfService;
import com.woodlogix.desktop.service.CustomerService;
import com.woodlogix.desktop.service.CustomerData;
import com.woodlogix.desktop.service.EnvironmentService;
import com.woodlogix.desktop.service.EnvironmentData;
import com.woodlogix.desktop.service.EnvironmentRow;
import com.woodlogix.desktop.service.EdgeBandingData;
import com.woodlogix.desktop.service.EdgeBandingService;
import com.woodlogix.desktop.service.EstimateDraftData;
import com.woodlogix.desktop.service.EstimateDraftService;
import com.woodlogix.desktop.service.HardwareData;
import com.woodlogix.desktop.service.HardwareService;
import com.woodlogix.desktop.service.MaterialData;
import com.woodlogix.desktop.service.MaterialService;
import com.woodlogix.desktop.service.ProjectWorkpieceData;
import com.woodlogix.desktop.service.ProjectWorkpieceService;
import com.woodlogix.desktop.service.SalesProjectData;
import com.woodlogix.desktop.service.SalesPipelineService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import jakarta.validation.ConstraintViolationException;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import jakarta.persistence.EntityNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    private ConfigurableApplicationContext applicationContext;
    private CustomerRepository customerRepository;
    private FurnitureItemRepository furnitureItemRepository;
    private ProjectEnvironmentRepository projectEnvironmentRepository;
    private FurniturePartRepository furniturePartRepository;

    @BeforeEach
    void setUp() {
        applicationContext = new SpringApplicationBuilder(WoodLogixApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.main.banner-mode=off",
                        "--logging.level.root=ERROR",
                        "--logging.file.name=",
                        "--woodlogix.database.path="
                                + temporaryDirectory.resolve("woodlogix-test.db")
                );
        customerRepository = applicationContext.getBean(CustomerRepository.class);
        furnitureItemRepository = applicationContext.getBean(FurnitureItemRepository.class);
        projectEnvironmentRepository =
                applicationContext.getBean(ProjectEnvironmentRepository.class);
        furniturePartRepository = applicationContext.getBean(FurniturePartRepository.class);
    }

    @AfterEach
    void tearDown() {
        applicationContext.close();
    }

    @Test
    void persistsClientEnvironmentAndPartWithVisibleEdges() {
        CustomerEntity customer = customerRepository.saveAndFlush(
                new CustomerEntity("Test Customer", "11999999999", "test@example.test",
                        "Test street")
        );
        ProjectEnvironmentEntity projectEnvironment = projectEnvironmentRepository.saveAndFlush(
                new ProjectEnvironmentEntity(customer, "Kitchen")
        );
        FurniturePartEntity part = furniturePartRepository.saveAndFlush(new FurniturePartEntity(
                projectEnvironment,
                "Side panel",
                new BigDecimal("1200"),
                new BigDecimal("600"),
                new BigDecimal("18"),
                MaterialType.MDF,
                Set.of(VisibleEdge.TOP, VisibleEdge.LEFT)
        ));

        FurniturePartEntity persistedPart =
                furniturePartRepository.findById(part.getId()).orElseThrow();

        assertEquals(customer.getId(), projectEnvironmentRepository
                .findAllByCustomerId(customer.getId()).get(0).getCustomer().getId());
        assertEquals(Set.of(VisibleEdge.TOP, VisibleEdge.LEFT), persistedPart.getVisibleEdges());
        assertEquals(new BigDecimal("1.800"), persistedPart.calculateEdgeBandingMeters());
        assertEquals(MaterialType.MDF, persistedPart.getMaterial());
        assertEquals(1, furniturePartRepository
                .findAllByProjectEnvironmentIdOrderByNameAsc(projectEnvironment.getId()).size());
    }

    @Test
    void persistsPhaseTwoSupplyCatalogsAndProjectWorkpiecesWithCrudAndReferences() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        SalesPipelineService salesPipelineService =
                applicationContext.getBean(SalesPipelineService.class);
        MaterialService materialService = applicationContext.getBean(MaterialService.class);
        EdgeBandingService edgeBandingService =
                applicationContext.getBean(EdgeBandingService.class);
        HardwareService hardwareService = applicationContext.getBean(HardwareService.class);
        ProjectWorkpieceService workpieceService =
                applicationContext.getBean(ProjectWorkpieceService.class);
        MaterialRepository materialRepository =
                applicationContext.getBean(MaterialRepository.class);
        EdgeBandingRepository edgeBandingRepository =
                applicationContext.getBean(EdgeBandingRepository.class);
        HardwareRepository hardwareRepository =
                applicationContext.getBean(HardwareRepository.class);
        ProjectWorkpieceRepository workpieceRepository =
                applicationContext.getBean(ProjectWorkpieceRepository.class);

        var customer = customerService.create(
                new CustomerData("Phase two customer", null, null, null));
        var project = salesPipelineService.create(new SalesProjectData(
                customer.id(), null, "Kitchen project", new BigDecimal("1000")));
        var material = materialService.create(new MaterialData(
                "MDF", "White", new BigDecimal("18"), new BigDecimal("2750"),
                new BigDecimal("1840"), new BigDecimal("200.00")));
        var edgeBanding = edgeBandingService.create(new EdgeBandingData(
                "PVC white", new BigDecimal("22"), new BigDecimal("3.50")));
        var hardware = hardwareService.create(new HardwareData(
                "Hinge", "PAIR", new BigDecimal("12.00")));
        var workpiece = workpieceService.create(new ProjectWorkpieceData(
                project.id(), "Door panel", new BigDecimal("700"),
                new BigDecimal("500"), 2, material.getId(), edgeBanding.getId(),
                true, true, false, false));

        assertEquals(1, materialService.findAll().size());
        assertEquals(1, edgeBandingService.findAll().size());
        assertEquals(1, hardwareService.findAll().size());
        assertEquals(1, workpieceService.findByProject(project.id()).size());
        assertEquals(project.id(), workpiece.getProject().getId());
        assertEquals(material.getId(), workpiece.getMaterial().getId());
        assertEquals(edgeBanding.getId(), workpiece.getEdgeBanding().getId());
        assertThrows(IllegalStateException.class, () -> materialService.delete(material.getId()));
        assertThrows(IllegalStateException.class,
                () -> edgeBandingService.delete(edgeBanding.getId()));

        materialService.update(material.getId(), new MaterialData(
                "MDF premium", "White", new BigDecimal("18"), new BigDecimal("2750"),
                new BigDecimal("1840"), new BigDecimal("225.00")));
        edgeBandingService.update(edgeBanding.getId(), new EdgeBandingData(
                "PVC premium", new BigDecimal("22"), new BigDecimal("4.00")));
        hardwareService.update(hardware.getId(), new HardwareData(
                "Soft-close hinge", "PAIR", new BigDecimal("15.00")));
        workpieceService.update(workpiece.getId(), new ProjectWorkpieceData(
                project.id(), "Updated door panel", new BigDecimal("710"),
                new BigDecimal("510"), 3, material.getId(), edgeBanding.getId(),
                true, false, false, true));

        assertEquals("MDF premium", materialService.findAll().get(0).getName());
        assertEquals("PVC premium", edgeBandingService.findAll().get(0).getName());
        assertEquals("Soft-close hinge", hardwareService.findAll().get(0).getName());
        assertEquals("Updated door panel",
                workpieceService.findByProject(project.id()).get(0).getName());

        workpieceService.delete(workpiece.getId());
        materialService.delete(material.getId());
        edgeBandingService.delete(edgeBanding.getId());
        hardwareService.delete(hardware.getId());
        assertTrue(workpieceRepository.findAll().isEmpty());
        assertTrue(materialRepository.findAll().isEmpty());
        assertTrue(edgeBandingRepository.findAll().isEmpty());
        assertTrue(hardwareRepository.findAll().isEmpty());
    }

    @Test
    void persistsActiveEstimateDraftWithOptionalProjectAndOperatingParameters() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        SalesPipelineService salesPipelineService =
                applicationContext.getBean(SalesPipelineService.class);
        MaterialService materialService = applicationContext.getBean(MaterialService.class);
        EdgeBandingService edgeBandingService =
                applicationContext.getBean(EdgeBandingService.class);
        HardwareService hardwareService = applicationContext.getBean(HardwareService.class);
        EstimateDraftService draftService = applicationContext.getBean(EstimateDraftService.class);
        EstimateDraftRepository draftRepository =
                applicationContext.getBean(EstimateDraftRepository.class);
        var material = materialService.create(new MaterialData(
                "Draft MDF", "Natural", new BigDecimal("18"), new BigDecimal("2750"),
                new BigDecimal("1840"), new BigDecimal("215.00")));
        var edgeBanding = edgeBandingService.create(new EdgeBandingData(
                "Draft PVC", new BigDecimal("22"), new BigDecimal("3.20")));
        var hardware = hardwareService.create(new HardwareData(
                "Draft hinge", "PAIR", new BigDecimal("14.50")));
        var workpiece = new EstimateDraftData.EstimateDraftWorkpieceData(
                null, "Counter panel", new BigDecimal("1200"), new BigDecimal("600"),
                2, material.getId(), edgeBanding.getId(), true, false, true, false);
        var hardwareUsage = new EstimateDraftData.EstimateDraftHardwareData(
                hardware.getId(), 4);
        EstimateDraftData freeDraft = new EstimateDraftData(
                null, new BigDecimal("12.5"), new BigDecimal("3.5"),
                new BigDecimal("42"), new BigDecimal("18"), new BigDecimal("35"),
                List.of(workpiece), List.of(hardwareUsage));

        draftService.saveActiveDraft(freeDraft);

        EstimateDraftEntity persisted = draftRepository.findById(
                EstimateDraftEntity.ACTIVE_DRAFT_ID).orElseThrow();
        EstimateDraftData restored = draftService.findActiveDraft().orElseThrow();
        assertNull(persisted.getProject());
        assertEquals(new BigDecimal("12.5"), persisted.getWasteFactor());
        assertEquals(new BigDecimal("3.5"), persisted.getEstimatedHours());
        assertEquals(new BigDecimal("42"), persisted.getHourlyRate());
        assertEquals(new BigDecimal("18"), persisted.getMachineRate());
        assertEquals(new BigDecimal("35"), persisted.getProfitMargin());
        assertEquals(freeDraft, restored);
        assertThrows(IllegalStateException.class, () -> materialService.delete(material.getId()));
        assertThrows(IllegalStateException.class,
                () -> edgeBandingService.delete(edgeBanding.getId()));
        assertThrows(IllegalStateException.class, () -> hardwareService.delete(hardware.getId()));

        var customer = customerService.create(
                new CustomerData("Draft project customer", null, null, null));
        var project = salesPipelineService.create(new SalesProjectData(
                customer.id(), null, "Draft project", new BigDecimal("1200")));
        draftService.saveActiveDraft(new EstimateDraftData(
                project.id(), freeDraft.wasteFactor(), freeDraft.estimatedHours(),
                freeDraft.hourlyRate(), freeDraft.machineRate(), freeDraft.profitMargin(),
                freeDraft.workpieces(), freeDraft.hardwares()));

        assertEquals(project.id(), draftService.findActiveDraft().orElseThrow().projectId());
    }

    @Test
    void searchesCustomersByPartialNameEmailOrPhoneIgnoringCase() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        var first = customerService.create(
                new CustomerData("Ana Carvalho", "11999991111", "ana@example.com", null));
        var second = customerService.create(
                new CustomerData("Bruno Lima", "21988887777", "bruno@wood.test", null));
        customerService.create(
                new CustomerData("Carla Souza", null, "carla@example.org", null));

        assertEquals(List.of(first.id()), customerService.search("CARVALHO").stream()
                .map(row -> row.id()).toList());
        assertEquals(List.of(first.id()), customerService.search("ANA@EXAMPLE").stream()
                .map(row -> row.id()).toList());
        assertEquals(List.of(first.id()), customerService.findByName("carvalho").stream()
                .map(row -> row.id()).toList());
        assertEquals(List.of(second.id()), customerService.search("8888").stream()
                .map(row -> row.id()).toList());
        assertEquals(3, customerService.search("  ").size());
        assertTrue(customerService.search("not-found").isEmpty());
    }

    @Test
    void createsSelectedRoomTypesForCustomerInOneOperation() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        EnvironmentService environmentService = applicationContext.getBean(EnvironmentService.class);
        var customer = customerService.create(
                new CustomerData("Room catalog customer", null, null, null));

        List<EnvironmentRow> created = environmentService.createForCustomer(customer.id(), List.of(
                RoomType.KITCHEN, RoomType.MASTER_BEDROOM, RoomType.BATHROOM,
                RoomType.KITCHEN
        ));
        List<EnvironmentRow> additional = environmentService.createForCustomer(customer.id(),
                List.of(RoomType.KITCHEN, RoomType.BATHROOM, RoomType.SINGLE_BEDROOM));

        assertEquals(3, created.size());
        assertEquals(1, additional.size());
        assertEquals(RoomType.SINGLE_BEDROOM.name(), additional.get(0).type());
        assertEquals(Set.of("KITCHEN", "MASTER_BEDROOM", "BATHROOM"),
                created.stream().map(EnvironmentRow::type).collect(java.util.stream.Collectors.toSet()));
        assertEquals(4, environmentService.findAll().size());
        assertTrue(environmentService.createForCustomer(customer.id(),
                List.of(RoomType.KITCHEN, RoomType.BATHROOM)).isEmpty());
        assertEquals(4, environmentService.findAll().size());
    }

    @Test
    void synchronizesCustomerEnvironmentsAsAnExactDeduplicatedList() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        EnvironmentService environmentService = applicationContext.getBean(EnvironmentService.class);
        var customer = customerService.create(
                new CustomerData("Environment synchronization customer", null, null, null));
        List<EnvironmentRow> initial = environmentService.saveOrUpdateCustomerEnvironments(
                customer.id(), List.of("Kitchen", "Bedroom", "Bathroom", "kitchen"));
        String kitchenId = initial.stream()
                .filter(row -> row.type().equals("Kitchen"))
                .findFirst().orElseThrow().id();

        List<EnvironmentRow> updated = environmentService.saveOrUpdateCustomerEnvironments(
                customer.id(), List.of(" Kitchen ", "Pantry", "BATHROOM"));

        assertEquals(3, initial.size());
        assertEquals(Set.of("Kitchen", "Pantry", "BATHROOM"),
                updated.stream().map(EnvironmentRow::type)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(kitchenId, updated.stream()
                .filter(row -> row.type().equals("Kitchen"))
                .findFirst().orElseThrow().id());
        assertEquals(3, environmentService.findByCustomer(customer.id()).size());

        assertTrue(environmentService.saveOrUpdateCustomerEnvironments(
                customer.id(), List.of()).isEmpty());
        assertTrue(environmentService.findByCustomer(customer.id()).isEmpty());
    }

    @Test
    void deletesAllEnvironmentsForCustomerInOneOperation() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        EnvironmentService environmentService = applicationContext.getBean(EnvironmentService.class);
        var customer = customerService.create(
                new CustomerData("Bulk environment deletion customer", null, null, null));
        environmentService.saveOrUpdateCustomerEnvironments(
                customer.id(), List.of("Kitchen", "Bedroom", "Bathroom"));

        environmentService.deleteAllByCustomer(customer.id());

        assertTrue(environmentService.findByCustomer(customer.id()).isEmpty());
    }

    @Test
    void environmentQueriesReturnUniqueRoomsForCustomer() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        EnvironmentService environmentService = applicationContext.getBean(EnvironmentService.class);
        var customer = customerService.create(
                new CustomerData("Duplicate environment customer", null, null, null));
        projectEnvironmentRepository.saveAllAndFlush(List.of(
                new ProjectEnvironmentEntity(
                        customerRepository.findById(customer.id()).orElseThrow(), "Closet"),
                new ProjectEnvironmentEntity(
                        customerRepository.findById(customer.id()).orElseThrow(), " closet ")
        ));

        assertEquals(1, environmentService.findByCustomer(customer.id()).size());
        assertEquals(1, environmentService.findAll().size());
    }

    @Test
    void environmentBatchReplacementRollsBackWhenRemovedRoomHasParts() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        EnvironmentService environmentService = applicationContext.getBean(EnvironmentService.class);
        var customer = customerService.create(
                new CustomerData("Referenced environment customer", null, null, null));
        EnvironmentRow environment = environmentService.saveOrUpdateCustomerEnvironments(
                customer.id(), List.of("Kitchen")).get(0);
        ProjectEnvironmentEntity environmentEntity = projectEnvironmentRepository
                .findById(environment.id()).orElseThrow();
        furniturePartRepository.saveAndFlush(new FurniturePartEntity(
                environmentEntity, "Counter panel", new BigDecimal("800"),
                new BigDecimal("500"), new BigDecimal("18"), MaterialType.MDF, Set.of()
        ));

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                environmentService.saveOrUpdateCustomerEnvironments(customer.id(), List.of()));

        assertEquals("error.environment-has-related-records", failure.getMessage());
        assertEquals(List.of(environment.id()), environmentService.findByCustomer(customer.id())
                .stream().map(EnvironmentRow::id).toList());
        assertEquals(1, furniturePartRepository.findAll().size());
    }

    @Test
    void roomCatalogCreationRejectsUnknownCustomerWithoutCreatingEnvironments() {
        EnvironmentService environmentService = applicationContext.getBean(EnvironmentService.class);

        assertThrows(EntityNotFoundException.class, () -> environmentService.createForCustomer(
                "missing", List.of(RoomType.KITCHEN, RoomType.BATHROOM)));
        assertTrue(environmentService.findAll().isEmpty());
    }

    @Test
    void roomCatalogCreationRequiresAtLeastOneRoom() {
        EnvironmentService environmentService = applicationContext.getBean(EnvironmentService.class);
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        var customer = customerService.create(
                new CustomerData("Empty room selection", null, null, null));

        assertThrows(ConstraintViolationException.class, () ->
                environmentService.createForCustomer(customer.id(), List.of()));
        assertTrue(environmentService.findAll().isEmpty());
    }

    @Test
    void createsCustomRoomAndPreventsDuplicatesAgainstCatalogAndExistingRooms() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        EnvironmentService environmentService = applicationContext.getBean(EnvironmentService.class);
        var customer = customerService.create(
                new CustomerData("Custom room customer", null, null, null));

        List<EnvironmentRow> created = environmentService.createForCustomer(customer.id(),
                List.of(RoomType.KITCHEN), " Pantry ");
        List<EnvironmentRow> duplicates = environmentService.createForCustomer(customer.id(),
                List.of(RoomType.KITCHEN), "PANTRY");
        List<EnvironmentRow> catalogDuplicate = environmentService.createForCustomer(customer.id(),
                List.of(), "kitchen");

        assertEquals(2, created.size());
        assertTrue(created.stream().anyMatch(row -> row.type().equals("Pantry")));
        assertTrue(duplicates.isEmpty());
        assertTrue(catalogDuplicate.isEmpty());
        assertEquals(2, environmentService.findAll().size());
    }

    @Test
    void rejectsNonPositivePartDimensions() {
        CustomerEntity customer = customerRepository.saveAndFlush(
                new CustomerEntity("Test Customer", null, null, null)
        );
        ProjectEnvironmentEntity projectEnvironment = projectEnvironmentRepository.saveAndFlush(
                new ProjectEnvironmentEntity(customer, "Bedroom")
        );

        assertThrows(IllegalArgumentException.class, () -> new FurniturePartEntity(
                projectEnvironment,
                "Shelf",
                BigDecimal.ZERO,
                new BigDecimal("300"),
                new BigDecimal("18"),
                MaterialType.PLYWOOD,
                Set.of()
        ));
    }

    @Test
    void servicesRejectInvalidCustomerAndEnvironmentDataBeforePersistence() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        EnvironmentService environmentService = applicationContext.getBean(EnvironmentService.class);

        assertThrows(ConstraintViolationException.class, () -> customerService.create(
                new CustomerData(" ", "invalid phone", "invalid-email", null)));
        assertTrue(customerRepository.findAll().isEmpty());

        assertThrows(ConstraintViolationException.class, () -> environmentService.create(
                new EnvironmentData("missing", null)));
    }

    @Test
    void legacyFurnitureRecordsRemainPersistedWithoutRegistrationModule() {
        CustomerEntity customer = customerRepository.saveAndFlush(
                new CustomerEntity("Legacy furniture owner", null, null, null));
        ProjectEnvironmentEntity environment = projectEnvironmentRepository.saveAndFlush(
                new ProjectEnvironmentEntity(customer, RoomType.KITCHEN.name()));
        FurnitureItemEntity item = furnitureItemRepository.saveAndFlush(
                new FurnitureItemEntity(customer, environment, "Base cabinet",
                        "Kitchen cabinet", "Oak", new BigDecimal("1800"),
                        new BigDecimal("900"), new BigDecimal("600")));

        FurnitureItemEntity persisted = furnitureItemRepository.findById(item.getId())
                .orElseThrow();
        assertEquals(customer.getId(), persisted.getCustomer().getId());
        assertEquals(environment.getId(), persisted.getEnvironment().getId());
        assertEquals(new BigDecimal("1800"), persisted.getLengthMm());
        assertEquals(new BigDecimal("900"), persisted.getHeightMm());
        assertEquals(new BigDecimal("600"), persisted.getDepthMm());
        assertEquals(1, furnitureItemRepository.findAllByCustomerIdOrderByNameAsc(
                customer.getId()).size());
    }

    @Test
    void entityConstraintsAreAlsoEnforcedByJpaBeforePersistence() {
        assertThrows(ConstraintViolationException.class, () -> customerRepository.saveAndFlush(
                new CustomerEntity("Valid name", "bad-phone", "bad-email", null)));
        assertTrue(customerRepository.findAll().isEmpty());
    }

    @Test
    void rejectsNegativeSalesProjectTotalBeforePersistence() {
        SalesPipelineService salesPipelineService =
                applicationContext.getBean(SalesPipelineService.class);

        assertThrows(ConstraintViolationException.class, () -> salesPipelineService.create(
                new SalesProjectData("missing", null, "Project", new BigDecimal("-0.01"))));
    }

    @Test
    void readsLegacyPlywoodMaterialValue() {
        MaterialTypeConverter converter = new MaterialTypeConverter();

        assertEquals(MaterialType.PLYWOOD, converter.convertToEntityAttribute("COMPENSADO"));
        assertEquals("PLYWOOD", converter.convertToDatabaseColumn(MaterialType.PLYWOOD));
    }

    @Test
    void persistsAndReadsCustomerTimestampAsSqliteText() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 4, 21, 40, 6, 123_000_000);
        String storedValue = LocalDateTimeText.format(createdAt);
        CustomerEntity customer = customerRepository.saveAndFlush(
                new CustomerEntity("Timestamp test", null, null, null)
        );

        assertEquals("2026-11-04T21:40:06.123", storedValue);
        assertEquals(createdAt, LocalDateTimeText.parse(storedValue));
        assertEquals(LocalDateTime.of(2026, 11, 4, 21, 40, 6),
                LocalDateTimeText.parse("2026-11-04 21:40:06"));
        assertEquals(customer.getId(), customerRepository.findAll().get(0).getId());
        assertNotNull(customerRepository.findById(customer.getId()).orElseThrow().getCreatedAt());
    }

    @Test
    void createsAndMovesSalesProjectAndExportsProposalPdf() throws Exception {
        CustomerEntity customer = customerRepository.saveAndFlush(
                new CustomerEntity("Test Customer", "11999999999", "test@example.test",
                        "Test street")
        );
        ProjectEnvironmentEntity environment = projectEnvironmentRepository.saveAndFlush(
                new ProjectEnvironmentEntity(customer, "Kitchen")
        );
        SalesPipelineService salesPipelineService =
                applicationContext.getBean(SalesPipelineService.class);
        var project = salesPipelineService.create(
                new SalesProjectData(customer.getId(), environment.getId(), "Custom kitchen",
                        new BigDecimal("12500.00"))
        );

        assertEquals(SalesStage.CONTACT, project.stage());
        salesPipelineService.move(project.id(), SalesStage.QUOTE_SENT);
        assertEquals(SalesStage.QUOTE_SENT,
                salesPipelineService.findById(project.id()).stage());

        Path proposal = temporaryDirectory.resolve("proposal.pdf");
        applicationContext.getBean(ProposalPdfService.class).export(project.id(), proposal);
        assertTrue(java.nio.file.Files.size(proposal) > 0);
        try (var document = Loader.loadPDF(proposal.toFile())) {
            String proposalText = new PDFTextStripper().getText(document);
            assertTrue(proposalText.contains("PROPOSTA COMERCIAL"));
            assertTrue(proposalText.contains("Test Customer"));
            assertTrue(proposalText.contains("Custom kitchen"));
            assertTrue(proposalText.contains("R$"));
        }
    }

    @Test
    void updatesAndDeletesCustomerAndEnvironment() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        EnvironmentService environmentService = applicationContext.getBean(EnvironmentService.class);

        var customer = customerService.create(new CustomerData("Before update", null, null, null));
        var updatedCustomer = customerService.update(
                customer.id(), new CustomerData("After update", "11999999999",
                        "updated@example.test", "Updated address")
        );
        assertEquals("After update", updatedCustomer.fullName());
        assertEquals("updated@example.test", updatedCustomer.email());

        var environment = environmentService.create(
                new EnvironmentData(customer.id(), RoomType.KITCHEN.name()));
        var updatedEnvironment = environmentService.update(
                environment.id(), new EnvironmentData(customer.id(),
                        RoomType.SINGLE_BEDROOM.name())
        );
        assertEquals("SINGLE_BEDROOM", updatedEnvironment.type());

        environmentService.delete(environment.id());
        customerService.delete(customer.id());
        assertTrue(projectEnvironmentRepository.findById(environment.id()).isEmpty());
        assertTrue(customerRepository.findById(customer.id()).isEmpty());
    }

    @Test
    void updateAndDeleteFailWithEntityNotFoundForUnknownIds() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        EnvironmentService environmentService = applicationContext.getBean(EnvironmentService.class);
        assertThrows(EntityNotFoundException.class, () ->
                customerService.update("missing", new CustomerData("Name", null, null, null)));
        assertThrows(EntityNotFoundException.class, () -> customerService.delete("missing"));
        assertThrows(EntityNotFoundException.class, () ->
                environmentService.update("missing",
                        new EnvironmentData("missing", RoomType.KITCHEN.name())));
        assertThrows(EntityNotFoundException.class, () -> environmentService.delete("missing"));
    }
}
