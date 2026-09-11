package com.nexa.api.conversations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nexa.api.identity.CurrentUserProvider;
import com.nexa.api.shared.errors.ResourceNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ConversationServiceTest {
  private final JdbcTemplate db = mock(JdbcTemplate.class);
  private final CurrentUserProvider user = () -> "owner";
  private final ConversationInterpreter interpreter = mock(ConversationInterpreter.class);
  private final ConversationService service =
      new ConversationService(
          db,
          user,
          interpreter,
          tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build());

  @Test
  void allConversationOperationsRejectAnotherOwnersId() {
    assertThatThrownBy(() -> service.history("other", Long.MAX_VALUE))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.append("other", "retry", "VOICE", "secret"))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.delete("other")).isInstanceOf(ResourceNotFoundException.class);
    verify(db, times(3))
        .queryForList(contains("user_id = ?"), eq(String.class), eq("other"), eq("owner"));
    verifyNoInteractions(interpreter);
    verify(db, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void voicePersistsOnlyEssenceWhileTypedMessagesKeepTheirText() {
    when(db.queryForList(anyString(), eq(String.class), eq("chat"), eq("owner")))
        .thenReturn(List.of("chat"));
    when(interpreter.interpret("please tell me my balance"))
        .thenReturn(
            new ConversationInterpreter.Interpretation(
                "BALANCE", "Check balances.", "Your balance is INR 10."));
    service.append("chat", "v1", "VOICE", "please tell me my balance");
    verify(db)
        .update(
            startsWith("INSERT INTO conversation_turns"),
            eq("chat"),
            eq("v1"),
            eq("VOICE"),
            eq("BALANCE"),
            eq("Check balances."),
            eq("Your balance is INR 10."),
            isNull());
    service.append("chat", "t1", "TEXT", "please tell me my balance");
    verify(db)
        .update(
            startsWith("INSERT INTO conversation_turns"),
            eq("chat"),
            eq("t1"),
            eq("TEXT"),
            eq("BALANCE"),
            eq("please tell me my balance"),
            eq("Your balance is INR 10."),
            isNull());
  }

  @Test
  void retryReturnsOriginalTurnWithoutGeneratingAnotherReply() {
    when(db.queryForList(anyString(), eq(String.class), eq("chat"), eq("owner")))
        .thenReturn(List.of("chat"));
    var original =
        new ConversationService.Turn(
            "1", "retry", "VOICE", "BALANCE", "Check balances.", "INR 10", null, null);
    when(db.query(
            anyString(),
            org.mockito.ArgumentMatchers.<RowMapper<ConversationService.Turn>>any(),
            eq("chat"),
            eq("retry")))
        .thenReturn(List.of(original));
    assertThat(service.append("chat", "retry", "VOICE", "please tell me my balance"))
        .isSameAs(original);
    verifyNoInteractions(interpreter);
    verify(db, never()).update(anyString(), any(Object[].class));
  }
}
