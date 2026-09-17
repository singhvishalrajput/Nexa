package com.ofss.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ofss.beans.*;
import com.ofss.exep.*;
import com.ofss.service.*;

class LoanControllerTest {
    private final LoanService service = mock(LoanService.class);
    private final LoanCalculationService calculation = mock(LoanCalculationService.class);
    private final Authentication customer = new UsernamePasswordAuthenticationToken("42", "unused", List.of());
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LoanController controller = new LoanController();
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "calculation", calculation);
        ReflectionTestUtils.setField(controller, "authorizationService", new AuthorizationServiceImpl());
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void appliesForTheAuthenticatedCustomerWithoutNeedingCustomerIdInBody() throws Exception {
        LoanResponse response = new LoanResponse();
        response.setId(7L);
        response.setStatus("APPROVED");
        when(service.apply(any(), eq(42L))).thenReturn(response);

        mvc.perform(post("/api/loans").principal(customer).contentType(MediaType.APPLICATION_JSON).content("""
                {"accountId":3,"applicationKey":"request-1","purpose":"Education","amount":200000,"tenureMonths":24}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.disbursedAt").doesNotHaveJsonPath());

        ArgumentCaptor<LoanApplicationRequest> request = ArgumentCaptor.forClass(LoanApplicationRequest.class);
        verify(service).apply(request.capture(), eq(42L));
        assertEquals(3L, request.getValue().getAccountId());
        assertEquals("request-1", request.getValue().getApplicationKey());
        assertEquals(0, new BigDecimal("200000").compareTo(request.getValue().getAmount()));
    }

    @Test
    void allLoanReadsAndAcceptanceUseAuthenticatedCustomerId() throws Exception {
        LoanResponse response = new LoanResponse();
        response.setId(7L);
        when(service.byCustomer(42L)).thenReturn(List.of(response));
        when(service.getById(7L, 42L)).thenReturn(response);
        when(service.accept(7L, 42L)).thenReturn(response);

        mvc.perform(get("/api/loans").principal(customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].disbursedAt").doesNotHaveJsonPath());
        mvc.perform(get("/api/loans/7").principal(customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.disbursedAt").doesNotHaveJsonPath());
        mvc.perform(post("/api/loans/7/accept").principal(customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.disbursedAt").doesNotHaveJsonPath());

        verify(service).byCustomer(42L);
        verify(service).getById(7L, 42L);
        verify(service).accept(7L, 42L);
    }

    @Test
    void scheduleAndPaymentEndpointsKeepBothLoanAndCustomerScope() throws Exception {
        LoanInstallmentResponse installment = new LoanInstallmentResponse();
        installment.setId(11L);
        installment.setDueDate(LocalDate.of(2026, 10, 16));
        LoanPaymentResponse payment = new LoanPaymentResponse();
        payment.setId(15L);
        payment.setLoanId(7L);
        payment.setInstallmentId(11L);
        when(service.schedule(7L, 42L)).thenReturn(List.of(installment));
        when(service.payments(7L, 42L)).thenReturn(List.of(payment));
        when(service.payInstallment(7L, 11L, 42L)).thenReturn(payment);

        mvc.perform(get("/api/loans/7/schedule").principal(customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(11));
        mvc.perform(get("/api/loans/7/payments").principal(customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].loanId").value(7));
        mvc.perform(post("/api/loans/7/installments/11/pay").principal(customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.installmentId").value(11));

        verify(service).schedule(7L, 42L);
        verify(service).payments(7L, 42L);
        verify(service).payInstallment(7L, 11L, 42L);
    }

    @Test
    void quoteDelegatesValidatedNumbersToTheCalculator() throws Exception {
        LoanQuoteResponse response = new LoanQuoteResponse();
        response.setEmiAmount(new BigDecimal("9649.89"));
        when(calculation.quote(any())).thenReturn(response);

        mvc.perform(post("/api/loans/quote").principal(customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":200000,\"tenureMonths\":24}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emiAmount").value(9649.89));

        ArgumentCaptor<LoanQuoteRequest> request = ArgumentCaptor.forClass(LoanQuoteRequest.class);
        verify(calculation).quote(request.capture());
        assertEquals(24, request.getValue().getTenureMonths());
        verifyNoInteractions(service);
    }

    @Test
    void invalidLoanDetailsReturnBadRequestWithAnError() throws Exception {
        when(calculation.quote(any())).thenThrow(new BadRequestException("Unsupported loan amount"));

        mvc.perform(post("/api/loans/quote").principal(customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1,\"tenureMonths\":24}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Unsupported loan amount"));
    }

    @Test
    void missingOrUnownedLoanReturnsNotFoundWithoutExposingAnotherCustomer() throws Exception {
        when(service.getById(7L, 42L)).thenThrow(new ResourceNotFoundException("Loan not found"));

        mvc.perform(get("/api/loans/7").principal(customer)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Loan not found"));
    }

    @Test
    void invalidCustomerPrincipalNeverInvokesLoanService() throws Exception {
        Authentication invalid = new UsernamePasswordAuthenticationToken("invalid", "unused", List.of());
        mvc.perform(get("/api/loans").principal(invalid)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
