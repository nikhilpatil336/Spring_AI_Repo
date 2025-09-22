package com.example.springAIDemo.new_redis_rag;

//import com.example.model.EmbeddingRequest;
//import com.example.service.EmbeddingService;
import com.example.springAIDemo.new_redis_rag.EmbeddingRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/embedding")
public class EmbeddingController {

    @Autowired
    private EmbeddingServiceNew embeddingService;

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

    @PostMapping("/pdf")
    public String embedPdf(@RequestBody EmbeddingRequest request) {
        return embeddingService.embedPdfAndStore(request.getContent()); // content = PDF file path
    }
}
