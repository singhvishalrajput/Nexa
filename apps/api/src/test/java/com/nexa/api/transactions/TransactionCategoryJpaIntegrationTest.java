package com.nexa.api.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexa.api.beans.Account;
import com.nexa.api.beans.AccountCategory;
import com.nexa.api.beans.AccountType;
import com.nexa.api.beans.Customer;
import com.nexa.api.beans.TransactionResponse;
import com.nexa.api.controller.TransactionsController;
import com.nexa.api.exep.ApiExceptionHandler;
import com.nexa.api.service.AccountQueryService;
import com.nexa.api.service.CurrentUserProvider;
import com.nexa.api.service.TransactionQueryService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.profiles.active=integration"
})
@Import({AccountQueryService.class, TransactionQueryService.class})
class TransactionCategoryJpaIntegrationTest {
  @Autowired EntityManager em;
  @Autowired TransactionQueryService queries;
  @MockitoBean CurrentUserProvider user;
  private Account account;
  private Account secondAccount;
  private Account otherAccount;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    when(user.userId()).thenReturn("category-owner");
    Customer owner = customer("category-owner");
    account = account("CATEGORY-ACCOUNT", owner);
    secondAccount = account("SECOND-ACCOUNT", owner);
    otherAccount = account("OTHER-ACCOUNT", customer("other-category-owner"));
    em.flush();
    for (int index = 0; index < 105; index++) {
      transaction("GROCERY-" + index, "PAYMENT", account, null,
          index % 2 == 0 ? " GROCERIES " : "Groceries", LocalDateTime.of(2026, 9, 25, 10, 0).plusSeconds(index));
    }
    transaction("OLD-SALARY", "PAYMENT", null, account, " Salary ", LocalDateTime.of(2001, 1, 1, 10, 0));
    transaction("INCOMING-UTILITIES", "PAYMENT", otherAccount, account, "Utilities", LocalDateTime.of(2026, 9, 24, 10, 0));
    transaction("NO-CATEGORY", "PAYMENT", account, null, null, LocalDateTime.of(2026, 9, 23, 10, 0));
    transaction("EMPTY-CATEGORY", "PAYMENT", account, null, "   ", LocalDateTime.of(2026, 9, 23, 10, 0));
    transaction("OTHER-PRIVATE", "PAYMENT", otherAccount, null, "Private health", LocalDateTime.of(2026, 9, 25, 10, 0));
    transaction("SECOND-INSURANCE", "PAYMENT", null, secondAccount, "Insurance", LocalDateTime.of(2026, 9, 25, 10, 0));
    transaction("BILL-INSTRUCTION", "BILL", account, null, "Bill-only", LocalDateTime.of(2026, 9, 26, 10, 0));
    em.clear();
    mvc = MockMvcBuilders.standaloneSetup(new TransactionsController(queries))
        .setControllerAdvice(new ApiExceptionHandler()).build();
  }

  @Test
  void categoriesCoverTheEntireAccountHistoryAndNormalizeOnlyPostedTransactionCategories() throws Exception {
    var firstPage = queries.transactions(account.getId().toString(), null, null, null, 0, 30);
    assertThat(firstPage.content()).hasSize(30);
    assertThat(firstPage.content()).extracting(TransactionResponse::id).doesNotContain("OLD-SALARY");
    assertThat(queries.categories(account.getId().toString()))
        .containsExactly("groceries", "salary", "utilities");
    mvc.perform(get("/api/v1/transactions/categories").param("accountId", account.getId().toString()))
        .andExpect(status().isOk()).andExpect(content().json("[\"groceries\",\"salary\",\"utilities\"]"));
    assertThat(queries.categories(secondAccount.getId().toString())).containsExactly("insurance");
  }

  @Test
  void categoryOptionsMatchTheTrimmedCaseInsensitiveFilterIncludingIncomingAndOlderRows() throws Exception {
    var groceries = queries.transactions(account.getId().toString(), " gRoCeRiEs ", null, null, 0, 100);
    assertThat(groceries.totalElements()).isEqualTo(105);
    assertThat(groceries.content()).hasSize(100);
    assertThat(queries.transactions(account.getId().toString(), "groceries", null, null, 1, 100).content())
        .hasSize(5);
    var salary = queries.transactions(account.getId().toString(), " SALARY ", null, null, 0, 30);
    assertThat(salary.content()).extracting(TransactionResponse::id).containsExactly("OLD-SALARY");
    assertThat(salary.content().get(0).amount()).isEqualByComparingTo("10");
    var utilities = queries.transactions(account.getId().toString(), "utilities", null, null, 0, 30, "CREDIT", null);
    assertThat(utilities.content()).extracting(TransactionResponse::id).containsExactly("INCOMING-UTILITIES");
    assertThat(queries.transactions(account.getId().toString(), "bill-only", null, null, 0, 30).content()).isEmpty();
    assertThat(queries.transactions(account.getId().toString(), "insurance", null, null, 0, 30).content()).isEmpty();
    mvc.perform(get("/api/v1/transactions").param("accountId", account.getId().toString()).param("category", " SALARY "))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value("OLD-SALARY"));
  }

  @Test
  void categoriesRejectUnownedUnknownOrInvalidAccountsBeforeExposingNames() throws Exception {
    for (String id : new String[] {otherAccount.getId().toString(), "9999999", "not-an-account"}) {
      mvc.perform(get("/api/v1/transactions/categories").param("accountId", id))
          .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
    mvc.perform(get("/api/v1/transactions/categories")).andExpect(status().isBadRequest());
  }

  private Customer customer(String userId) {
    Customer customer = new Customer();
    customer.setUserId(userId);
    customer.setFullName(userId);
    customer.setEmail(userId + "@example.test");
    em.persist(customer);
    return customer;
  }

  private Account account(String number, Customer customer) {
    Account result = new Account();
    result.setAccountNumber(number);
    result.setAccountName(number);
    result.setCustomer(customer);
    result.setAccountType(AccountType.SAVINGS);
    result.setAccountCategory(AccountCategory.CUSTOMER);
    em.persist(result);
    return result;
  }

  private void transaction(String id, String kind, Account source, Account destination,
      String category, LocalDateTime createdAt) {
    // Native fixture insert permits the BILL row to exercise BankTransaction's SQLRestriction.
    em.createNativeQuery("INSERT INTO transactions(id,record_kind,transaction_type,source_account_id,"
            + "destination_account_id,category,amount,status,created_at)"
            + " VALUES(:id,:kind,:type,:source,:destination,:category,:amount,:status,:createdAt)")
        .setParameter("id", id).setParameter("kind", kind)
        .setParameter("type", source == null ? "DEPOSIT" : destination == null ? "WITHDRAWAL" : "TRANSFER")
        .setParameter("source", source == null ? null : source.getId())
        .setParameter("destination", destination == null ? null : destination.getId())
        .setParameter("category", category).setParameter("amount", new BigDecimal("10.00"))
        .setParameter("status", "PAYMENT".equals(kind) ? "SUCCESS" : "UPCOMING")
        .setParameter("createdAt", createdAt).executeUpdate();
  }
}
