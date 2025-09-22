package com.example.springAIDemo.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DateTimeController {

    private final ChatClient chatClient;

    public DateTimeController(ChatClient.Builder builder)
    {
        this.chatClient = builder.build();
    }

    @GetMapping("/tools")
    public String getTools(@RequestBody String message)
    {
        return chatClient.prompt().user("What is tommorows date?").call().content();
    }

    @GetMapping("/personalInfo")
    public String getPersonalInfo(@RequestBody String message)
    {
        return chatClient.prompt()
                .user(message)
                .tools(new DateTimeTools())
                .call()
                .content();
    }

    @GetMapping("/getNumber")
    public String getNumber(@RequestBody String message)
    {
        return chatClient.prompt()
                .user(message)
                .tools(new DateTimeTools())
                .call()
                .content();
    }
}