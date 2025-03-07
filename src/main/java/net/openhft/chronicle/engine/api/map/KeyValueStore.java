/*
 *     Copyright (C) 2015  higherfrequencytrading.com
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.openhft.chronicle.engine.api.map;

import net.openhft.chronicle.bytes.BytesUtil;
import net.openhft.chronicle.core.io.Closeable;
import net.openhft.chronicle.engine.api.EngineReplication.ReplicationEntry;
import net.openhft.chronicle.engine.api.pubsub.InvalidSubscriberException;
import net.openhft.chronicle.engine.api.pubsub.SubscriptionConsumer;
import net.openhft.chronicle.engine.api.tree.Assetted;
import net.openhft.lang.model.constraints.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Internal API for creating new data stores.
 *
 * @param <K>  key type
 * @param <V>  immutable value type
 */

public interface KeyValueStore<K, V> extends Assetted<KeyValueStore<K, V>>, Closeable,
        Consumer<ReplicationEntry> {

    /**
     * put an entry
     *
     * @param key   to set
     * @param value to set
     * @return true if it was replaced or the value is identical, false if it was added.
     */
    boolean put(K key, V value);

    @org.jetbrains.annotations.Nullable
    V getAndPut(K key, V value);

    /**
     * remove a key
     *
     * @param key to remove
     * @return true if it was removed, false if not.
     */
    boolean remove(K key);

    @org.jetbrains.annotations.Nullable
    V getAndRemove(K key);

    @org.jetbrains.annotations.Nullable
    @Nullable
    default V get(K key) {
        return getUsing(key, null);
    }

    @org.jetbrains.annotations.Nullable
    @Nullable
    V getUsing(K key, Object value);

    default boolean containsKey(K key) {
        return get(key) != null;
    }

    default boolean isReadOnly() {
        return false;
    }

    long longSize();

    default int segments() {
        return 1;
    }

    default int segmentFor(K key) {
        return 0;
    }

    void keysFor(int segment, SubscriptionConsumer<K> kConsumer) throws InvalidSubscriberException;

    void entriesFor(int segment, SubscriptionConsumer<MapEvent<K, V>> kvConsumer) throws InvalidSubscriberException;

    default Iterator<Map.Entry<K, V>> entrySetIterator() {
        // todo optimise
        List<Map.Entry<K, V>> entries = new ArrayList<>();
        try {
            for (int i = 0, seg = segments(); i < seg; i++)
                entriesFor(i, e -> entries.add(e));
        } catch (InvalidSubscriberException e) {
            throw new AssertionError(e);
        }
        return entries.iterator();
    }

    default Iterator<K> keySetIterator() {
        // todo optimise
        List<K> keys = new ArrayList<>();
        try {
            for (int i = 0, seg = segments(); i < seg; i++)
                keysFor(i, keys::add);
        } catch (InvalidSubscriberException e) {
            throw new AssertionError(e);
        }
        return keys.iterator();
    }

    void clear();

    @org.jetbrains.annotations.Nullable
    @Nullable
    default V replace(K key, V value) {
        if (containsKey(key)) {
            return getAndPut(key, value);
        } else {
            return null;
        }
    }

    default boolean replaceIfEqual(K key, V oldValue, V newValue) {
        if (containsKey(key) && BytesUtil.equals(get(key), oldValue)) {
            put(key, newValue);
            return true;
        } else
            return false;
    }

    default boolean removeIfEqual(K key, V value) {
        if (!isKeyType(key))
            return false;
        if (containsKey(key) && BytesUtil.equals(get(key), value)) {
            remove(key);
            return true;
        } else
            return false;
    }

    default boolean isKeyType(Object key) {
        return true;
    }

    @org.jetbrains.annotations.Nullable
    default V putIfAbsent(K key, V value) {
        V value2 = get(key);
        return value2 == null ? getAndPut(key, value) : value2;
    }

    default boolean keyedView() {
        return true;
    }

    @NotNull
    default Iterator<V> valuesIterator() {
        // todo optimise
        List<V> entries = new ArrayList<>();
        try {
            for (int i = 0, seg = segments(); i < seg; i++)
                entriesFor(i, e -> entries.add(e.getValue()));
        } catch (InvalidSubscriberException e) {
            throw new AssertionError(e);
        }
        return entries.iterator();
    }

    boolean containsValue(V value);

    interface Entry<K, V> {
        @org.jetbrains.annotations.Nullable
        K key();

        @org.jetbrains.annotations.Nullable
        V value();
    }
}
