package com.nexa.api.banking;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import com.nexa.api.repository.BankingProductRepository;
import com.nexa.api.service.CurrentUserProvider;
import com.nexa.api.service.MandateQueryService;


import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexa.api.service.CurrentUserProvider;
import java.util.*;
import org.junit.jupiter.api.Test;

class BankingDomainTest {
  final BankingProductRepository repo = mock(BankingProductRepository.class);
  final CurrentUserProvider user = mock(CurrentUserProvider.class);
  final MandateQueryService service = new MandateQueryService(repo, user);

  @Test
  void scopesEveryReadAndPassesPaginationAndStatus() {
    when(user.userId()).thenReturn("owner");
    when(repo.list(
            "owner",
            BankingProductRepository.Kind.MANDATE,
            BankingModels.Mandate.class,
            "PAUSED",
            1,
            10))
        .thenReturn(List.of());
    assertThat(service.list("paused", 1, 10)).isEmpty();
    verify(repo)
        .list(
            "owner",
            BankingProductRepository.Kind.MANDATE,
            BankingModels.Mandate.class,
            "PAUSED",
            1,
            10);
    when(repo.find(
            "owner",
            BankingProductRepository.Kind.MANDATE,
            "mnd_other",
            BankingModels.Mandate.class))
        .thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.detail("mnd_other"))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(repo)
        .find(
            "owner",
            BankingProductRepository.Kind.MANDATE,
            "mnd_other",
            BankingModels.Mandate.class);
  }

  @Test
  void rejectsInvalidParametersBeforeRepositoryAccess() {
    assertThatThrownBy(() -> service.list(null, -1, 10))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.list(null, 0, 101))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.detail("../private"))
        .isInstanceOf(InvalidRequestException.class);
    verifyNoInteractions(repo);
  }

  @Test
  void returnsTypedDataAndMasksIdentifiers() {
    when(user.userId()).thenReturn("owner");
    var item =
        new BankingModels.Mandate(
            "mnd_1",
            "Demo",
            "PAUSED",
            "500",
            "INR",
            "MONTHLY",
            "2026-01-01",
            null,
            null,
            "acc_1",
            "Savings",
            "123456789012",
            "DEMO-1");
    when(repo.find(
            "owner", BankingProductRepository.Kind.MANDATE, "mnd_1", BankingModels.Mandate.class))
        .thenReturn(Optional.of(item));
    assertThat(service.detail("mnd_1").accountNumberMasked()).isEqualTo("•••• 9012");
    assertThat(service.detail("mnd_1").status()).isEqualTo("PAUSED");
  }
}
