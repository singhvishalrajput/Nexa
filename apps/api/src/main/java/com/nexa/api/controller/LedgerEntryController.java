package com.nexa.api.controller;
import com.nexa.api.beans.Account;
import com.nexa.api.beans.LedgerEntry;
import com.nexa.api.service.LedgerEntryService;


import com.nexa.api.beans.LedgerEntry;
import com.nexa.api.service.LedgerEntryService;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ledger-entries")
public class LedgerEntryController {
  @Autowired LedgerEntryService service;

  @GetMapping
  public List<LedgerEntry> all() {
    return service.getAll();
  }

  @GetMapping("/{id}")
  public LedgerEntry one(@PathVariable Long id) {
    return service.getById(id);
  }

  @GetMapping("/account/{accountId}")
  public List<LedgerEntry> account(@PathVariable Long accountId) {
    return service.byAccount(accountId);
  }

  @GetMapping("/journal-entry/{journalEntryId}")
  public List<LedgerEntry> journal(@PathVariable Long journalEntryId) {
    return service.byJournalEntry(journalEntryId);
  }
}
