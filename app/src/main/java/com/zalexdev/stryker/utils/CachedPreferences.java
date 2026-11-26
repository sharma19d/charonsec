package com.zalexdev.stryker.utils;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory mirror of the encrypted preferences. Every read of
 * EncryptedSharedPreferences re-encrypts the key (~15 ms on mid-range phones), so
 * values are decrypted once and served from memory; writes go to both.
 * Safe because the app runs in a single process.
 */
final class CachedPreferences implements SharedPreferences {

    private static final Object NULL = new Object();

    private final SharedPreferences store;
    private final ConcurrentHashMap<String, Object> values = new ConcurrentHashMap<>();

    CachedPreferences(@NonNull SharedPreferences store) {
        this.store = store;
        for (Map.Entry<String, ?> e : store.getAll().entrySet()) {
            if (e.getKey() != null) values.put(e.getKey(), e.getValue() == null ? NULL : e.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T get(String key, T def, Class<?> type) {
        Object v = key == null ? null : values.get(key);
        if (v == null || v == NULL || !type.isInstance(v)) return def;
        return (T) v;
    }

    @Override
    public Map<String, ?> getAll() {
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, Object> e : values.entrySet()) out.put(e.getKey(), e.getValue() == NULL ? null : e.getValue());
        return out;
    }

    @Nullable @Override public String getString(String key, @Nullable String def) { return get(key, def, String.class); }

    @SuppressWarnings("unchecked")
    @Nullable @Override public Set<String> getStringSet(String key, @Nullable Set<String> def) {
        Set<String> s = get(key, null, Set.class);
        return s == null ? def : new HashSet<>(s);
    }

    @Override public int getInt(String key, int def) { return get(key, def, Integer.class); }
    @Override public long getLong(String key, long def) { return get(key, def, Long.class); }
    @Override public float getFloat(String key, float def) { return get(key, def, Float.class); }
    @Override public boolean getBoolean(String key, boolean def) { return get(key, def, Boolean.class); }
    @Override public boolean contains(String key) { return key != null && values.containsKey(key); }

    @Override
    public Editor edit() {
        return new CachedEditor(store.edit());
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
        store.registerOnSharedPreferenceChangeListener(l);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
        store.unregisterOnSharedPreferenceChangeListener(l);
    }

    private final class CachedEditor implements Editor {
        private final Editor inner;
        private final Map<String, Object> puts = new HashMap<>();
        private final Set<String> removes = new HashSet<>();
        private boolean clear;

        CachedEditor(Editor inner) { this.inner = inner; }

        private Editor put(String key, Object v) {
            if (v == null) return remove(key);
            removes.remove(key);
            puts.put(key, v);
            return this;
        }

        @Override public Editor putString(String key, @Nullable String v) { inner.putString(key, v); return put(key, v); }
        @Override public Editor putStringSet(String key, @Nullable Set<String> v) {
            inner.putStringSet(key, v);
            return put(key, v == null ? null : new HashSet<>(v));
        }
        @Override public Editor putInt(String key, int v) { inner.putInt(key, v); return put(key, v); }
        @Override public Editor putLong(String key, long v) { inner.putLong(key, v); return put(key, v); }
        @Override public Editor putFloat(String key, float v) { inner.putFloat(key, v); return put(key, v); }
        @Override public Editor putBoolean(String key, boolean v) { inner.putBoolean(key, v); return put(key, v); }

        @Override public Editor remove(String key) {
            inner.remove(key);
            puts.remove(key);
            removes.add(key);
            return this;
        }

        @Override public Editor clear() {
            inner.clear();
            clear = true;
            return this;
        }

        private void publish() {
            if (clear) values.clear();
            for (String k : removes) values.remove(k);
            values.putAll(puts);
        }

        @Override public boolean commit() {
            publish();
            return inner.commit();
        }

        @Override public void apply() {
            publish();
            inner.apply();
        }
    }
}
