package com.nagarjuna.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record AskRequest(

        @NotBlank(message = "Question should not be empty")
        String question,

        String sessionId // optional
) {}
