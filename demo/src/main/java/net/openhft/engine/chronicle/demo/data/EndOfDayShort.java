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

package net.openhft.engine.chronicle.demo.data;

import net.openhft.chronicle.wire.Marshallable;
import net.openhft.chronicle.wire.WireIn;
import net.openhft.chronicle.wire.WireOut;
import org.jetbrains.annotations.NotNull;

/**
 * Created by peter on 27/08/15.
 */
public class EndOfDayShort implements Marshallable {
    long daysVolume;
    // Symbol,Company,Price,Change,ChangePercent,Day's Volume
    private String name;
    private double closingPrice, change, changePercent;

    @Override
    public void readMarshallable(@NotNull WireIn wire) throws IllegalStateException {
        wire.read(() -> "name").text(s -> name = s)
                .read(() -> "price").float64(d -> closingPrice = d)
                .read(() -> "change").float64(d -> change = d)
                .read(() -> "changePercent").float64(d -> changePercent = d)
                .read(() -> "daysVolume").int64(d -> daysVolume = d);
    }

    @Override
    public void writeMarshallable(WireOut wire) {
        wire.write(() -> "name").text(name)
                .write(() -> "price").float64(closingPrice)
                .write(() -> "change").float64(change)
                .write(() -> "changePercent").float64(changePercent)
                .write(() -> "daysVolume").int64(daysVolume);
    }

    @Override
    public String toString() {
        return "EndOfDayShort{" +
                "name='" + name + '\'' +
                ", closingPrice=" + closingPrice +
                ", change=" + change +
                ", changePercent=" + changePercent +
                ", daysVolume=" + daysVolume +
                '}';
    }
}
