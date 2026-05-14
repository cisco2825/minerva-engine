package com.jrules.ruleengine.cache;

import java.util.concurrent.TimeUnit;

public interface CacheServiceable {

    <T> T getFromCache(String map, String key);

    void storeInCache(String map, String key, Object value, long ttl, TimeUnit unit);
}
