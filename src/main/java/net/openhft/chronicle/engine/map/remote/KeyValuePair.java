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

package net.openhft.chronicle.engine.map.remote;

import net.openhft.chronicle.wire.Marshallable;
import net.openhft.chronicle.wire.WireIn;
import net.openhft.chronicle.wire.WireOut;
import org.jetbrains.annotations.NotNull;

/**
 * Created by peter on 07/07/15.
 */
public class KeyValuePair implements Marshallable {
    Object key;
    Object value;

    KeyValuePair(Object key, Object value) {
        this.key = key;
        this.value = value;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void readMarshallable(@NotNull WireIn wire) throws IllegalStateException {
        wire.read(() -> "key").object(Object.class, o -> key = o)
                .read(() -> "value").object(Object.class, o -> value = o);
    }

    @Override
    public void writeMarshallable(@NotNull WireOut wire) {
        wire.write(() -> "key").object(key)
                .write(() -> "value").object(value);
    }

    @NotNull
    public static KeyValuePair of(Object key, Object value) {
        return new KeyValuePair(key, value);
    }
}
