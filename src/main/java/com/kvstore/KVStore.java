package com.kvstore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KVStore {

    private final Map<String, String> store;

    public KVStore() {
        this.store = new ConcurrentHashMap<>();
    }

    public void put(String key, String value) {
        store.put(key, value);
    }

    public String get(String key) {
        return store.get(key);
    }
}
