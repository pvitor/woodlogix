package com.woodlogix.server.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.woodlogix.application.customer.CustomerCatalog;
import com.woodlogix.application.customer.CustomerInput;
import com.woodlogix.application.environment.EnvironmentCatalog;
import com.woodlogix.application.environment.EnvironmentCatalogPort;
import com.woodlogix.application.environment.EnvironmentRow;
import com.woodlogix.core.enums.MaterialType;
import com.woodlogix.core.enums.RoomType;
import com.woodlogix.core.enums.VisibleEdge;
import com.woodlogix.server.WoodLogixServerApplication;
import com.woodlogix.server.api.dto.ApiErrorResponse;
import com.woodlogix.server.api.dto.EnvironmentAddRequest;
import com.woodlogix.server.api.dto.EnvironmentReplaceRequest;
import com.woodlogix.server.api.dto.EnvironmentResponse;
import com.woodlogix.server.event.EnvironmentChangedEvent;
import com.woodlogix.server.persistence.entity.CustomerEntity;
import com.woodlogix.server.persistence.entity.FurnitureItemEntity;
import com.woodlogix.server.persistence.entity.FurniturePartEntity;
import com.woodlogix.server.persistence.entity.ProjectEnvironmentEntity;
import com.woodlogix.server.persistence.entity.SalesProjectEntity;
import com.woodlogix.server.persistence.repository.CustomerRepository;
import com.woodlogix.server.persistence.repository.FurnitureItemRepository;
import com.woodlogix.server.persistence.repository.FurniturePartRepository;
import com.woodlogix.server.persistence.repository.ProjectEnvironmentRepository;
import com.woodlogix.server.persistence.repository.SalesProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = WoodLogixServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@Import(EnvironmentControllerIntegrationTest.EventRecorder.class)
class EnvironmentControllerIntegrationTest {
    @TempDir
    static Path temporaryDirectory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerCatalog customerCatalog;

    @Autowired
    private EnvironmentCatalog environmentCatalog;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProjectEnvironmentRepository environmentRepository;

    @Autowired
    private FurniturePartRepository furniturePartRepository;

    @Autowired
    private FurnitureItemRepository furnitureItemRepository;

