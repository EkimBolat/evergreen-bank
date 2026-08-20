package com.ekim.bankingapi.customer;

import com.ekim.bankingapi.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class CustomerControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private String adminToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        // Customer management is admin-only; the JWT here is synthetic (never logged in
        // for real) since JwtAuthFilter trusts the signed token's claims and never re-checks
        // the DB per-request — consistent with this app's stateless JWT design.
        adminToken = jwtService.generateToken("admin-test@example.com", 1L, 1L, "ADMIN");
    }

    @Test
    void createCustomer_shouldReturn201_whenDataIsValid() throws Exception {
        CustomerRequest request = new CustomerRequest();
        request.setFirstName("Test");
        request.setLastName("User");
        request.setEmail("integration-test-" + System.currentTimeMillis() + "@example.com");
        request.setPhoneNumber("05550000000");
        request.setNationalId(String.valueOf(System.currentTimeMillis()).substring(0, 11));
        request.setAge(30);
        request.setAddress("Test Address");

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Test"))
                .andExpect(jsonPath("$.email").value(request.getEmail()));
    }

    @Test
    void createCustomer_shouldReturn400_whenEmailIsMissing() throws Exception {
        CustomerRequest request = new CustomerRequest();
        request.setFirstName("Test");
        request.setLastName("User");
        request.setEmail("");
        request.setPhoneNumber("05550000000");
        request.setNationalId("99999999999");
        request.setAge(30);

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void createCustomer_shouldReturn403_whenCallerIsNotAdmin() throws Exception {
        String customerToken = jwtService.generateToken("customer-test@example.com", 2L, 2L, "CUSTOMER");

        CustomerRequest request = new CustomerRequest();
        request.setFirstName("Test");
        request.setLastName("User");
        request.setEmail("blocked-" + System.currentTimeMillis() + "@example.com");
        request.setPhoneNumber("05550000000");
        request.setNationalId(String.valueOf(System.currentTimeMillis()).substring(0, 11));
        request.setAge(30);

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCustomerById_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/customers/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
