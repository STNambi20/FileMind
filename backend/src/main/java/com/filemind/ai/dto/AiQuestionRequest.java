package com.filemind.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiQuestionRequest(
    @NotBlank(message = "Question is required.")
    @Size(max = 400, message = "Question must be 400 characters or fewer.")
    String question
) {
}
