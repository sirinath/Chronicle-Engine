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

package net.openhft.chronicle.engine;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.OS;
import net.openhft.chronicle.engine.api.map.KeyValueStore;
import net.openhft.chronicle.engine.api.map.MapEvent;
import net.openhft.chronicle.engine.api.tree.LeafViewFactory;
import net.openhft.chronicle.engine.map.AuthenticatedKeyValueStore;
import net.openhft.chronicle.engine.map.FilePerKeyValueStore;
import net.openhft.chronicle.engine.map.VanillaMapView;
import net.openhft.chronicle.engine.map.VanillaStringMarshallableKeyValueStore;
import net.openhft.chronicle.engine.tree.VanillaAsset;
import net.openhft.chronicle.wire.*;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static net.openhft.chronicle.core.Jvm.pause;
import static net.openhft.chronicle.engine.Chassis.*;
import static org.junit.Assert.assertEquals;

/**
 * JUnit test class to support
 */
public class MarshallableFilePerKeyValueStoreTest {
    public static final String NAME = "marsfileperkvstoretests";
    private static Map<String, TestMarshallable> map;

    @BeforeClass
    public static void createMap() throws IOException {
        resetChassis();
        Function<Bytes, Wire> writeType = WireType.TEXT;
        ((VanillaAsset) assetTree().root()).enableTranslatingValuesToBytesStore();

        LeafViewFactory<AuthenticatedKeyValueStore> factory = (context, asset) -> new FilePerKeyValueStore(context.basePath(OS.TARGET).wireType(writeType), asset);
        assetTree().root().addLeafRule(AuthenticatedKeyValueStore.class, "FilePer Key", factory);

        map = acquireMap(NAME, String.class, TestMarshallable.class);
        KeyValueStore mapU = ((VanillaMapView) map).underlying();
        assertEquals(VanillaStringMarshallableKeyValueStore.class, mapU.getClass());
        assertEquals(FilePerKeyValueStore.class, mapU.underlying().getClass());

        //just in case it hasn't been cleared up last time
        map.clear();
        // allow the events to be picked up.
        pause(50);
    }

    @AfterClass
    public static void cleanUp() {
        map.clear();
    }

    @Ignore("todo fix -  see JIRA https://higherfrequencytrading.atlassian.net/browse/CE-118")
    @Test
    public void test() throws InterruptedException {
        TestMarshallable tm = new TestMarshallable("testing1", "testing2",
                new Nested(Arrays.asList(2.3, 4.5, 6.7, 8.9)));

        List<MapEvent<String, TestMarshallable>> events = new ArrayList<>();
        registerSubscriber(NAME, MapEvent.class, events::add);

        map.put("testA", tm);
        map.put("testB", tm);
        waitFor(events, 2);
        tm.setS1("hello");
        map.put("testB", tm);

        assertEquals(2, map.size());
        assertEquals("testing1", map.get("testA").getS1());
        assertEquals(4.5, map.get("testA").getNested().getListDouble().get(1), 0);

        waitFor(events, 3);
        TimeUnit.MILLISECONDS.sleep(100);
        if (events.size() != 3)
            events.forEach(System.out::println);
        assertEquals(3, events.size());
    }

    private void waitFor(@NotNull List<MapEvent<String, TestMarshallable>> events, int count) throws InterruptedException {
        for (int i = 1; i <= 10; i++) {
            if (events.size() >= count)
                break;
            TimeUnit.MILLISECONDS.sleep(i * i);
        }
    }

    static class TestMarshallable implements Marshallable {
        private String s1, s2;
        private Nested nested;

        public TestMarshallable() {
            nested = new Nested();
        }

        public TestMarshallable(String s1, String s2, Nested nested) {
            this.s1 = s1;
            this.s2 = s2;
            this.nested = nested;
        }

        public Nested getNested() {
            return nested;
        }

        public void setNested(Nested nested) {
            this.nested = nested;
        }

        public String getS1() {
            return s1;
        }

        public void setS1(String s1) {
            this.s1 = s1;
        }

        public String getS2() {
            return s2;
        }

        public void setS2(String s2) {
            this.s2 = s2;
        }

        @Override
        public void readMarshallable(@NotNull WireIn wireIn) throws IllegalStateException {
            setS1(wireIn.read(TestKey.S1).text());
            setS2(wireIn.read(TestKey.S2).text());
            wireIn.read(TestKey.nested).marshallable(nested);
        }

        @Override
        public void writeMarshallable(@NotNull WireOut wireOut) {
            wireOut.write(TestKey.S1).text(getS1());
            wireOut.write(TestKey.S2).text(getS2());
            wireOut.write(TestKey.nested).marshallable(nested);
        }

        @NotNull
        @Override
        public String toString() {
            return "TestMarshallable{" +
                    "s1='" + s1 + '\'' +
                    ", s2='" + s2 + '\'' +
                    ", nested=" + nested +
                    '}';
        }

        private enum TestKey implements WireKey {
            S1, S2, nested
        }
    }

    static class Nested implements Marshallable {
        List<Double> listDouble;

        public Nested() {
            listDouble = new ArrayList<>();
        }

        public Nested(List<Double> listDouble) {
            this.listDouble = listDouble;
        }

        public List<Double> getListDouble() {
            return listDouble;
        }

        public void setListDouble(List<Double> listDouble) {
            this.listDouble = listDouble;
        }

        @Override
        public void readMarshallable(@NotNull WireIn wireIn) throws IllegalStateException {
            listDouble.clear();
            wireIn.read(TestKey.listDouble).sequence(v -> {
                while (v.hasNextSequenceItem()) {
                    v.float64(listDouble::add);
                }
            });
        }

        @Override
        public void writeMarshallable(@NotNull WireOut wireOut) {
            wireOut.write(TestKey.listDouble).sequence(v ->
                            listDouble.stream().forEach(v::float64)
            );
        }

        @NotNull
        @Override
        public String toString() {
            return "Nested{" +
                    "listDouble=" + listDouble +
                    '}';
        }

        private enum TestKey implements WireKey {
            listDouble
        }
    }
}
