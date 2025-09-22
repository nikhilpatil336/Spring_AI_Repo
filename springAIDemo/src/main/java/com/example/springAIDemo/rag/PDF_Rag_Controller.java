package com.example.springAIDemo.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("/pdf")
public class PDF_Rag_Controller {

    private final ChatClient chatClient;

    public PDF_Rag_Controller(ChatClient.Builder builder,@Qualifier(value = "PDF_VectorStore") VectorStore vectorStore) {
        this.chatClient = builder
                .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore))
                .build();
    }

    @PostMapping("/rag")
    public ResponseEntity<?> faq(@RequestBody String message) {
        return ResponseEntity.ok(chatClient.prompt()
                .user(message)
                .call()
                .content());
    }
}