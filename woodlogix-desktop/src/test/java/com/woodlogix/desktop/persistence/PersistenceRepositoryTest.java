package com.woodlogix.desktop.persistence;

import com.woodlogix.core.enums.MaterialType;
import com.woodlogix.core.enums.RoomType;
import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.core.enums.VisibleEdge;
import com.woodlogix.desktop.WoodLogixApplication;
import com.woodlogix.desktop.persistence.entity.CustomerEntity;
import com.woodlogix.desktop.persistence.entity.FurniturePartEntity;
import com.woodlogix.desktop.persistence.entity.FurnitureItemEntity;
import com.woodlogix.desktop.persistence.entity.LocalDateTimeText;
import com.woodlogix.desktop.persistence.entity.MaterialTypeConverter;
import com.woodlogix.desktop.persistence.entity.ProjectEnvironmentEntity;
import com.woodlogix.desktop.persistence.repository.CustomerRepository;
import com.woodlogix.desktop.persistence.repository.FurniturePartRepository;
import com.woodlogix.desktop.persistence.repository.ProjectEnvironmentRepository;
import com.woodlogix.desktop.service.ProposalPdfService;
import com.woodlogix.desktop.service.CustomerService;
import com.woodlogix.desktop.service.CustomerData;
import com.woodlogix.desktop.service.EnvironmentService;
import com.woodlogix.desktop.service.EnvironmentData;
import com.woodlogix.desktop.service.EnvironmentRow;
import com.woodlogix.desktop.service.FurniturePartService;
import com.woodlogix.desktop.service.FurniturePartData;
import com.woodlogix.desktop.service.FurnitureItemData;
import com.woodlogix.desktop.service.FurnitureItemService;
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
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--woodlogix.database.path="
                                + temporaryDirectory.resolve("woodlogix-test.db")
                );
        customerRepository = applicationContext.getBean(CustomerRepository.class);
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
    void servicesRejectInvalidCustomerAndPartDataBeforePersistence() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        EnvironmentService environmentService = applicationContext.getBean(EnvironmentService.class);
        FurniturePartService partService = applicationContext.getBean(FurniturePartService.class);

        assertThrows(ConstraintViolationException.class, () -> customerService.create(
                new CustomerData(" ", "invalid phone", "invalid-email", null)));
        assertTrue(customerRepository.findAll().isEmpty());

        assertThrows(ConstraintViolationException.class, () -> environmentService.create(
                new EnvironmentData("missing", null)));
        assertThrows(ConstraintViolationException.class, () -> partService.update(
                "missing", new FurniturePartData("missing", "Shelf", BigDecimal.ZERO,
                        new BigDecimal("300"), new BigDecimal("18"),
                        MaterialType.MDF, Set.of())));
    }

    @Test
    void createsFurniturePartAndAssociatesItWithItsEnvironment() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        EnvironmentService environmentService = applicationContext.getBean(EnvironmentService.class);
        FurniturePartService partService = applicationContext.getBean(FurniturePartService.class);
        var customer = customerService.create(
                new CustomerData("Part owner", null, null, null));
        var environment = environmentService.create(
                new EnvironmentData(customer.id(), RoomType.KITCHEN.name()));

        FurniturePartEntity part = partService.create(new FurniturePartData(
                environment.id(), "Countertop", new BigDecimal("2400"),
                new BigDecimal("600"), new BigDecimal("38"),
                MaterialType.PLYWOOD, Set.of(VisibleEdge.TOP)
        ));

        assertEquals(environment.id(), part.getProjectEnvironment().getId());
        assertEquals(List.of(part.getId()), partService.findAllByEnvironment(environment.id())
                .stream().map(FurniturePartEntity::getId).toList());
        assertEquals(List.of(part.getId()), partService
                .searchByEnvironment(environment.id(), "COUNTER")
                .stream().map(FurniturePartEntity::getId).toList());
        assertTrue(partService.searchByEnvironment(environment.id(), "not-found").isEmpty());
        assertEquals(MaterialType.PLYWOOD, part.getMaterial());
    }

    @Test
    void furniturePartCreationRejectsUnknownEnvironment() {
        FurniturePartService partService = applicationContext.getBean(FurniturePartService.class);

        assertThrows(EntityNotFoundException.class, () -> partService.create(
                new FurniturePartData("missing", "Shelf", new BigDecimal("800"),
                        new BigDecimal("300"), new BigDecimal("18"),
                        MaterialType.MDF, Set.of())
        ));
        assertTrue(furniturePartRepository.findAll().isEmpty());
    }

    @Test
    void furnitureItemsPersistDimensionsAndCustomerEnvironmentAssociations() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        EnvironmentService environmentService = applicationContext.getBean(EnvironmentService.class);
        FurnitureItemService itemService = applicationContext.getBean(FurnitureItemService.class);
        var customer = customerService.create(
                new CustomerData("Furniture item owner", null, null, null));
        var environment = environmentService.create(
                new EnvironmentData(customer.id(), RoomType.KITCHEN.name()));

        FurnitureItemEntity item = itemService.create(new FurnitureItemData(
                customer.id(), environment.id(), "Base cabinet", "Kitchen cabinet", "Oak",
                new BigDecimal("1800"), new BigDecimal("900"), new BigDecimal("600")
        ));

        assertEquals(customer.id(), item.getCustomer().getId());
        assertEquals(environment.id(), item.getEnvironment().getId());
        assertEquals(new BigDecimal("1800"), item.getLengthMm());
        assertEquals(new BigDecimal("900"), item.getHeightMm());
        assertEquals(new BigDecimal("600"), item.getDepthMm());
        assertEquals(List.of(item.getId()), itemService.findByCustomer(customer.id())
                .stream().map(FurnitureItemEntity::getId).toList());
        assertEquals(List.of(item.getId()), itemService.searchByCustomer(
                        customer.id(), "CABINET").stream()
                .map(FurnitureItemEntity::getId).toList());
        assertTrue(itemService.searchByCustomer(customer.id(), "not-found").isEmpty());
        assertEquals(List.of(item.getId()), itemService.findByEnvironment(environment.id())
                .stream().map(FurnitureItemEntity::getId).toList());

        FurnitureItemEntity updated = itemService.update(item.getId(), new FurnitureItemData(
                customer.id(), null, "Updated cabinet", "Wall cabinet", "Pine",
                new BigDecimal("1200"), new BigDecimal("700"), new BigDecimal("350")
        ));
        assertEquals("Updated cabinet", updated.getName());
        assertEquals("Pine", updated.getWoodType());
        assertNull(updated.getEnvironment());

        itemService.delete(item.getId());
        assertTrue(itemService.findByCustomer(customer.id()).isEmpty());
    }

    @Test
    void furnitureItemRejectsEnvironmentOwnedByAnotherCustomer() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        EnvironmentService environmentService = applicationContext.getBean(EnvironmentService.class);
        FurnitureItemService itemService = applicationContext.getBean(FurnitureItemService.class);
        var firstCustomer = customerService.create(
                new CustomerData("First customer", null, null, null));
        var secondCustomer = customerService.create(
                new CustomerData("Second customer", null, null, null));
        var environment = environmentService.create(
                new EnvironmentData(secondCustomer.id(), RoomType.KITCHEN.name()));

        assertThrows(IllegalArgumentException.class, () -> itemService.create(
                new FurnitureItemData(firstCustomer.id(), environment.id(), "Cabinet",
                        "Kitchen cabinet", "Oak", new BigDecimal("1000"),
                        new BigDecimal("800"), new BigDecimal("500"))
        ));
        assertTrue(itemService.findByCustomer(firstCustomer.id()).isEmpty());
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
    void updatesAndDeletesCustomerEnvironmentAndFurniturePart() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        EnvironmentService environmentService = applicationContext.getBean(EnvironmentService.class);
        FurniturePartService partService = applicationContext.getBean(FurniturePartService.class);

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

        FurniturePartEntity part = furniturePartRepository.saveAndFlush(new FurniturePartEntity(
                projectEnvironmentRepository.findById(environment.id()).orElseThrow(),
                "Shelf",
                new BigDecimal("800"),
                new BigDecimal("300"),
                new BigDecimal("18"),
                MaterialType.MDF,
                Set.of(VisibleEdge.TOP)
        ));
        FurniturePartEntity updatedPart = partService.update(
                part.getId(),
                new FurniturePartData(environment.id(), "Updated shelf",
                        new BigDecimal("900"), new BigDecimal("350"), new BigDecimal("18"),
                        MaterialType.PLYWOOD, Set.of(VisibleEdge.TOP, VisibleEdge.BOTTOM))
        );
        assertEquals("Updated shelf", updatedPart.getName());
        assertEquals(MaterialType.PLYWOOD, updatedPart.getMaterial());
        assertEquals(new BigDecimal("0.700"), updatedPart.calculateEdgeBandingMeters());

        partService.delete(part.getId());
        environmentService.delete(environment.id());
        customerService.delete(customer.id());
        assertTrue(furniturePartRepository.findById(part.getId()).isEmpty());
        assertTrue(projectEnvironmentRepository.findById(environment.id()).isEmpty());
        assertTrue(customerRepository.findById(customer.id()).isEmpty());
    }

    @Test
    void updateAndDeleteFailWithEntityNotFoundForUnknownIds() {
        CustomerService customerService = applicationContext.getBean(CustomerService.class);
        EnvironmentService environmentService = applicationContext.getBean(EnvironmentService.class);
        FurniturePartService partService = applicationContext.getBean(FurniturePartService.class);

        assertThrows(EntityNotFoundException.class, () ->
                customerService.update("missing", new CustomerData("Name", null, null, null)));
        assertThrows(EntityNotFoundException.class, () -> customerService.delete("missing"));
        assertThrows(EntityNotFoundException.class, () ->
                environmentService.update("missing",
                        new EnvironmentData("missing", RoomType.KITCHEN.name())));
        assertThrows(EntityNotFoundException.class, () -> environmentService.delete("missing"));
        assertThrows(EntityNotFoundException.class, () -> partService.update(
                "missing", new FurniturePartData("missing", "Shelf", new BigDecimal("800"),
                        new BigDecimal("300"), new BigDecimal("18"),
                        MaterialType.MDF, Set.of())));
        assertThrows(EntityNotFoundException.class, () -> partService.delete("missing"));
    }
}
