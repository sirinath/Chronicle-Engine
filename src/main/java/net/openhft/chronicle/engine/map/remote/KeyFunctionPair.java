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

import java.io.Serializable;

/**
 * Created by peter on 07/07/15.
 */
public class KeyFunctionPair implements Marshallable {
    @NotNull
    Object key;

    @NotNull
    Object function;

    KeyFunctionPair(@NotNull Object key, @NotNull Object function) {
        this.key = key;
        assert Serializable.class.isAssignableFrom(function.getClass());
        this.function = function;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void readMarshallable(@NotNull WireIn wire) throws IllegalStateException {
        wire.read(() -> "key").object(Object.class, o -> key = o)
                .read(() -> "function").object(Object.class, f -> function = f);
    }

    @Override
    public void writeMarshallable(@NotNull WireOut wire) {
        wire.write(() -> "key").object(key)
                .write(() -> "function").object(function);
    }

    @NotNull
    public static KeyFunctionPair of(Object key, @NotNull Object function) {
        return new KeyFunctionPair(key, function);
    }
}
