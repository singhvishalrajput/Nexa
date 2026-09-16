package com.nexa.api.banking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/demo/actions")
public class ShowcaseController {
  public record Request(
      @NotBlank
          @Pattern(
              regexp =
                  "START_TRANSFER|PAY_BILL|PAY_CARD|CANCEL_MANDATE|FREEZE_CARD|UNFREEZE_CARD|REPLACE_CARD")
          String operation,
      @Size(max = 80) String accountId,
      @NotBlank @Size(max = 80) String targetId,
      @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount) {}

  private final ShowcaseService demo;

  public ShowcaseController(ShowcaseService demo) {
    this.demo = demo;
  }

  @PostMapping("/prepare")
  public ShowcaseService.Receipt prepare(@Valid @RequestBody Request request) {
    return demo.prepare(
        request.operation(), request.accountId(), request.targetId(), request.amount());
  }

  @PostMapping("/{id}/confirm")
  public ShowcaseService.Receipt confirm(@PathVariable String id) {
    return demo.confirm(id);
  }

  @PostMapping("/{id}/cancel")
  public ShowcaseService.Receipt cancel(@PathVariable String id) {
    return demo.cancel(id);
  }

  @GetMapping("/{id}")
  public ShowcaseService.Receipt status(@PathVariable String id) {
    return demo.status(id);
  }

  @GetMapping
  public List<ShowcaseService.Receipt> history() {
    return demo.history();
  }
}