    @Autowired
    private SalesProjectRepository salesProjectRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EventRecorder eventRecorder;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("woodlogix.database.path",
                () -> temporaryDirectory.resolve("environments-api.db").toString());
        registry.add("spring.main.banner-mode", () -> "off");
        registry.add("logging.level.root", () -> "ERROR");
        registry.add("logging.file.name", () -> "");
    }

    @BeforeEach
    void clearFixtures() {
        jdbcTemplate.update("DELETE FROM furniture_part_visible_edges");
        jdbcTemplate.update("DELETE FROM furniture_parts");
        jdbcTemplate.update("DELETE FROM furniture_items");
        jdbcTemplate.update("DELETE FROM project_estimate_hardwares");
        jdbcTemplate.update("DELETE FROM project_estimate_workpieces");
        jdbcTemplate.update("DELETE FROM project_estimates");
        jdbcTemplate.update("DELETE FROM sales_projects");
        jdbcTemplate.update("DELETE FROM project_environments");
        jdbcTemplate.update("DELETE FROM customers");
        eventRecorder.events.clear();
    }

    @Test
    void listsAllAndFiltersByCustomer() throws Exception {
        String firstCustomer = customer("First");
        String secondCustomer = customer("Second");
        EnvironmentRow first = createEnvironment(firstCustomer, "Kitchen");
        EnvironmentRow second = createEnvironment(secondCustomer, "Bedroom");

        List<String> allIds = ids(mockMvc.perform(get("/api/v1/environments").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn());
        assertTrue(allIds.containsAll(List.of(first.id(), second.id())));
        assertEquals(List.of(first.id()),
                ids(mockMvc.perform(get("/api/v1/environments")
                                .param("customerId", firstCustomer).accept(APPLICATION_JSON))
                        .andExpect(status().isOk()).andReturn()));
        assertEquals(List.of(),
                ids(mockMvc.perform(get("/api/v1/environments")
                                .param("customerId", "missing").accept(APPLICATION_JSON))
                        .andExpect(status().isOk()).andReturn()));
    }

    @Test
    void createsEnumAndCustomEnvironmentsWithNormalizationAndDuplicates() throws Exception {
        String customerId = customer("Customer");
        EnvironmentAddRequest request = new EnvironmentAddRequest(
                List.of(RoomType.KITCHEN, RoomType.KITCHEN, RoomType.BATHROOM),
                " kitchen ");

        List<EnvironmentResponse> response = readResponses(mockMvc.perform(
                post("/api/v1/customers/{customerId}/environments", customerId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn());

        assertEquals(List.of("KITCHEN", "BATHROOM"),
                response.stream().map(EnvironmentResponse::type).toList());
        assertEquals(2, environmentRepository.count());
        assertEquals(1, eventRecorder.events.size());
    }

    @Test
    void replacesEnvironmentsCreatingRenamingRemovingAndPreservingIds() throws Exception {
        String customerId = customer("Customer");
        List<EnvironmentResponse> initial = readResponses(mockMvc.perform(
                put("/api/v1/customers/{customerId}/environments", customerId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new EnvironmentReplaceRequest(List.of("Kitchen", "Bedroom")))))
                .andExpect(status().isOk()).andReturn());
        String kitchenId = initial.stream()
                .filter(row -> row.type().equals("Kitchen")).findFirst().orElseThrow().id();
        String bedroomId = initial.stream()
                .filter(row -> row.type().equals("Bedroom")).findFirst().orElseThrow().id();

        List<EnvironmentResponse> updated = readResponses(mockMvc.perform(
                put("/api/v1/customers/{customerId}/environments", customerId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new EnvironmentReplaceRequest(
                                        List.of(" KITCHEN ", "Pantry", "pantry")))))
                .andExpect(status().isOk()).andReturn());

        assertTrue(updated.stream().anyMatch(row -> row.id().equals(kitchenId)
                && row.type().equals("KITCHEN")));
        assertTrue(updated.stream().anyMatch(row -> row.type().equals("Pantry")
                && !row.id().equals(kitchenId)));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_environments WHERE id = ?",
                Integer.class, bedroomId));
        assertEquals(2, environmentRepository.count());
        assertEquals(2, eventRecorder.events.size());
    }

    @Test
    void deletesIndividualEnvironmentAndAllEnvironmentsForCustomer() throws Exception {
        String firstCustomer = customer("First");
        String secondCustomer = customer("Second");
        EnvironmentRow first = createEnvironment(firstCustomer, "Kitchen");
        createEnvironment(firstCustomer, "Bedroom");
        EnvironmentRow other = createEnvironment(secondCustomer, "Kitchen");
        eventRecorder.events.clear();

        mockMvc.perform(delete("/api/v1/environments/{id}", first.id()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(delete("/api/v1/customers/{customerId}/environments", firstCustomer))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertEquals(List.of(other.id()), ids(mockMvc.perform(
                get("/api/v1/environments").accept(APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn()));
        assertEquals(2, eventRecorder.events.size());
    }

    @Test
    void mapsMissingEnvironmentAndCustomerToNotFound() throws Exception {
        assertError(mockMvc.perform(delete("/api/v1/environments/{id}", "missing"))
                .andExpect(status().isNotFound()).andReturn(),
                "NOT_FOUND", "error.environment-not-found");
        assertError(mockMvc.perform(post(
                        "/api/v1/customers/{customerId}/environments", "missing")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(
                        new EnvironmentAddRequest(List.of(RoomType.KITCHEN), null))))
                .andExpect(status().isNotFound()).andReturn(),
                "NOT_FOUND", "error.customer-not-found");
        assertError(mockMvc.perform(put(
                        "/api/v1/customers/{customerId}/environments", "missing")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(
                        new EnvironmentReplaceRequest(List.of("Kitchen")))))
                .andExpect(status().isNotFound()).andReturn(),
                "NOT_FOUND", "error.customer-not-found");
        assertError(mockMvc.perform(delete(
                        "/api/v1/customers/{customerId}/environments", "missing"))
                .andExpect(status().isNotFound()).andReturn(),
                "NOT_FOUND", "error.customer-not-found");
    }

    @Test
    void mapsNullInvalidAndMalformedEnvironmentRequestsToBadRequest() throws Exception {
        String customerId = customer("Customer");
        assertInvalid(mockMvc.perform(post(
                        "/api/v1/customers/{customerId}/environments", customerId)
                .contentType(APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest()).andReturn());
        assertInvalid(mockMvc.perform(put(
                        "/api/v1/customers/{customerId}/environments", customerId)
                .contentType(APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest()).andReturn());
        assertInvalid(mockMvc.perform(post(
                        "/api/v1/customers/{customerId}/environments", customerId)
                .contentType(APPLICATION_JSON).content("{\"roomTypes\":null}"))
                .andExpect(status().isBadRequest()).andReturn());
        assertInvalid(mockMvc.perform(put(
                        "/api/v1/customers/{customerId}/environments", customerId)
                .contentType(APPLICATION_JSON).content("{\"types\":null}"))
                .andExpect(status().isBadRequest()).andReturn());
        assertInvalid(mockMvc.perform(post(
                        "/api/v1/customers/{customerId}/environments", customerId)
                .contentType(APPLICATION_JSON)
                .content("{\"roomTypes\":[\"NOT_A_ROOM\"],\"customRoomType\":null}"))
                .andExpect(status().isBadRequest()).andReturn());
        assertInvalid(mockMvc.perform(post(
                        "/api/v1/customers/{customerId}/environments", customerId)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(
                        new EnvironmentAddRequest(List.of(), null))))
                .andExpect(status().isBadRequest()).andReturn());
    }

    @Test
    void rejectsDeletionReferencedByFurniturePart() throws Exception {
        String customerId = customer("Customer");
        ProjectEnvironmentEntity environment = environmentEntity(customerId, "Kitchen");
        FurniturePartEntity part = furniturePartRepository.saveAndFlush(new FurniturePartEntity(
                environment, "Part", new BigDecimal("100"), new BigDecimal("200"),
                new BigDecimal("18"), MaterialType.MDF, EnumSet.noneOf(VisibleEdge.class)));

        assertConflictAndPreserved(environment, "furniture_parts", part.getId());
    }

    @Test
    void rejectsDeletionReferencedByFurnitureItem() throws Exception {
        String customerId = customer("Customer");
        ProjectEnvironmentEntity environment = environmentEntity(customerId, "Kitchen");
        FurnitureItemEntity item = furnitureItemRepository.saveAndFlush(new FurnitureItemEntity(
                customerRepository.findById(customerId).orElseThrow(), environment,
                "Cabinet", "Cabinet", "Wood",
                new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("300")));

        assertConflictAndPreserved(environment, "furniture_items", item.getId());
    }

    @Test
    void rejectsDeletionReferencedBySalesProject() throws Exception {
        String customerId = customer("Customer");
        ProjectEnvironmentEntity environment = environmentEntity(customerId, "Kitchen");
        SalesProjectEntity project = salesProjectRepository.saveAndFlush(new SalesProjectEntity(
                customerRepository.findById(customerId).orElseThrow(), environment,
                "Project", new BigDecimal("100")));

        assertConflictAndPreserved(environment, "sales_projects", project.getId());
    }

    @Test
    void preservesDataAndOtherCustomersWhenSynchronizationFailsAfterFlush() throws Exception {
        String customerId = customer("Customer");
        String otherCustomerId = customer("Other");
        createEnvironment(customerId, "Kitchen");
        createEnvironment(customerId, "Bedroom");
        createEnvironment(otherCustomerId, "Other room");
        eventRecorder.events.clear();
        EnvironmentCatalogPort realPort = (EnvironmentCatalogPort) ReflectionTestUtils
                .getField(environmentCatalog, "environments");
        EnvironmentCatalogPort intercepted = org.mockito.Mockito.mock(
                EnvironmentCatalogPort.class,
                org.mockito.AdditionalAnswers.delegatesTo(realPort));
        org.mockito.Mockito.doAnswer(invocation -> {
            realPort.createAllAndFlush(invocation.getArgument(0), invocation.getArgument(1));
            throw new IllegalStateException("injected environment failure");
        }).when(intercepted).createAllAndFlush(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList());
        ReflectionTestUtils.setField(environmentCatalog, "environments", intercepted);
        try {
            assertThrows(Exception.class, () -> mockMvc.perform(put(
                            "/api/v1/customers/{customerId}/environments", customerId)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(
                            new EnvironmentReplaceRequest(List.of("Kitchen", "Pantry"))))));
        } finally {
            ReflectionTestUtils.setField(environmentCatalog, "environments", realPort);
        }

        assertEquals(3, environmentRepository.count());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_environments WHERE customer_id = ? "
                        + "AND environment_type = ?", Integer.class, customerId, "Pantry"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_environments WHERE customer_id = ?",
                Integer.class, otherCustomerId));
        assertTrue(eventRecorder.events.isEmpty());
        assertForeignKeysValid();
    }

    @Test
    void doesNotPublishEventWhenSynchronizationMakesNoChanges() throws Exception {
        String customerId = customer("Customer");
        createEnvironment(customerId, "Kitchen");
        eventRecorder.events.clear();

        mockMvc.perform(put("/api/v1/customers/{customerId}/environments", customerId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new EnvironmentReplaceRequest(List.of("Kitchen")))))
                .andExpect(status().isOk());

        assertTrue(eventRecorder.events.isEmpty());
    }

    private String customer(String name) {
        return customerCatalog.create(new CustomerInput(name, null, null, null)).id();
    }

    private EnvironmentRow createEnvironment(String customerId, String type) {
        return environmentCatalog.create(customerId, type);
    }

    private ProjectEnvironmentEntity environmentEntity(String customerId, String type) {
        CustomerEntity customer = customerRepository.findById(customerId).orElseThrow();
        return environmentRepository.saveAndFlush(new ProjectEnvironmentEntity(customer, type));
    }

    private void assertConflictAndPreserved(
            ProjectEnvironmentEntity environment, String table, String referenceId
    ) throws Exception {
        assertError(mockMvc.perform(delete("/api/v1/environments/{id}", environment.getId()))
                        .andExpect(status().isConflict()).andReturn(),
                "CONFLICT", "error.environment-has-related-records");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_environments WHERE id = ?",
                Integer.class, environment.getId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE id = ?",
                Integer.class, referenceId));
        assertForeignKeysValid();
    }

    private List<EnvironmentResponse> readResponses(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<>() {
                });
    }

    private List<String> ids(MvcResult result) throws Exception {
        return readResponses(result).stream().map(EnvironmentResponse::id).toList();
    }

    private void assertInvalid(MvcResult result) throws Exception {
        assertError(result, "VALIDATION_ERROR", "validation.environment-invalid");
    }

    private void assertError(MvcResult result, String code, String messageKey) throws Exception {
        assertEquals(new ApiErrorResponse(code, messageKey, List.of(), null),
                objectMapper.readValue(result.getResponse().getContentAsString(),
                        ApiErrorResponse.class));
    }

    private void assertForeignKeysValid() {
        assertTrue(jdbcTemplate.queryForList("PRAGMA foreign_key_check").isEmpty());
    }

    @TestConfiguration
    static class EventRecorder {
        private final List<EnvironmentChangedEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        @TransactionalEventListener
        void onEnvironmentChanged(EnvironmentChangedEvent event) {
            events.add(event);
        }
    }
}
