package com.nagarjuna.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatMemory chatMemory() {

        return MessageWindowChatMemory.builder()
                .maxMessages(30)
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .build();
    }

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            VectorStore vectorStore,
            @Value("classpath:/prompts/system.st") Resource systemPromptFile
    ) {

        PromptTemplate promptTemplate = new PromptTemplate(systemPromptFile);

        String prompt = promptTemplate
                .render(
                        Map.of(
                                "date",
                                LocalDate.now(Clock.systemUTC()).toString()
                        )
                );

        // RAG config
        QuestionAnswerAdvisor ragAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(4)
                        .similarityThreshold(0.2)
                        .build()
                )
                .build();

        return builder.defaultSystem(prompt)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).order(1).build(),
                        ragAdvisor,
                        new SimpleLoggerAdvisor()
                )
                .build();
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {

        return TokenTextSplitter
                .builder()
                .build();
    }

}
