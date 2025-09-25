package com.example.springAIDemo.model;

import com.example.springAIDemo.new_redis_rag.RedisDAO;
import com.example.springAIDemo.rag.RagConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class VectorDB {

    private static final Logger log = LoggerFactory.getLogger(VectorDB.class);

    private final EmbeddingModel embeddingModel;
    private final RedisDAO redisDAO;
    private final ChatClient chatClient;

    public VectorDB(ChatClient.Builder builder, ChatMemory chatMemory, EmbeddingModel embeddingModel, RedisDAO redisDAO, OllamaChatModel ollamaChatModel)
    {
        this.embeddingModel = embeddingModel;
        this.redisDAO = redisDAO;
        this.chatClient = builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build()).build();
    }

    private final List<VectorEntry> index = new ArrayList<>();

    public void storeEmbedding(float[] embedding, String text, Map<String, String> metadata) {
        index.add(new VectorEntry(embedding, text, metadata));
    }

    public List<String> search(float[] queryEmbedding, int topK) {

        List<String> result = index.stream()
                .sorted(Comparator.comparingDouble(e -> -cosineSimilarity(queryEmbedding, e.embedding)))
                .limit(topK)
                .map(e -> e.text)
                .collect(Collectors.toList());

        log.info("Consine Similarity: " + result);

        return result;
    }

//    private double cosineSimilarity(float[] vec1, float[] vec2) {
//        double dot = 0.0, norm1 = 0.0, norm2 = 0.0;
//        for (int i = 0; i < vec1.length; i++) {
//            dot += vec1[i] * vec2[i];
//            norm1 += Math.pow(vec1[i], 2);
//            norm2 += Math.pow(vec2[i], 2);
//        }
//        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2) + 1e-10); // avoid div by zero
//    }

    private static class VectorEntry {
        float[] embedding;
        String text;
        Map<String, String> metadata;

        VectorEntry(float[] embedding, String text, Map<String, String> metadata) {
            this.embedding = embedding;
            this.text = text;
            this.metadata = metadata;
        }
    }

    public String embedAndStore(String input) {
        // Get vector from Ollama via Spring AI

        List<String> chunks = splitIntoChunks(input, 300, 60); // Utility function

        int chunkCount = 0;
        for (String chunk : chunks) {
            List<float[]> embeddings = embeddingModel.embed(List.of(chunk));
            if (embeddings.isEmpty()) continue;

            float[] vector = embeddings.get(0);
            String key = "Analysis:"+ chunkCount;

            redisDAO.storeEmbedding(key, chunk, vector);
            chunkCount++;
        }

//        List<float[]> embeddings = embeddingModel.embed(List.of(input));
//
//        if (embeddings.isEmpty()) {
//            throw new RuntimeException("Failed to generate embedding for input");
//        }
//
//        float[] vector = embeddings.get(0);
//        String key = "Analysis:" + input.hashCode();
//
//        System.out.println(key);
//
//        redisDAO.storeEmbedding(key, input, vector);
        return "Stored vector in Redis with keys";
    }

    public String semanticSearch(String query) {
        // Step 1: Embed the query
        List<float[]> queryEmbeddingList = embeddingModel.embed(List.of(query));
        if (queryEmbeddingList.isEmpty()) {
            throw new RuntimeException("Failed to embed search query");
        }
        float[] queryVector = queryEmbeddingList.get(0);

        // Step 2: Scan Redis for all stored vectors (inefficient but works for small scale)
        Set<String> keys = redisDAO.getAllDocumentKeys("Analysis");

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

    public String answerWithContext(String question) {
        // Step 1: Embed the question
        List<float[]> queryEmbeddingList = embeddingModel.embed(List.of(question));
        if (queryEmbeddingList.isEmpty()) {
            throw new RuntimeException("Failed to embed the question");
        }
        float[] queryVector = queryEmbeddingList.get(0);

        // Step 2: Search for similar documents
        Set<String> keys = redisDAO.getAllDocumentKeys("Analysis");
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
                .limit(10)
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

        return context;

//        // Step 4: Construct prompt
//        String promptText = """
//            Use the following context to answer the question.
//
//            Context:
//            %s
//
//            Question:
//            %s
//            """.formatted(context, question);
//
//        String response = chatClient.prompt()
//                .user(promptText)
//                .call()
//                .content();
//
//        return response;
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

