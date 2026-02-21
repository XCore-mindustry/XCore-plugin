package org.xcore.plugin.common;

import java.util.AbstractSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class BiMap<K, V> {
    private final HashMap<K, V> forward = new HashMap<>();
    private final HashMap<V, K> inverse = new HashMap<>();

    private final Set<K> keySetView = Collections.unmodifiableSet(forward.keySet());
    private final Set<Map.Entry<K, V>> entrySetView = Collections.unmodifiableSet(new EntrySetView());

    public V put(K key, V value) {
        var hasKey = forward.containsKey(key);
        var previousValue = forward.get(key);
        if (hasKey) {
            inverse.remove(previousValue);
        }

        var hasValue = inverse.containsKey(value);
        var previousKey = inverse.get(value);
        if (hasValue) {
            forward.remove(previousKey);
        }

        forward.put(key, value);
        inverse.put(value, key);
        return previousValue;
    }

    public V get(K key) {
        return forward.get(key);
    }

    public K getByValue(V value) {
        return inverse.get(value);
    }

    public boolean containsKey(Object key) {
        return forward.containsKey(key);
    }

    public boolean containsValue(Object value) {
        return inverse.containsKey(value);
    }

    public Set<K> keySet() {
        return keySetView;
    }

    public Set<Map.Entry<K, V>> entrySet() {
        return entrySetView;
    }

    public int size() {
        return forward.size();
    }

    public boolean isEmpty() {
        return forward.isEmpty();
    }

    public void clear() {
        forward.clear();
        inverse.clear();
    }

    @Override
    public String toString() {
        return forward.toString();
    }

    private class EntrySetView extends AbstractSet<Map.Entry<K, V>> {
        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            var iterator = forward.entrySet().iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Map.Entry<K, V> next() {
                    var entry = iterator.next();
                    return Map.entry(entry.getKey(), entry.getValue());
                }
            };
        }

        @Override
        public int size() {
            return forward.size();
        }

        @Override
        public boolean contains(Object o) {
            return forward.entrySet().contains(o);
        }
    }
}
