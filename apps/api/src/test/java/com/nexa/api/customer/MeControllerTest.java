package com.nexa.api.customer;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import com.nexa.api.shared.configuration.CorrelationIdFilter;
import com.nexa.api.shared.errors.ApiExceptionHandler;

@WebMvcTest(MeController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({CorrelationIdFilter.class, ApiExceptionHandler.class})
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerQueryService customerQueryService;

    @Test
    void returnsCurrentProfile() throws Exception {
        when(customerQueryService.currentProfile()).thenReturn(new CustomerProfileResponse(
                "usr_01JDEMO000000000000001",
                "usr_01JDEMO000000000000001",
                "Vishal Singh",
                "vishal@example.com",
                "+91 90000 00482",
                "ACTIVE",
                "CUSTOMER"));

        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Vishal Singh"))
                .andExpect(jsonPath("$.email").value("vishal@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void updatesCurrentProfile() throws Exception {
        when(customerQueryService.updateCurrentProfile(new UpdateProfileRequest("Vishal Kumar Singh", "+91 98765 43210")))
                .thenReturn(new CustomerProfileResponse(
                        "usr_01JDEMO000000000000001",
                        "usr_01JDEMO000000000000001",
                        "Vishal Kumar Singh",
                        "vishal@example.com",
                        "+91 98765 43210",
                        "ACTIVE",
                        "CUSTOMER"));

        mockMvc.perform(patch("/api/v1/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Vishal Kumar Singh","phoneNumber":"+91 98765 43210"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Vishal Kumar Singh"))
                .andExpect(jsonPath("$.phoneNumber").value("+91 98765 43210"));
    }

    @Test
    void rejectsBlankFullName() throws Exception {
        mockMvc.perform(patch("/api/v1/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"","phoneNumber":"+91 98765 43210"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
