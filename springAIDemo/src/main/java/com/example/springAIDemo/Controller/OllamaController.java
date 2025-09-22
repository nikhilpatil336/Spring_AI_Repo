package com.example.springAIDemo.Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ollama")
public class OllamaController {

    private Logger LOGGER = LoggerFactory.getLogger(OllamaController.class);

    private ChatClient ollamaChatClient;

    public OllamaController(OllamaChatModel ollamaChatModel) {
        this.ollamaChatClient = ChatClient.create(ollamaChatModel);
    }

    @PostMapping("/blockingcall")
    public ResponseEntity<?> sendBlockingCall(@RequestBody String message) {

        LOGGER.info("Message: {}", message);

        String response = ollamaChatClient.prompt()
                .user(message)
                .call()
                .content();

        LOGGER.info("Response: {}", response);

        return new ResponseEntity<>(response, org.springframework.http.HttpStatus.OK);
    }

    @PostMapping("/chatresponse")
    public ResponseEntity<?> getChatResponse(@RequestBody String message) {

        LOGGER.info("Message: {}", message);

        ChatResponse response = ollamaChatClient.prompt()
                .user(message)
                .call()
                .chatResponse();

        LOGGER.info("Response: {}", response);

        return new ResponseEntity<>(response, org.springframework.http.HttpStatus.OK);
    }
}
