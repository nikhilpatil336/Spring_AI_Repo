package com.example.springAIDemo.new_redis_rag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/embedding")
public class EmbeddingController {

    @Autowired
    private EmbeddingServiceNew embeddingService;

    //this controller will take a simple text input and store it in the form of vector in redis.
    @PostMapping
    public String embedAndStore(@RequestBody EmbeddingRequest request) {
        return embeddingService.embedAndStore(request.getContent());
    }

    @PostMapping("/search")
    public String semanticSearch(@RequestBody EmbeddingRequest request) {
        return embeddingService.semanticSearch(request.getContent());
    }

    @PostMapping("/summarize")
    public String summarize(@RequestBody EmbeddingRequest request) {
        return embeddingService.summarizeText(request.getContent());
    }

    @PostMapping("/ask")
    public String askQuestion(@RequestBody EmbeddingRequest request) {
        return embeddingService.answerWithContext(request.getContent());
    }

    //this controller take path of PDF as input and store it as vector in redis.
    @PostMapping("/pdf")
    public String embedPdf(@RequestBody EmbeddingRequest request) {
        return embeddingService.embedPdfAndStore(request.getContent()); // content = PDF file path
    }
}
