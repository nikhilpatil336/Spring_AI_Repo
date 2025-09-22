package com.example.springAIDemo.Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemoryController {

    private final Logger LOGGER = LoggerFactory.getLogger(MemoryController.class);

    private final ChatClient ollamaChatClient;

    public MemoryController(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.ollamaChatClient = builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build()).build();
    }


    @PostMapping("/memory")
    public ResponseEntity<?> memory(@RequestBody String message)
    {
        LOGGER.info("Message: {}", message);

        String response = ollamaChatClient.prompt().user(message).call().content();

        LOGGER.info("Response: {}", response);

        return ResponseEntity.ok(response);
    }
}
