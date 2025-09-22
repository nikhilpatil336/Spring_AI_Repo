//package com.example.springAIDemo.redis_rag;
//
//import org.springframework.ai.embedding.EmbeddingClient;
//import org.springframework.ai.embedding.EmbeddingModel;
//import org.springframework.ai.embedding.EmbeddingRequest;
//import org.springframework.ai.embedding.EmbeddingResponse;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import redis.clients.jedis.*;
//
//import java.nio.ByteBuffer;
//import java.util.*;
//import java.util.stream.Collectors;
//
//@Service
//public class VectorService {
//
////    @Autowired
////    private EmbeddingModel embeddingModel;
//
//    @Autowired
//    private EmbeddingClient embeddingClient;
//
//    @Autowired
//    private Jedis jedis;
//
//    private static final int EMBEDDING_DIM = 1536;
//
//    public void storeTextAsVector(String id, String content) {
//        float[] embedding = generateEmbedding(content);
//
//        Map<String, String> doc = new HashMap<>();
//        doc.put("content", content);
//        doc.put("embedding", Base64.getEncoder().encodeToString(floatArrayToBytes(embedding)));
//
//        jedis.hset("doc:" + id, doc);
//    }
//
//    public List<String> searchSimilarTexts(String query, int k) {
//        float[] queryVector = generateEmbedding(query);
//        byte[] blob = floatArrayToBytes(queryVector);
//
//        String knnQuery = "*=>[KNN " + k + " @embedding $vec_param AS score]";
//        Map<String, Object> params = Map.of("vec_param", blob);
//
//        SearchResult result = ((UnifiedJedis) jedis).ftSearch("doc_index", knnQuery,
//                FTSearchParams.params().returnFields("content", "score").dialect(2),
//                params);
//
//        return result.getDocuments().stream()
//                .map(doc -> doc.getString("content"))
//                .collect(Collectors.toList());
//    }
//
////    private float[] generateEmbedding(String text) {
////        EmbeddingRequest request = new EmbeddingRequest(List.of(text));
////        EmbeddingResponse response = embeddingClient.embed(request);
////        List<Double> list = response.getResults().get(0).getEmbedding();
////
////        float[] result = new float[list.size()];
////        for (int i = 0; i < list.size(); i++) result[i] = list.get(i).floatValue();
////        return result;
////    }
//    private float[] generateEmbedding(String text) {
//        EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
//        EmbeddingResponse response = embeddingModel.embed(String.valueOf(request)); // Changed line
//
//        List<Double> list = response.getResults().get(0).getEmbedding();
//
//        float[] result = new float[list.size()];
//        for (int i = 0; i < list.size(); i++) {
//            result[i] = list.get(i).floatValue();
//        }
//        return result;
//    }
//
//    private byte[] floatArrayToBytes(float[] array) {
//        ByteBuffer buffer = ByteBuffer.allocate(4 * array.length);
//        for (float f : array) buffer.putFloat(f);
//        return buffer.array();
//    }
//}
//
