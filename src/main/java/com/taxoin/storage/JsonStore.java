package com.taxoin.storage;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Generic thread-safe JSON persistence.
 * Used by GenesisRegistry, ServiceRegistry, ReputationTracker.
 *
 * Python equivalent pattern:
 *   json.dump(data, open(path, 'w'))
 *   json.load(open(path))
 */
public class JsonStore<T> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final Path path;
    private final Class<T> type;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public JsonStore(Path path, Class<T> type) {
        this.path = path;
        this.type = type;
    }

    public void save(T data) {
        lock.writeLock().lock();
        try {
            Files.createDirectories(path.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save to " + path, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Returns null if file doesn't exist yet. */
    public T load() {
        lock.readLock().lock();
        try {
            if (!Files.exists(path)) return null;
            return MAPPER.readValue(path.toFile(), type);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load from " + path, e);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean exists() {
        return Files.exists(path);
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
