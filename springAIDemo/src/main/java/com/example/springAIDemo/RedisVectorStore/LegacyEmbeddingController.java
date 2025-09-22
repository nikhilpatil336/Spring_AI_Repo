package com.example.springAIDemo.RedisVectorStore;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.ai.document.Document;
//import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.pdfbox.pdmodel.PDDocument;

@RestController
@RequestMapping("/redis_vector_store/embedding")
public class LegacyEmbeddingController {

    private final RedisVectorStore vectorStore;
    private final ChatClient chatClient;

    public LegacyEmbeddingController(RedisVectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    @PostMapping("/add")
    public String addDoc(@RequestBody EmbeddingRequest req) {
        Document doc = new Document(req.getContent(), Map.of());
        vectorStore.add(List.of(doc));
        return "Document stored.";
    }

    @PostMapping("/ask")
    public String ask(@RequestBody EmbeddingRequest req) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(req.getContent())
                        .topK(3)
                        .build()
        );

        StringBuilder context = new StringBuilder();
        results.forEach(d -> context.append(d.getText()).append("\n"));

        System.out.println("closest phrases: "+ context);

        String promptText = """
            Context:
            %s
            Question:
            %s
            """.formatted(context, req.getContent());

//        Prompt prompt = new Prompt(promptText);
//        return chatClient.call(prompt).getResult().getOutput().getContent();

        String response = chatClient.prompt()
                .user(promptText)
                .system("summarize this ")
                .call()
                .content();

        return response;
    }

    @PostMapping("/upload-pdf-by-path")
    public String uploadPdfByPath(@RequestBody FilePathRequest request) {
        String filePathStr = request.getFilePath();
        Path filePath = Paths.get(filePathStr);

        if (!Files.exists(filePath)) {
            return "File does not exist at path: " + filePathStr;
        }

        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String content = stripper.getText(document);

            List<Document> documents = chunkText(content, 500);
            vectorStore.add(documents);

            return "PDF content loaded from path and stored as vectors.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error reading PDF file: " + e.getMessage();
        }
    }

    private List<Document> chunkText(String text, int chunkSize) {
        List<Document> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(new Document(chunk, Map.of()));
            }
            start = end;
        }

        return chunks;
    }
}