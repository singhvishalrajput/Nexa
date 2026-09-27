package com.nexa.api.service;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.repository.BankingProductRepository;


import com.nexa.api.service.CurrentUserProvider;
import org.springframework.stereotype.Service;

@Service
public class ScheduledPaymentQueryService extends ProductQueryService<BankingModels.Payment> {
  private final ScheduledPaymentService authorized;

  @org.springframework.beans.factory.annotation.Autowired
  public ScheduledPaymentQueryService(BankingProductRepository repository, CurrentUserProvider user,
      ScheduledPaymentService authorized) {
    super(repository,user,BankingProductRepository.Kind.SCHEDULED_PAYMENT,BankingModels.Payment.class);
    this.authorized=authorized;
  }

  /** Compatibility constructor for repository-only unit fixtures. Runtime uses the owned aggregate. */
  public ScheduledPaymentQueryService(
      BankingProductRepository repository, CurrentUserProvider user) {
    super(
        repository,
        user,
        BankingProductRepository.Kind.SCHEDULED_PAYMENT,
        BankingModels.Payment.class);
    this.authorized=null;
  }

  @Override public java.util.List<BankingModels.Payment> list(String status,int page,int size) {
    return authorized==null?super.list(status,page,size):authorized.list(status,page,size).stream().map(this::payment).toList();
  }
  @Override public BankingModels.Payment detail(String id) {
    return authorized==null?super.detail(id):payment(authorized.detail(id));
  }
  private BankingModels.Payment payment(ScheduledPaymentService.Receipt item) {
    return new BankingModels.Payment(item.id(),item.managed()?item.payee():"Historical reminder · "+item.payee(),
        item.amount(),item.currencyCode(),item.dueAt(),item.status(),item.accountId(),item.reference());
  }
}
