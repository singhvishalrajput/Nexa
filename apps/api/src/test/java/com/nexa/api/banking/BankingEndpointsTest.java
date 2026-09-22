package com.nexa.api.banking;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.controller.ActionsController;
import com.nexa.api.controller.BeneficiariesController;
import com.nexa.api.controller.BillController;
import com.nexa.api.controller.CardController;
import com.nexa.api.controller.CreditCardsController;
import com.nexa.api.controller.LoanController;
import com.nexa.api.controller.MandateController;
import com.nexa.api.controller.ScheduledPaymentController;
import com.nexa.api.controller.TransactionsController;
import com.nexa.api.exep.ApiExceptionHandler;
import com.nexa.api.repository.BankingProductRepository;
import com.nexa.api.service.ActionPreparationService;
import com.nexa.api.service.BeneficiaryQueryService;
import com.nexa.api.service.BillQueryService;
import com.nexa.api.service.CardQueryService;
import com.nexa.api.service.CurrentUserProvider;
import com.nexa.api.service.LoanQueryService;
import com.nexa.api.service.MandateQueryService;
import com.nexa.api.service.ScheduledPaymentQueryService;
import com.nexa.api.service.TransactionQueryService;
import com.nexa.api.service.TransferQueryService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nexa.api.service.CurrentUserProvider;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BankingEndpointsTest {
  final BankingProductRepository repo = mock(BankingProductRepository.class);
  final CurrentUserProvider user = () -> "owner";
  final BeneficiaryQueryService beneficiaries = mock(BeneficiaryQueryService.class);
  final TransferQueryService transfers = mock(TransferQueryService.class);
  final TransactionQueryService transactions = mock(TransactionQueryService.class);
  final ActionPreparationService actions = mock(ActionPreparationService.class);
  final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(
              new MandateController(new MandateQueryService(repo, user)),
              new BillController(new BillQueryService(repo, user), mock(com.nexa.api.service.PaymentItemService.class)),
              new CardController(new CardQueryService(repo, user)),
              new CreditCardsController(new CardQueryService(repo, user)),
              new LoanController(new LoanQueryService(repo, user)),
              new ScheduledPaymentController(new ScheduledPaymentQueryService(repo, user)),
              new BeneficiariesController(beneficiaries),
              new ActionsController(actions, transfers),
              new TransactionsController(transactions))
          .setControllerAdvice(new ApiExceptionHandler())
          .build();

  @Test
  void newListsAndDetailsHavePredictableContracts() throws Exception {
    for (String route :
        List.of(
            "mandates",
            "bills",
            "cards",
            "credit-cards",
            "loans",
            "scheduled-payments",
            "beneficiaries"))
      mvc.perform(get("/api/v1/" + route))
          .andExpect(status().isOk())
          .andExpect(content().json("[]"));
    for (String route :
        List.of("mandates", "bills", "cards", "credit-cards", "loans", "scheduled-payments"))
      mvc.perform(get("/api/v1/" + route + "/missing"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    mvc.perform(get("/api/v1/mandates?size=1000")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/transactions")).andExpect(status().isBadRequest());
  }

  @Test
  void historiesAndDetailsUseOwnedServices() throws Exception {
    var card =
        new BankingModels.Card(
            "crd_1",
            "Demo",
            "1234567812345678",
            "CREDIT",
            "100",
            "900",
            "1000",
            "5",
            "INR",
            null,
            "ACTIVE",
            "acc_1",
            List.of());
    when(repo.find("owner", BankingProductRepository.Kind.CARD, "crd_1", BankingModels.Card.class))
        .thenReturn(Optional.of(card));
    mvc.perform(get("/api/v1/credit-cards/crd_1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.numberMasked").value("•••• 5678"));
    mvc.perform(get("/api/v1/cards/crd_1/transactions"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
    var bill =
        new BankingModels.Bill(
            "bil_1", "Demo", "100", null, "INR", null, "DUE", "acc_1", "REF", null, null,
            List.of());
    when(repo.find("owner", BankingProductRepository.Kind.BILL, "bil_1", BankingModels.Bill.class))
        .thenReturn(Optional.of(bill));
    mvc.perform(get("/api/v1/bills/bil_1/payments"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
    var loan =
        new BankingModels.Loan(
            "lon_1",
            "Demo",
            "1234",
            "PERSONAL",
            "1000",
            "100",
            "INR",
            null,
            null,
            "ACTIVE",
            "acc_1",
            List.of());
    when(repo.find("owner", BankingProductRepository.Kind.LOAN, "lon_1", BankingModels.Loan.class))
        .thenReturn(Optional.of(loan));
    mvc.perform(get("/api/v1/loans/lon_1/payments"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
  }

  @Test
  void rejectsMalformedActionsAndSanitisesFailures() throws Exception {
    mvc.perform(
            post("/api/v1/actions/prepare").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(actions);
    when(beneficiaries.list())
        .thenThrow(
            new org.springframework.dao.DataAccessResourceFailureException(
                "SECRET DATABASE DETAILS"));
    mvc.perform(get("/api/v1/beneficiaries"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("BANKING_UNAVAILABLE"))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET"))));
  }
}
