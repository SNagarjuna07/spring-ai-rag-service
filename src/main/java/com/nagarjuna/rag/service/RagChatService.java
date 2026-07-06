package com.nagarjuna.rag.service;

import com.nagarjuna.rag.dto.AskReply;
import com.nagarjuna.rag.dto.SourceCitation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagChatService {

    // how many characters of a source chunk we show back to the user as a citation preview
    private static final int SNIPPET_LENGTH = 220;

    // injected bean built in ChatClientConfig - already wired with Vera's persona + RAG advisor + memory
    private final ChatClient chatClient;

    // called by ChatController for every incoming question
    public AskReply ask(String question, String sessionId) {

        // Figure out which "conversation thread" this question belongs to.
        // If the caller passed a sessionId, reuse it so ChatMemory can recall earlier
        // messages in that thread. If not, generate a throwaway id — the memory advisor
        // requires *some* conversation id on every call, even for a one-off question.
        String conversationId = (sessionId != null && !sessionId.isBlank())
                ? sessionId
                : "stateless-" + UUID.randomUUID();

        // Send the question to the LLM. Before the LLM ever sees it, QuestionAnswerAdvisor
        // (wired in ChatClientConfig) intercepts this call: embeds the question, does a
        // similarity search against pgvector, and rewrites the prompt to include the
        // matching chunks as context. MessageChatMemoryAdvisor also injects prior messages
        // from this conversationId. chatClientResponse() (not .content()) is used instead
        // of the plain-text shortcut because we need the advisor metadata afterward, not
        // just the answer text.
        ChatClientResponse response = chatClient.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatClientResponse();

        // Pull the plain answer text out of the response - this is what actually
        // goes back to the user as "the answer".
        String answer = response
                .chatResponse()
                .getResult()
                .getOutput()
                .getText();

        // Recover the exact chunks QuestionAnswerAdvisor retrieved for this question.
        // It stores them in the response's shared context map under a well-known key
        // this is how we get citations without re-running the search ourselves.
        @SuppressWarnings("unchecked")
        List<Document> retrieved = (List<Document>) response.context()
                .getOrDefault(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS, List.of());

        // Turn each retrieved chunk into a small citation the client can render
        // filename it came from, plus a trimmed preview of the matching text.
        List<SourceCitation> sources = retrieved
                .stream()
                .map(doc -> new SourceCitation(
                        String.valueOf(doc.getMetadata().getOrDefault("filename", "unknown")),
                        truncate(doc.getText())
                ))
                .toList();

        // Bundle answer + citations into one DTO for the controller to return as JSON.
        return new AskReply(answer, sources);
    }

    private String truncate(String text) {

        if (text == null) {
            return "";
        }

        String trimmed = text.strip();

        return trimmed.length() <= SNIPPET_LENGTH
                ? trimmed
                : trimmed.substring(0, SNIPPET_LENGTH)
                  + "…";
    }
}