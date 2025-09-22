package com.example.springAIDemo.Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/bank")
public class BankController {

    private Logger LOGGER = LoggerFactory.getLogger(BankController.class);

    private ChatClient ollamaChatClient;

    public BankController(OllamaChatModel ollamaChatModel) {
        this.ollamaChatClient = ChatClient.create(ollamaChatModel);
    }

    @PostMapping("/bankenquiry")
    public ResponseEntity<String> bankenquiry(@RequestBody String message) {

        LOGGER.info("Message: {}", message);

        var systemInstruction = """
                You are a customer service assistant for bank.
                You can only discuss:
                - Account balances and transactions
                - Branch locations and hours
                - General banking services
                                
                If asked about anything else, respond: "I can only help you with banking-related questions"
                """;

        String response = ollamaChatClient.prompt()
                .user(message)
                .system(systemInstruction)
                .call()
                .content();

        LOGGER.info("Response: {}", response);

        return new ResponseEntity<>(response, org.springframework.http.HttpStatus.OK);
    }


    @PostMapping("/branchdetails")
    public ResponseEntity<?> branchdetails(@RequestBody String message) {

        LOGGER.info("Message: {}", message);

        var systemInstruction = """
                Branch details generation guidelines:
                                
                - Give branch full name
                - give employee number
                - give branch current manager name
                - what are braches next goals
                                
                the tone of the response should be positive.
                """;

        String response = ollamaChatClient.prompt()
                .user(u -> {
                    u.text("Write details about branch {message}");
                    u.param("message", message);
                })
                .system(systemInstruction)
                .call()
                .content();

        LOGGER.info("Response: {}", response);

        return new ResponseEntity<>(response, org.springframework.http.HttpStatus.OK);
    }
}
