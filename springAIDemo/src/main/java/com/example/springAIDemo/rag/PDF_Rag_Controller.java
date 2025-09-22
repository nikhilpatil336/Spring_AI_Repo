package com.example.springAIDemo.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("/pdf")
public class PDF_Rag_Controller {

    private final ChatClient chatClient;
//    private final ChatClient redisChatClient;

//    public PDF_Rag_Controller(ChatClient.Builder builder,@Qualifier(value = "Simple_VectorStore") VectorStore vectorStore, @Qualifier("Redis_VectorStore") VectorStore redisVectorStore) {
public PDF_Rag_Controller(ChatClient.Builder builder,@Qualifier(value = "PDF_VectorStore") VectorStore vectorStore) {
        this.chatClient = builder
                .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore))
                .build();

//        this.redisChatClient = builder
//                .defaultAdvisors(new QuestionAnswerAdvisor(redisVectorStore))
//                .build();
    }

    @PostMapping("/rag")
    public ResponseEntity<?> faq(@RequestBody String message) {
        return ResponseEntity.ok(chatClient.prompt()
                .user(message)
                .call()
                .content());
    }

//    @PostMapping("/redis")
//    public ResponseEntity<?> queryRedisRag(@RequestBody String prompt) {
//        String response = redisChatClient.prompt()
//                .user(prompt)
//                .call()
//                .content();
//        return ResponseEntity.ok(response);
//    }
}