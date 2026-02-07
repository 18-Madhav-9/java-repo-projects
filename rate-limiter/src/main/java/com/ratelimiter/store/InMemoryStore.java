package com.ratelimiter.store;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Thread-safe, in-memory {@link CounterStore} implementation backed
 * by a {@link ConcurrentHashMap}.
 *
 * <p>All mutations through {@code compute()} are atomic per key.
 * State is ephemeral — a restart resets all counters.</p>
 */
public class InMemoryStore implements CounterStore {

    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();

    @Override
    public Object compute(String key, BiFunction<String, Object, Object> remappingFunction) {
        return store.compute(key, remappingFunction);
    }

    @Override
    public Object get(String key) {
        return store.get(key);
    }

    @Override
    public void remove(String key) {
        store.remove(key);
    }

    @Override
    public Set<String> keys() {
        return store.keySet();
    }

    @Override
    public void forEach(BiConsumer<String, Object> action) {
        store.forEach(action);
    }

    @Override
    public int size() {
        return store.size();
    }

    @Override
    public void clear() {
        store.clear();
    }
}
