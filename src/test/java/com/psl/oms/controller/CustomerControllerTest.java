package com.psl.oms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.psl.oms.dto.request.CreateCustomerRequest;
import com.psl.oms.dto.response.CustomerResponse;
import com.psl.oms.exception.DuplicateResourceException;
import com.psl.oms.exception.GlobalExceptionHandler;
import com.psl.oms.exception.ResourceNotFoundException;
import com.psl.oms.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CustomerControllerTest — web-layer slice test using MockMvc.
 *
 * @WebMvcTest loads only the controller layer (no service, no JPA, no DB).
 * The service is replaced with a @MockBean — we control its output.
 *
 * This tests:
 *   - Correct HTTP status codes (201, 200, 404, 409, 400)
 *   - Request deserialization and validation (@Valid)
 *   - Response body JSON structure
 *   - Exception handler integration (GlobalExceptionHandler)
 */
@WebMvcTest(CustomerController.class)
@Import(GlobalExceptionHandler.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CustomerService customerService;

    // ── POST /api/customers ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/customers — returns 201 with customer body on success")
    void createCustomer_returns201_onSuccess() throws Exception {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .name("Alice Smith").cellNumber("9876543210").address("10 Test Lane").build();

        CustomerResponse response = CustomerResponse.builder()
                .id(1L).name("Alice Smith").cellNumber("9876543210").address("10 Test Lane").build();

        when(customerService.createCustomer(any())).thenReturn(response);

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Alice Smith"))
                .andExpect(jsonPath("$.cellNumber").value("9876543210"));
    }

    @Test
    @DisplayName("POST /api/customers — returns 409 when cell number is duplicate")
    void createCustomer_returns409_onDuplicate() throws Exception {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .name("Bob").cellNumber("9876543210").build();

        when(customerService.createCustomer(any()))
                .thenThrow(new DuplicateResourceException("Customer", "cellNumber", "9876543210"));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/customers — returns 400 when cell number is blank")
    void createCustomer_returns400_whenCellNumberBlank() throws Exception {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .name("Alice").cellNumber("").build();

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    @DisplayName("POST /api/customers — returns 400 when cell number has invalid format")
    void createCustomer_returns400_whenCellNumberInvalid() throws Exception {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .name("Alice").cellNumber("ABCDE").build();

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/customers/{id} ──────────────────────────────────────────

    @Test
    @DisplayName("GET /api/customers/{id} — returns 200 with customer on success")
    void getCustomer_returns200_whenFound() throws Exception {
        CustomerResponse response = CustomerResponse.builder()
                .id(1L).name("Alice Smith").cellNumber("9876543210").build();

        when(customerService.getCustomerById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Alice Smith"));
    }

    @Test
    @DisplayName("GET /api/customers/{id} — returns 404 when not found")
    void getCustomer_returns404_whenNotFound() throws Exception {
        when(customerService.getCustomerById(99L))
                .thenThrow(new ResourceNotFoundException("Customer", "id", 99L));

        mockMvc.perform(get("/api/customers/99"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/customers ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/customers — returns 200 with list of all customers")
    void getAllCustomers_returns200_withList() throws Exception {
        List<CustomerResponse> responses = List.of(
                CustomerResponse.builder().id(1L).name("Alice").cellNumber("1111111111").build(),
                CustomerResponse.builder().id(2L).name("Bob").cellNumber("2222222222").build()
        );

        when(customerService.getAllCustomers()).thenReturn(responses);

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Alice"))
                .andExpect(jsonPath("$[1].name").value("Bob"));
    }
}
