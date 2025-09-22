//package com.example.springAIDemo.redis_rag;
//
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class PostConstruct {
//
//    @PostConstruct
//    public void initIndex() {
//        try {
//            String command = "FT.CREATE doc_index ON HASH PREFIX 1 doc: SCHEMA " +
//                    "content TEXT " +
//                    "embedding VECTOR FLAT 6 TYPE FLOAT32 DIM 1536 DISTANCE_METRIC COSINE";
//            jedis.sendCommand(Protocol.Command.valueOf("FT.CREATE"), command.split(" "));
//        } catch (Exception e) {
//            // Ignore if index exists
//        }
//    }
//
//}
