package com.rova.banking.api;

import com.rova.banking.ledger.JournalService;
import com.rova.banking.api.Views.EntryView;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/postings")
public class LedgerController {
    private final JournalService journal;
    public LedgerController(JournalService journal) { this.journal = journal; }
    @GetMapping("/{id}/ledger") @PreAuthorize("hasRole('AUDITOR')")
    public List<EntryView> ledger(@PathVariable UUID id, @RequestParam(defaultValue="0") int page,
                                  @RequestParam(defaultValue="50") int size) {
        return journal.ledger(id, page, size).stream().map(EntryView::of).toList();
    }
}
