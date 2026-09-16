package com.nexa.api.core;
import com.nexa.api.beans.Account;
import com.nexa.api.beans.AccountCategory;
import com.nexa.api.beans.AccountType;
import com.nexa.api.beans.Customer;
import com.nexa.api.service.AccountService;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nexa.api.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.profiles.active=integration",
      "spring.datasource.url=jdbc:h2:mem:merged;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "nexa.security.jwt.secret=merged-application-test-secret-long-enough",
      "nexa.cors.allowed-origins=http://localhost:8000"
    })
@AutoConfigureMockMvc
class MergedApplicationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired AccountService accounts;

  @Test
  void loginAccountOpeningAdminDepositAndCustomerHistoryUseOneCore() throws Exception {
    var registration =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
{"fullName":"Integrated Customer","email":"integrated@example.com","password":"SecureTest@123"}
"""))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = json.readTree(registration).get("accessToken").asText();
    String auth = "Bearer " + token;
    var opened =
        mvc.perform(
                post("/api/v1/accounts")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
{"displayName":"Primary","accountType":"SAVINGS","currencyCode":"INR","dateOfBirth":"1990-01-01","address":"Mumbai"}
"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.availableBalance").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(opened).get("id").asText();
    mvc.perform(get("/api/accounts").header("Authorization", auth))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    var cash = new Account();
    cash.setAccountNumber("APP-CASH");
    cash.setAccountName("Cash");
    cash.setAccountType(AccountType.CASH);
    cash.setAccountCategory(AccountCategory.SYSTEM);
    accounts.create(cash);
    mvc.perform(
            post("/api/transactions/deposit")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destinationAccountId\":" + id + ",\"amount\":25.00}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCESS"));
    mvc.perform(get("/api/v1/accounts/" + id + "/balance").header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availableBalance").value(25));
    mvc.perform(get("/api/v1/accounts/" + id + "/transactions").header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].amount").value(25))
        .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"));
    mvc.perform(
            patch("/api/v1/me")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"Updated Customer","phoneNumber":null,"address":"Pune"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address").value("Pune"));
    assertThat(accounts.getById(Long.valueOf(id)).getCustomer().getFullName())
        .isEqualTo("Updated Customer");
  }
}
