package com.ofss.controller;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.ofss.beans.JournalEntry;
import com.ofss.service.JournalEntryService;

@RestController
@RequestMapping("/api/journal-entries")
public class JournalEntryController {
	@Autowired
	JournalEntryService service;

	@GetMapping
	public List<JournalEntry> all() {
		return service.getAll();
	}

	@GetMapping("/{id}")
	public JournalEntry one(@PathVariable Long id) {
		return service.getById(id);
	}

	@GetMapping("/transaction/{transactionId}")
	public JournalEntry transaction(@PathVariable String transactionId) {
		return service.byTransaction(transactionId);
	}
}
