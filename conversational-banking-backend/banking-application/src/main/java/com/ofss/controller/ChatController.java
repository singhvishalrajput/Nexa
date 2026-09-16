package com.ofss.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.ofss.beans.*;
import com.ofss.service.*;

@RestController
@RequestMapping("/api/conversations")
public class ChatController {
	@Autowired
	ChatConversationService conversationService;

	@Autowired
	ChatMessageService messageService;

	@Autowired
	AuthorizationService authorizationService;

	@PostMapping
	public ResponseEntity<ChatConversation> create(@RequestBody CreateConversationRequest request,
			Authentication authentication) {
		Long customerId = authorizationService.getCustomerId(authentication);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(conversationService.create(customerId, request.getTitle()));
	}

	@GetMapping
	public List<ChatConversation> all(Authentication authentication) {
		Long customerId = authorizationService.getCustomerId(authentication);
		return conversationService.getAll(customerId);
	}

	@GetMapping("/{id}")
	public ChatConversation one(@PathVariable Long id, Authentication authentication) {
		Long customerId = authorizationService.getCustomerId(authentication);
		return conversationService.getById(id, customerId);
	}

	@PutMapping("/{id}/close")
	public ChatConversation close(@PathVariable Long id, Authentication authentication) {
		Long customerId = authorizationService.getCustomerId(authentication);
		return conversationService.close(id, customerId);
	}

	@PostMapping("/{id}/messages")
	public ResponseEntity<ChatResponse> send(@PathVariable Long id, @RequestBody SendMessageRequest request,
			Authentication authentication) {
		Long customerId = authorizationService.getCustomerId(authentication);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(messageService.send(id, customerId, request.getMessage()));
	}

	@GetMapping("/{id}/messages")
	public List<ChatMessage> messages(@PathVariable Long id, Authentication authentication) {
		Long customerId = authorizationService.getCustomerId(authentication);
		return messageService.getAll(id, customerId);
	}
}
