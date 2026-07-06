package com.nagarjuna.rag.dto;

import java.util.List;

public record AskReply(

        String answer,

        List<SourceCitation> sources
) {}
