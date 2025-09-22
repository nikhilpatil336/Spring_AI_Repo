package com.example.springAIDemo.Controller;

import com.example.springAIDemo.model.TravelItinerary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/travel")
public class TravelController {

    private Logger LOGGER = LoggerFactory.getLogger(TravelController.class);

    private ChatClient ollamaChatClient;

    public TravelController(OllamaChatModel ollamaChatModel) {
        this.ollamaChatClient = ChatClient.create(ollamaChatModel);
    }

    @PostMapping("/iternary/structured")
    public ResponseEntity<?> structuredIternary(@RequestBody String message) {

        LOGGER.info("Message: {}", message);

        var systemInstruction = """
                You are a travel planner. Respond ONLY with a valid JSON object matching this exact structure:

                {
                  "itinerary": [
                    {
                      "activity": "string",
                      "location": "string",
                      "day": "string",
                      "time": "string"
                    },
                    ...
                  ]
                }

                Do NOT return a single object. Always return an array of activities, even if there's only one.
                No extra text, no tags, no explanation. Just the JSON.
                """;

        TravelItinerary response = null;

        try {
            response = ollamaChatClient.prompt()
                    .user(u -> {
                        u.text("I want to plan a trip to {message}. Give me a list of things to do.");
                        u.param("message", message);
                    })
                    .system(systemInstruction)
                    .call()
                    .entity(TravelItinerary.class);

            LOGGER.info("Response: {}", response);
        } catch (Exception e) {
            LOGGER.info("Exception: {}", e.getMessage());
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/iternary/structured/manual")
    public ResponseEntity<?> manualStructuredIternary(@RequestBody String message) {

        LOGGER.info("Message: {}", message);

        var systemInstruction = """
            Respond only with a valid JSON object matching the TravelItinerary class.
            Do not include any explanation, tags, or extra text.
            """;

        TravelItinerary response = null;

        try {
            String content = ollamaChatClient.prompt()
                    .user(u -> {
                        u.text("I want to plan a trip to {message}. Give me a list of things to do.");
                        u.param("message", message);
                    })
                    .system(systemInstruction)
                    .call()
                    .content();

            LOGGER.info("Raw AI Response: {}", content);

            response = new ObjectMapper().readValue(content, TravelItinerary.class);
        } catch (Exception e) {
            LOGGER.info("Exception: {}", e.getMessage());
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
