package com.woodlogix.server.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.woodlogix.application.customer.CustomerCatalog;
import com.woodlogix.application.customer.CustomerInput;
import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.server.WoodLogixServerApplication;
import com.woodlogix.server.api.dto.ApiErrorResponse;
import com.woodlogix.server.api.dto.CustomerRequest;
import com.woodlogix.server.api.dto.CustomerResponse;
import com.woodlogix.server.persistence.entity.CustomerEntity;
import com.woodlogix.server.persistence.entity.FurnitureItemEntity;
import com.woodlogix.server.persistence.entity.ProjectEnvironmentEntity;
import com.woodlogix.server.persistence.entity.SalesProjectEntity;
import com.woodlogix.server.persistence.repository.CustomerRepository;
import com.woodlogix.server.persistence.repository.FurnitureItemRepository;
import com.woodlogix.server.persistence.repository.ProjectEnvironmentRepository;
import com.woodlogix.server.persistence.repository.SalesProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
class CustomerControllerIntegrationTest {
    @TempDir
    static Path temporaryDirectory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerCatalog customerCatalog;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProjectEnvironmentRepository environmentRepository;

    @Autowired
    private SalesProjectRepository salesProjectRepository;

    @Autowired
    private FurnitureItemRepository furnitureItemRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("woodlogix.database.path",
                () -> temporaryDirectory.resolve("customers-api.db").toString());
        registry.add("spring.main.banner-mode", () -> "off");
        registry.add("logging.level.root", () -> "ERROR");
        registry.add("logging.file.name", () -> "");
    }

    @BeforeEach
    void clearFixtures() {
        jdbcTemplate.update("DELETE FROM project_estimate_hardwares");
        jdbcTemplate.update("DELETE FROM project_estimate_workpieces");
        jdbcTemplate.update("DELETE FROM project_workpieces");
        jdbcTemplate.update("DELETE FROM project_estimates");
        jdbcTemplate.update("DELETE FROM sales_projects");
        jdbcTemplate.update("DELETE FROM furniture_part_visible_edges");
        jdbcTemplate.update("DELETE FROM furniture_parts");
        jdbcTemplate.update("DELETE FROM furniture_items");
        jdbcTemplate.update("DELETE FROM project_environments");
        jdbcTemplate.update("DELETE FROM customers");
    }

    @Test
    void listsAllCustomers() throws Exception {
        CustomerRow first = createCustomer("Ana Carvalho", "11999991111",
                "ana@example.test", "Street 1");
        CustomerRow second = createCustomer("Bruno Lima", "21988887777",
                "bruno@example.test", null);

        List<CustomerResponse> responses = readResponses(mockMvc.perform(
                get("/api/v1/customers").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn());

        assertEquals(List.of(first.id(), second.id()),
                responses.stream().map(CustomerResponse::id).toList());
    }

    @Test
    void searchesByAnyFieldAndByNameWithTrimAndCaseInsensitivity() throws Exception {
        CustomerRow ana = createCustomer("Ana Carvalho", "11999991111",
                "ana@example.test", "Street 1");
        CustomerRow bruno = createCustomer("Bruno Lima", "21988887777",
                "bruno@example.test", null);

        assertEquals(List.of(ana.id()), ids(mockMvc.perform(get("/api/v1/customers")
                .param("search", "  CARVALHO ").accept(APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn()));
        assertEquals(List.of(bruno.id()), ids(mockMvc.perform(get("/api/v1/customers")
                .param("search", "BRUNO@EXAMPLE").accept(APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn()));
        assertEquals(List.of(bruno.id()), ids(mockMvc.perform(get("/api/v1/customers")
                .param("search", "8888").accept(APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn()));
        assertEquals(List.of(ana.id()), ids(mockMvc.perform(get("/api/v1/customers")
                .param("name", " carvalho ").accept(APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn()));
    }

    @Test
    void emptySearchParametersFollowCatalogFallbackToList() throws Exception {
        createCustomer("Ana Carvalho", null, null, null);
        createCustomer("Bruno Lima", null, null, null);

        assertEquals(2, readResponses(mockMvc.perform(get("/api/v1/customers")
                .param("search", " ").accept(APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn()).size());
        assertEquals(2, readResponses(mockMvc.perform(get("/api/v1/customers")
                .param("name", " ").accept(APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn()).size());
        assertEquals(2, readResponses(mockMvc.perform(get("/api/v1/customers")
                .param("search", "").accept(APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn()).size());
    }

    @Test
    void rejectsBothSearchParameters() throws Exception {
        createCustomer("Ana Carvalho", null, null, null);

        MvcResult result = mockMvc.perform(get("/api/v1/customers")
                        .param("search", "ana")
                        .param("name", "ana")
                        .accept(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        assertInvalid(result);
    }

    @Test
    void createsAndPersistsCustomer() throws Exception {
        CustomerRequest request = customerRequest("Ana Carvalho", "11999991111",
                "ana@example.test", "Street 1");

        CustomerResponse response = readResponse(mockMvc.perform(post("/api/v1/customers")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn());

        assertTrue(response.id() != null && !response.id().isBlank());
        assertEquals(request.fullName(), response.fullName());
        assertEquals(request.phone(), response.phone());
        assertEquals(request.email(), response.email());
        assertEquals(request.address(), response.address());
        assertEquals(1, customerRepository.count());
    }

    @Test
    void updatesPreservingIdAndPersistsValuesVisibleThroughGet() throws Exception {
        CustomerRow original = createCustomer("Ana Carvalho", "11999991111",
                "ana@example.test", "Street 1");
        CustomerRequest request = customerRequest("Ana Souza", null, null, "Street 2");

        CustomerResponse response = readResponse(mockMvc.perform(put(
                "/api/v1/customers/{id}", original.id())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn());

        assertEquals(original.id(), response.id());
        assertEquals(request.fullName(), response.fullName());
        assertEquals(request.phone(), response.phone());
        assertEquals(request.email(), response.email());
        assertEquals(request.address(), response.address());

        List<CustomerResponse> responses = readResponses(mockMvc.perform(
                get("/api/v1/customers").accept(APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn());
        assertEquals(1, responses.size());
        assertEquals(response, responses.get(0));
    }

    @Test
    void deletesCustomerAndItNoLongerAppearsInGet() throws Exception {
        CustomerRow customer = createCustomer("Ana Carvalho", null, null, null);

        mockMvc.perform(delete("/api/v1/customers/{id}", customer.id()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/v1/customers").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void rejectsInvalidDataAndNullJsonWithoutChangingExistingCustomer() throws Exception {
        CustomerRow existing = createCustomer("Original", "11999991111",
                "original@example.test", "Street");
        CustomerRequest invalid = customerRequest(" ", "invalid phone",
                "invalid-email", null);

        assertInvalid(mockMvc.perform(post("/api/v1/customers")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(invalid)))
                .andExpect(status().isBadRequest()).andReturn());
        assertInvalid(mockMvc.perform(post("/api/v1/customers")
                .contentType(APPLICATION_JSON)
                .content("null"))
                .andExpect(status().isBadRequest()).andReturn());
        assertInvalid(mockMvc.perform(put("/api/v1/customers/{id}", existing.id())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(invalid)))
                .andExpect(status().isBadRequest()).andReturn());
        assertInvalid(mockMvc.perform(put("/api/v1/customers/{id}", existing.id())
                .contentType(APPLICATION_JSON)
                .content("null"))
                .andExpect(status().isBadRequest()).andReturn());

        CustomerResponse persisted = readResponses(mockMvc.perform(
                get("/api/v1/customers").accept(APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn()).get(0);
        assertEquals(existing.id(), persisted.id());
        assertEquals(existing.fullName(), persisted.fullName());
        assertEquals(existing.phone(), persisted.phone());
        assertEquals(existing.email(), persisted.email());
        assertEquals(existing.address(), persisted.address());
    }

    @Test
    void mapsEntityBeanValidationFailuresToBadRequest() throws Exception {
        CustomerRequest invalid = customerRequest("Valid", "invalid phone",
                "invalid-email", null);

        assertInvalid(mockMvc.perform(post("/api/v1/customers")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(invalid)))
                .andExpect(status().isBadRequest()).andReturn());
        assertEquals(0, customerRepository.count());
    }

    @Test
    void mapsUnknownIdsToNotFoundWithoutChangingOtherCustomers() throws Exception {
        CustomerRow existing = createCustomer("Original", null, null, null);
        CustomerRequest request = customerRequest("Changed", null, null, null);

        assertNotFound(mockMvc.perform(put("/api/v1/customers/{id}", "missing")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn());
        assertNotFound(mockMvc.perform(delete("/api/v1/customers/{id}", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn());

        assertEquals(List.of(existing.id()),
                customerCatalog.findAll().stream().map(CustomerRow::id).toList());
    }

    @Test
    void rejectsDeletionWhenReferencedByEnvironment() throws Exception {
        createCustomer("Other customer", null, null, null);
        CustomerEntity customer = customerEntity("Environment customer");
        ProjectEnvironmentEntity environment = environmentRepository.saveAndFlush(
                new ProjectEnvironmentEntity(customer, "Kitchen"));

        assertConflictAndPreserved(customer, "project_environments", environment.getId());
    }

    @Test
    void rejectsDeletionWhenReferencedBySalesProject() throws Exception {
        createCustomer("Other customer", null, null, null);
        CustomerEntity customer = customerEntity("Project customer");
        SalesProjectEntity project = salesProjectRepository.saveAndFlush(
                new SalesProjectEntity(customer, null, "Project", new BigDecimal("100")));

        assertConflictAndPreserved(customer, "sales_projects", project.getId());
    }

    @Test
    void rejectsDeletionWhenReferencedByFurnitureItem() throws Exception {
        createCustomer("Other customer", null, null, null);
        CustomerEntity customer = customerEntity("Furniture customer");
        FurnitureItemEntity furniture = furnitureItemRepository.saveAndFlush(
                new FurnitureItemEntity(customer, null, "Cabinet", "Cabinet", "Wood",
                        new BigDecimal("100"), new BigDecimal("200"),
                        new BigDecimal("300")));

        assertConflictAndPreserved(customer, "furniture_items", furniture.getId());
    }

    private void assertConflictAndPreserved(
            CustomerEntity customer, String referenceTable, String referenceId
    ) throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/v1/customers/{id}", customer.getId()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        assertConflict(result);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers WHERE id = ?", Integer.class, customer.getId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + referenceTable + " WHERE id = ?",
                Integer.class, referenceId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers WHERE full_name = ?",
                Integer.class, "Other customer"));
        assertTrue(jdbcTemplate.queryForList("PRAGMA foreign_key_check").isEmpty());
    }

    private CustomerRow createCustomer(
            String fullName, String phone, String email, String address
    ) {
        return customerCatalog.create(new CustomerInput(fullName, phone, email, address));
    }

    private CustomerEntity customerEntity(String fullName) {
        return customerRepository.saveAndFlush(
                new CustomerEntity(fullName, null, null, null));
    }

    private static CustomerRequest customerRequest(
            String fullName, String phone, String email, String address
    ) {
        return new CustomerRequest(fullName, phone, email, address);
    }

    private List<CustomerResponse> readResponses(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<>() {
                });
    }

    private CustomerResponse readResponse(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(),
                CustomerResponse.class);
    }

    private List<String> ids(MvcResult result) throws Exception {
        return readResponses(result).stream().map(CustomerResponse::id).toList();
    }

    private void assertInvalid(MvcResult result) throws Exception {
        assertEquals(new ApiErrorResponse(
                        "VALIDATION_ERROR", "validation.customer-invalid", List.of(), null),
                objectMapper.readValue(result.getResponse().getContentAsString(),
                        ApiErrorResponse.class));
    }

    private void assertNotFound(MvcResult result) throws Exception {
        assertEquals(new ApiErrorResponse(
                        "NOT_FOUND", "error.customer-not-found", List.of(), null),
                objectMapper.readValue(result.getResponse().getContentAsString(),
                        ApiErrorResponse.class));
    }

    private void assertConflict(MvcResult result) throws Exception {
        assertEquals(new ApiErrorResponse(
                        "CONFLICT", "error.customer-has-related-records", List.of(), null),
                objectMapper.readValue(result.getResponse().getContentAsString(),
                        ApiErrorResponse.class));
    }
}
