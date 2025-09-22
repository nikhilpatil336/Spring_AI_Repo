package com.example.springAIDemo.redis_rag;

public class SearchRequest {
    private String query;
    private int topK;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
}
