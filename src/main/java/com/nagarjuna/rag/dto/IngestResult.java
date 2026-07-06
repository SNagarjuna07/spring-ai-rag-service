package com.nagarjuna.rag.dto;

import java.time.Instant;
import java.util.UUID;

public record IngestResult(

        UUID documentId,

        String fileName,

        int chunkCount,

        Instant ingestedAt
) {}
