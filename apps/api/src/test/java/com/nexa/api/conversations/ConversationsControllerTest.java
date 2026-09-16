package com.nexa.api.conversations;
import com.nexa.api.beans.BankingContent;
import com.nexa.api.controller.ConversationsController;
import com.nexa.api.exep.ApiExceptionHandler;
import com.nexa.api.service.ConversationService;


import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nexa.api.exep.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConversationsControllerTest {
  private final ConversationService service = mock(ConversationService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new ConversationsController(service))
          .setControllerAdvice(new ApiExceptionHandler())
          .build();
  private final String route = "/api/v1/conversations/11111111-1111-4111-8111-111111111111/turns";

  @Test
  void rejectsAudioPayloadsAndInvalidInput() throws Exception {
    mvc.perform(
            post(route)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
{"clientId":"11111111-1111-4111-8111-111111111111","source":"VOICE","text":"balance","audio":"base64-data"}
"""))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(route)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
{"clientId":"11111111-1111-4111-8111-111111111111","source":"AUDIO","text":"balance"}
"""))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(route)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clientId":"11111111-1111-4111-8111-111111111111","source":"VOICE","text":""}
                    """))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void returnsStructuredEnvelopeThroughTheExistingChatEndpoint() throws Exception {
    var content = BankingContent.domain("MANDATES", "MANDATE_LIST", java.util.List.of());
    var turn =
        new ConversationService.Turn(
            "1",
            "11111111-1111-4111-8111-111111111111",
            "TEXT",
            "GET_MANDATES",
            "show mandates",
            "Here are your mandates.",
            java.time.OffsetDateTime.now(),
            content);
    when(service.append(anyString(), anyString(), eq("TEXT"), eq("show mandates")))
        .thenReturn(turn);
    mvc.perform(
            post(route)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
{"clientId":"11111111-1111-4111-8111-111111111111","source":"TEXT","text":"show mandates"}
"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.response.type").value("MANDATE_LIST"))
        .andExpect(jsonPath("$.banking.type").value("MANDATES"))
        .andExpect(jsonPath("$.response.data.mandates").isArray());
  }

  @Test
  void rejectsInvalidPagination() throws Exception {
    mvc.perform(get(route + "?before=0")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/conversations?page=-1")).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }
}
