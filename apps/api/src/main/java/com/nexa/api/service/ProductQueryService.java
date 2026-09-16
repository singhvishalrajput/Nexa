package com.nexa.api.service;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import com.nexa.api.repository.BankingProductRepository;


import com.nexa.api.service.CurrentUserProvider;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import java.util.List;
import java.util.Locale;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public abstract class ProductQueryService<T> {
  private final BankingProductRepository repository;
  private final CurrentUserProvider user;
  private final BankingProductRepository.Kind kind;
  private final Class<T> type;

  protected ProductQueryService(
      BankingProductRepository repository,
      CurrentUserProvider user,
      BankingProductRepository.Kind kind,
      Class<T> type) {
    this.repository = repository;
    this.user = user;
    this.kind = kind;
    this.type = type;
  }

  public List<T> list(String status, int page, int size) {
    if (page < 0 || page > 100000 || size < 1 || size > 100)
      throw new InvalidRequestException("Invalid page or size.");
    if (status != null && !status.matches("[A-Za-z_]{1,32}"))
      throw new InvalidRequestException("Invalid status.");
    return repository.list(
        user.userId(),
        kind,
        type,
        status == null ? null : status.toUpperCase(Locale.ROOT),
        page,
        size);
  }

  public T detail(String id) {
    if (id == null || !id.matches("[A-Za-z0-9_-]{1,80}"))
      throw new InvalidRequestException("Invalid reference.");
    return repository
        .find(user.userId(), kind, id, type)
        .orElseThrow(() -> new ResourceNotFoundException("The banking item was not found."));
  }
}
