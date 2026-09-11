package com.nexa.api.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nexa.api.shared.errors.ApiExceptionHandler;

@WebMvcTest(AuthenticationController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(ApiExceptionHandler.class)
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationService authenticationService;

    @Test
    void registersCustomerAndReturnsTokens() throws Exception {
        when(authenticationService.register(any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "New Customer",
                                  "email": "customer@example.com",
                                  "password": "Strong@123",
                                  "phoneNumber": "+91 90000 00000"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));
    }

    @Test
    void rejectsInvalidRegistrationBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"", "email":"not-an-email", "password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void logsInAndReturnsTokens() throws Exception {
        when(authenticationService.login(any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"vishal@example.com", "password":"NexaDemo@123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void logsOutWithRefreshToken() throws Exception {
        doNothing().when(authenticationService).logout(any());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isNoContent());
    }

    private AuthenticationResponse response() {
        return new AuthenticationResponse(
                "access-token",
                "refresh-token",
                "Bearer",
                900,
                new AuthenticationResponse.User(
                        "usr_01JDEMO000000000000001",
                        "vishal@example.com",
                        "CUSTOMER"));
    }
}
