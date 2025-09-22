//package com.example.springAIDemo.redis_rag;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.List;
//
//@RestController
//@RequestMapping("/api/vectors")
//public class VectorController {
//
//    @Autowired
//    private VectorService vectorService;
//
//    @PostMapping("/store")
//    public String storeVector(@RequestBody VectorRequest request) {
//        vectorService.storeTextAsVector(request.getId(), request.getContent());
//        return "Stored successfully!";
//    }
//
//    @PostMapping("/search")
//    public List<String> searchVectors(@RequestBody SearchRequest request) {
//        return vectorService.searchSimilarTexts(request.getQuery(), request.getTopK());
//    }
//}
