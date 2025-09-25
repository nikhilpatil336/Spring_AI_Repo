package com.example.springAIDemo.new_redis_rag;

import org.springframework.stereotype.Repository;
import redis.clients.jedis.Jedis;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Repository
public class RedisDAO {

    private final Jedis jedis;

    public RedisDAO() {
        this.jedis = new Jedis("localhost", 6379);
    }

    public void storeEmbedding(String key, String content, float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(4 * vector.length);
        for (float v : vector) {
            buffer.putFloat(v);
        }

        byte[] vectorBytes = buffer.array();

        Map<byte[], byte[]> redisMap = new HashMap<>();
        redisMap.put("content".getBytes(), content.getBytes());
        redisMap.put("embedding".getBytes(), vectorBytes);

        jedis.hset(key.getBytes(), redisMap);
    }

    public Set<String> getAllDocumentKeys(String key) {
        return jedis.keys(key + ":*");  // WARNING: use only in dev, inefficient in prod
    }

    public float[] getVector(String key) {
        byte[] vectorBytes = jedis.hget(key.getBytes(), "embedding".getBytes());
        if (vectorBytes == null) return null;

        ByteBuffer buffer = ByteBuffer.wrap(vectorBytes);
        float[] vector = new float[vectorBytes.length / 4];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }

    public String getContent(String key) {
        byte[] contentBytes = jedis.hget(key.getBytes(), "content".getBytes());
        return contentBytes != null ? new String(contentBytes) : null;
    }

}
