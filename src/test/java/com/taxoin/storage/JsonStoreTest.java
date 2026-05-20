package com.taxoin.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonStoreTest {

    @TempDir Path tmp;

    record TestData(String name, int value) {}

    @Test
    void saveAndLoad() {
        JsonStore<TestData> store = new JsonStore<>(tmp.resolve("test.json"), TestData.class);
        store.save(new TestData("taxoin", 42));
        TestData loaded = store.load();
        assertNotNull(loaded);
        assertEquals("taxoin", loaded.name());
        assertEquals(42, loaded.value());
    }

    @Test
    void loadReturnsNullWhenFileAbsent() {
        JsonStore<TestData> store = new JsonStore<>(tmp.resolve("missing.json"), TestData.class);
        assertNull(store.load());
    }

    @Test
    void existsReturnsFalseBeforeSave() {
        JsonStore<TestData> store = new JsonStore<>(tmp.resolve("x.json"), TestData.class);
        assertFalse(store.exists());
    }

    @Test
    void existsReturnsTrueAfterSave() {
        JsonStore<TestData> store = new JsonStore<>(tmp.resolve("x.json"), TestData.class);
        store.save(new TestData("a", 1));
        assertTrue(store.exists());
    }

    @Test
    void saveOverwrites() {
        JsonStore<TestData> store = new JsonStore<>(tmp.resolve("x.json"), TestData.class);
        store.save(new TestData("first", 1));
        store.save(new TestData("second", 2));
        assertEquals("second", store.load().name());
    }

    @Test
    void createsParentDirectories() {
        JsonStore<TestData> store = new JsonStore<>(
                tmp.resolve("a/b/c/test.json"), TestData.class);
        assertDoesNotThrow(() -> store.save(new TestData("deep", 99)));
        assertEquals(99, store.load().value());
    }
}
