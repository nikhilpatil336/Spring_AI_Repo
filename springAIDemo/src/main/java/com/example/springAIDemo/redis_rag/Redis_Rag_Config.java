//package com.example.springAIDemo.redis_rag;
//
//import org.springframework.ai.document.Document;
//import org.springframework.ai.embedding.EmbeddingModel;
//import org.springframework.ai.reader.TextReader;
//import org.springframework.ai.transformer.splitter.TokenTextSplitter;
//import org.springframework.ai.vectorstore.redis.RedisVectorStore;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.context.annotation.Bean;
//import redis.clients.jedis.JedisPool;
//import redis.clients.jedis.JedisPooled;
//
//import java.util.List;
//
//public class Redis_Rag_Config {
//    @Bean
//    public JedisPool jedisPool() {
//        return new JedisPool("localhost", 6379); // Or use your Redis URI
//    }
//
//
////    @Bean
////    @Qualifier("Redis_VectorStore")
////    public RedisVectorStore redisVectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
//////        RedisVectorStore redisVectorStore = RedisVectorStore.builder(jedisPooled, embeddingModel)
////////                .indexName("my-index")
////////                .contentFieldName("content")
////////                .embeddingFieldName("embedding")
////////                .initializeSchema(true)
//////                .build();
////
////        jedisPooled.set
////
////        TextReader textReader = new TextReader(models);
////        textReader.getCustomMetadata().put("Filename", "models.txt");
////        List<Document> documents = textReader.get();
//////        log.info("List of Documents: {}", documents);
////
////        TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();
////        List<Document> splitDocuments = tokenTextSplitter.apply(documents);
//////        log.info("Split Documents: {}", splitDocuments);
////
////        redisVectorStore.add(splitDocuments);
////        log.info("added things in redis.");
//////        redisVectorStore.doAdd(splitDocuments);
//////        redisVectorStore.write(splitDocuments);
////
////        return redisVectorStore;
////    }
//}
