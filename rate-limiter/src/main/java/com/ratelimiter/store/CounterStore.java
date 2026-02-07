package com.ratelimiter.store;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Storage abstraction for rate limit state.
 *
 * <p>Implementations must be <b>thread-safe</b>. The {@code compute()} method
 * must guarantee atomicity per key (e.g. via {@code ConcurrentHashMap.compute()}).</p>
 *
 * <p>Values are stored as {@code Object} because different strategies use
 * different data models (counters, timestamp deques, token buckets).
 * Each strategy casts internally — safe because a single strategy runs per engine.</p>
 *
 * <p>Current implementations:
 * <ul>
 *   <li>{@link InMemoryStore} — V3.0 (ConcurrentHashMap)</li>
 *   <li>{@code RedisStore} — planned for V3.1</li>
 * </ul>
 */
public interface CounterStore {

    /**
     * Atomically computes a new value for the given key.
     *
     * @param key               the storage key
     * @param remappingFunction receives (key, existingValueOrNull) → newValue
     * @return the new value associated with the key
     */
    Object compute(String key, BiFunction<String, Object, Object> remappingFunction);

    /** Returns the value for the given key, or {@code null}. */
    Object get(String key);

    /** Removes the entry for the given key. */
    void remove(String key);

    /** Returns all keys currently in the store. */
    Set<String> keys();

    /** Iterates over all entries. */
    void forEach(BiConsumer<String, Object> action);

    /** Returns the number of entries. */
    int size();

    /** Removes all entries. */
    void clear();
}
