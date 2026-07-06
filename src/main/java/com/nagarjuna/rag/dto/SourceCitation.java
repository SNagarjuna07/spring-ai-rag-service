package com.nagarjuna.rag.dto;

/**
 * One retrieved chunk that backed the answer. {@code snippet} is trimmed, not the
 * full chunk - enough for the caller to verify relevance without dumping the whole doc.
 */
public record SourceCitation(

        String fileName,

        String snippet
) {}
