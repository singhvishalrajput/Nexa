package com.nexa.api.conversations;

import com.nexa.api.shared.errors.InvalidRequestException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationsController {
  public record TurnRequest(
      @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String clientId,
      @NotBlank @Pattern(regexp = "TEXT|VOICE") String source,
      @NotBlank @Size(max = 2000) String text) {
    // Explicitly reject audio and other extra payload fields.
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectExtra(String name, Object value) {
      throw new InvalidRequestException("Only clientId, source and text are accepted.");
    }
  }

  private final ConversationService service;

  public ConversationsController(ConversationService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ConversationService.Conversation create() {
    return service.create();
  }

  @GetMapping
  public List<ConversationService.Conversation> list(@RequestParam(defaultValue = "0") int page) {
    if (page < 0 || page > 100000) throw new InvalidRequestException("Invalid history page.");
    return service.list(page);
  }

  @GetMapping("/{id}/turns")
  public List<ConversationService.Turn> history(
      @PathVariable UUID id, @RequestParam(defaultValue = "9223372036854775807") long before) {
    if (before < 1) throw new InvalidRequestException("Invalid message cursor.");
    return service.history(id.toString(), before);
  }

  @PostMapping("/{id}/turns")
  public ConversationService.Turn append(
      @PathVariable UUID id, @Valid @RequestBody TurnRequest request) {
    return service.append(id.toString(), request.clientId(), request.source(), request.text());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(id.toString());
  }
}
