package com.nagarjuna.rag.controller;

import com.nagarjuna.rag.dto.IngestResult;
import com.nagarjuna.rag.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentIngestionService ingestionService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<IngestResult> upload(@RequestParam("file") MultipartFile file) {

        IngestResult result = ingestionService.ingest(file);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(result);
    }

    @GetMapping
    public List<IngestResult> list() {
        return ingestionService.listIngested();
    }
}
