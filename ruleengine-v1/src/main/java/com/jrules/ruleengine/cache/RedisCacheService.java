package com.jrules.ruleengine.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisCacheService implements CacheServiceable {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getFromCache(String map, String key) {
        String redisKey = buildKey(map, key);
        Object value = redisTemplate.opsForValue().get(redisKey);
        return (T) value;
    }

    @Override
    public void storeInCache(String map, String key, Object value, long ttl, TimeUnit unit) {
        String redisKey = buildKey(map, key);
        redisTemplate.opsForValue().set(redisKey, value, ttl, unit);
    }

    private String buildKey(String map, String key) {
        return map + "::" + key;
    }
}
