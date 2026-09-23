package com.nexa.api.controller;

import com.nexa.api.service.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/loans")
public class LoanDocumentsController {
  private final LoanApplicationService applications;
  private final LoanDocumentService documents;
  private final LoanQueryService loans;
  public LoanDocumentsController(LoanApplicationService applications, LoanDocumentService documents,
      LoanQueryService loans) {
    this.applications = applications; this.documents = documents; this.loans = loans;
  }
  @GetMapping("/salary-slip-requirements")
  public List<String> requirements() { return documents.requiredMonths(); }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> apply(@RequestPart("application") CreditMandateService.LoanRequest application,
      @RequestParam("months") List<String> months, @RequestPart("files") List<MultipartFile> files) {
    var loan = applications.submit(application, months, files);
    return ResponseEntity.status(application.scheduled() ? 201 : 200)
        .body(application.scheduled() ? loans.detail(loan.get("PRODUCT_ID").toString()) : loan);
  }

  @GetMapping("/{id}/salary-slips")
  public ResponseEntity<LoanDocumentService.Bundle> list(@PathVariable String id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documents.list(id));
  }

  @PostMapping(value = "/{id}/salary-slips", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public LoanDocumentService.Bundle attach(@PathVariable String id,
      @RequestParam("months") List<String> months, @RequestPart("files") List<MultipartFile> files) {
    return documents.attach(id, months, files);
  }

  @GetMapping("/{id}/salary-slips/{documentId}")
  public ResponseEntity<byte[]> download(@PathVariable String id, @PathVariable String documentId) {
    var file = documents.download(id, documentId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
            .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
        .contentType(MediaType.parseMediaType(file.mediaType())).body(file.content());
  }
}
