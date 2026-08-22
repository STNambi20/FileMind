package com.filemind.ai.provider;

import com.filemind.ai.dto.AiRewriteResponse;
import com.filemind.ai.dto.AiQuestionResponse;
import com.filemind.ai.dto.AiSummaryResponse;
import com.filemind.ai.dto.AiTagsResponse;

public interface AiProvider {

    AiSummaryResponse summarize(String text, int maxSentences);

    AiTagsResponse tags(String text);

    AiRewriteResponse rewrite(String text, String mode);

    default AiQuestionResponse answerQuestion(String text, String question) {
        throw new UnsupportedOperationException("Question answering is not available for the configured AI provider.");
    }
}
