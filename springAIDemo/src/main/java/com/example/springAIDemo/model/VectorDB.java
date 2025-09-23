package com.example.springAIDemo.model;

import com.example.springAIDemo.rag.RagConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class VectorDB {

    private static final Logger log = LoggerFactory.getLogger(VectorDB.class);

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

    private double cosineSimilarity(float[] vec1, float[] vec2) {
        double dot = 0.0, norm1 = 0.0, norm2 = 0.0;
        for (int i = 0; i < vec1.length; i++) {
            dot += vec1[i] * vec2[i];
            norm1 += Math.pow(vec1[i], 2);
            norm2 += Math.pow(vec2[i], 2);
        }
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2) + 1e-10); // avoid div by zero
    }

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
}

