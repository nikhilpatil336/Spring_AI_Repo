package com.example.springAIDemo.new_redis_rag;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Service
public class EmbeddingServiceNew {

    private final EmbeddingModel embeddingModel;
    private final RedisDAO redisDAO;
    private final ChatClient chatClient;

    public EmbeddingServiceNew(ChatClient.Builder builder, ChatMemory chatMemory, EmbeddingModel embeddingModel, RedisDAO redisDAO, OllamaChatModel ollamaChatModel) {
        this.embeddingModel = embeddingModel;
        this.redisDAO = redisDAO;
        this.chatClient = builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build()).build();
    }

    public String embedAndStore(String input) {
        // Get vector from Ollama via Spring AI
        List<float[]> embeddings = embeddingModel.embed(List.of(input));

        if (embeddings.isEmpty()) {
            throw new RuntimeException("Failed to generate embedding for input");
        }

        float[] vector = embeddings.get(0);
        String key = "Manual:" + input.hashCode();

        System.out.println(key);

        redisDAO.storeEmbedding(key, input, vector);
        return "Stored vector in Redis with key: " + key;
    }

    public String semanticSearch(String query) {
        // Step 1: Embed the query
        List<float[]> queryEmbeddingList = embeddingModel.embed(List.of(query));
        if (queryEmbeddingList.isEmpty()) {
            throw new RuntimeException("Failed to embed search query");
        }
        float[] queryVector = queryEmbeddingList.get(0);

        // Step 2: Scan Redis for all stored vectors (inefficient but works for small scale)
        Set<String> keys = redisDAO.getAllDocumentKeys();

        double bestSimilarity = -1.0;
        String bestMatchContent = null;

        for (String key : keys) {
            float[] storedVector = redisDAO.getVector(key);
            String storedContent = redisDAO.getContent(key);

            if (storedVector == null || storedContent == null) continue;

            double similarity = cosineSimilarity(queryVector, storedVector);
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatchContent = storedContent;
            }
        }

        return bestMatchContent != null
                ? "Best match: \"" + bestMatchContent + "\"\n(similarity: " + bestSimilarity + ")"
                : "No match found.";
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public String summarizeText(String content) {

        String response = chatClient.prompt()
                .user(content)
                .system("summarize this ")
                .call()
                .content();

        return String.valueOf(new ResponseEntity<>(response, org.springframework.http.HttpStatus.OK));
    }

    public String answerWithContext(String question) {
        // Step 1: Embed the question
        List<float[]> queryEmbeddingList = embeddingModel.embed(List.of(question));
        if (queryEmbeddingList.isEmpty()) {
            throw new RuntimeException("Failed to embed the question");
        }
        float[] queryVector = queryEmbeddingList.get(0);

        // Step 2: Search for similar documents
        Set<String> keys = redisDAO.getAllDocumentKeys();
        Map<String, Double> similarityMap = new HashMap<>();

        for (String key : keys) {
            float[] storedVector = redisDAO.getVector(key);
            if (storedVector == null) continue;

            double similarity = cosineSimilarity(queryVector, storedVector);
            similarityMap.put(key, similarity);
        }

        // Step 3: Sort by similarity (top 3 results)
        List<String> topKeys = similarityMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        StringBuilder contextBuilder = new StringBuilder();
        for (String key : topKeys) {
            String content = redisDAO.getContent(key);
            if (content != null) {
                contextBuilder.append("- ").append(content).append("\n");
            }
        }

        String context = contextBuilder.toString();

        System.out.println("closed phrases: " + context);

        // Step 4: Construct prompt
        String promptText = """
            Use the following context to answer the question.

            Context:
            %s

            Question:
            %s
            """.formatted(context, question);

        String response = chatClient.prompt()
                .user(promptText)
                .call()
                .content();

        return response;
    }

    public String embedPdfAndStore(String pdfPath) {
        try {
            // Load PDF and extract text
            PDDocument document = PDDocument.load(new File(pdfPath));
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            document.close();

            if (text.isBlank()) {
                return "PDF is empty or could not be read.";
            }

            List<String> chunks = splitIntoChunks(text, 300, 60); // Utility function

            int chunkCount = 0;
            for (String chunk : chunks) {
                List<float[]> embeddings = embeddingModel.embed(List.of(chunk));
                if (embeddings.isEmpty()) continue;

                float[] vector = embeddings.get(0);
                String key = "Manual:" + pdfPath.hashCode() + ":" + chunkCount;

                redisDAO.storeEmbedding(key, chunk, vector);
                chunkCount++;
            }

            return "Stored " + chunkCount + " PDF chunks in Redis.";

        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to process PDF: " + e.getMessage();
        }
    }

    private List<String> splitIntoChunks(String text, int chunkSize, int overlap) {
        String[] words = text.split("\\s+");
        List<String> chunks = new ArrayList<>();

        for (int start = 0; start < words.length; start += (chunkSize - overlap)) {
            int end = Math.min(start + chunkSize, words.length);
            StringBuilder chunk = new StringBuilder();
            for (int i = start; i < end; i++) {
                chunk.append(words[i]).append(" ");
            }
            chunks.add(chunk.toString().trim());
        }

        return chunks;
    }
}