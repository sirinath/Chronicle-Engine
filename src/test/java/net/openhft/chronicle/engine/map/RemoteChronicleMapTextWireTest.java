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

package net.openhft.chronicle.engine.map;

import net.openhft.chronicle.engine.api.tree.AssetTree;
import net.openhft.chronicle.engine.map.MapClientTest.RemoteMapSupplier;
import net.openhft.chronicle.engine.tree.VanillaAssetTree;
import net.openhft.chronicle.network.TCPRegistry;
import net.openhft.chronicle.wire.WireType;
import net.openhft.chronicle.wire.YamlLogging;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.rules.TestName;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import static net.openhft.chronicle.engine.Utils.methodName;
import static net.openhft.chronicle.engine.Utils.yamlLoggger;
import static net.openhft.chronicle.wire.YamlLogging.writeMessage;
import static org.junit.Assert.*;


public class RemoteChronicleMapTextWireTest extends JSR166TestCase {

    private static int s_port = 11050;
    @NotNull
    @Rule
    public TestName name = new TestName();
    @NotNull
    private final AssetTree assetTree = new VanillaAssetTree().forTesting();

    @Before
    public void before() {
        System.out.println("\t... test " + name.getMethodName());
        methodName(name.getMethodName());
        YamlLogging.clientReads = true;
        YamlLogging.clientWrites = true;

    }

    @AfterClass
    public static void testTearDown() {
        TCPRegistry.assertAllServersStopped();
    }

    @NotNull
    private ClosableMapSupplier newIntString(@NotNull String name) throws IOException {
        final RemoteMapSupplier remoteMapSupplier = new RemoteMapSupplier<>(
                "RemoteChronicleMapTextWireTest.host.port",
                Integer.class, String.class, WireType.TEXT, assetTree, name);

        return new ClosableMapSupplier() {
            @NotNull
            @Override
            public Object get() {
                return remoteMapSupplier.get();
            }

            @Override
            public void close() throws IOException {
                remoteMapSupplier.close();
                assetTree.close();
            }
        };
    }

    @NotNull
    private ClosableMapSupplier<CharSequence, CharSequence> newStrStrMap() throws
            IOException {

        final RemoteMapSupplier remoteMapSupplier = new RemoteMapSupplier<>(
                "RemoteChronicleMapTextWireTest.host.port",
                CharSequence.class, CharSequence.class, WireType.TEXT, assetTree, "test");

        return new ClosableMapSupplier() {
            @NotNull
            @Override
            public Object get() {
                return remoteMapSupplier.get();
            }

            @Override
            public void close() throws IOException {
                remoteMapSupplier.close();
                assetTree.close();
            }
        };
    }

    /**
     * Returns a new map from Integers 1-5 to Strings "A"-"E".
     */
    @NotNull
    private ClosableMapSupplier<Integer, String> map5() throws IOException {
        ClosableMapSupplier<Integer, String> supplier = newIntString("test");
        final Map<Integer, String> map = supplier.get();
        assertTrue(map.isEmpty());
        map.put(1, "A");
        map.put(2, "B");
        map.put(3, "C");
        map.put(4, "D");
        map.put(5, "E");
        assertFalse(map.isEmpty());
        assertEquals(5, map.size());
        return supplier;
    }

    /**
     * clear removes all pairs
     */
    @Test(timeout = 50000)
    public void testClear() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();

            yamlLoggger(map::clear);
            assertEquals(0, map.size());
        }
    }

    /**
     * contains returns true for contained value
     */
    @Test(timeout = 50000)
    public void testContains() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();

            writeMessage = "when the key exists";
            yamlLoggger(() -> assertTrue(map.containsValue("A")));
            writeMessage = "when it doesnt exist";
            yamlLoggger(() -> assertFalse(map.containsValue("Z")));
        }
    }

    /**
     * containsKey returns true for contained key
     */

    @Test(timeout = 50000)
    public void testContainsKey() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            writeMessage = "example of containsKey(<key>) returning true";
            yamlLoggger(() -> assertTrue(map.containsKey(one)));
            assertFalse(map.containsKey(zero));
        }
    }

    /**
     * containsValue returns true for held values
     */
    @Test(timeout = 50000)
    public void testContainsValue() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            writeMessage = "example of containsValue(<value>) returning true";
            yamlLoggger(() -> assertTrue(map.containsValue("A")));
            assertFalse(map.containsValue("Z"));
        }
    }

    /**
     * get returns the correct element at the given key, or null if not present
     */
    @Test
    public void testGetObjectNotPresent() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            assertEquals("A", map.get(one));
            try (ClosableMapSupplier empty = newStrStrMap()) {
                writeMessage = "example of get(<key>) returning null, when the keys is not " +
                        "present in the map";

                yamlLoggger(() -> {
                    Object object = map.get(notPresent);
                    assertNull(object);
                });
            }
        }
    }

    /**
     * isEmpty is true of empty map and false for non-empty
     */
    @Test(timeout = 50000)
    public void testIsEmpty() throws IOException {
        try (ClosableMapSupplier<Integer, String> emptySupplier = newIntString("testEmpty")) {
            final Map empty = emptySupplier.get();
            try (ClosableMapSupplier<Integer, String> supplier = map5()) {

                final Map map = supplier.get();
                if (!empty.isEmpty()) {
                    System.out.print("not empty " + empty);
                }

                writeMessage = "example of isEmpty() returning true, not it uses the size() method";
                yamlLoggger(() -> assertTrue(empty.isEmpty()));
                assertFalse(map.isEmpty());
            }
        }
    }

    /**
     * keySet returns a Set containing all the keys
     */

    @Test(timeout = 50000)
    public void testKeySet() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            writeMessage = "example of checking the size of a keyset";
            yamlLoggger(() -> {
                        Set s = map.keySet();
                        assertEquals(5, s.size());
                    }
            );
            Set s = map.keySet();
            assertTrue(s.contains(one));
            assertTrue(s.contains(two));
            assertTrue(s.contains(three));
            assertTrue(s.contains(four));
            assertTrue(s.contains(five));
        }
    }

    /**
     * keySet.toArray returns contains all keys
     */
    @Test(timeout = 50000)
    public void testKeySetToArray() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            Set s = map.keySet();
            Object[] ar = s.toArray();
            assertTrue(s.containsAll(Arrays.asList(ar)));
            assertEquals(5, ar.length);
            ar[0] = m10;
            assertFalse(s.containsAll(Arrays.asList(ar)));
        }
    }

    /**
     * Values.toArray contains all values
     */

    @Test(timeout = 50000)
    public void testValuesToArray() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            Collection v = map.values();
            Object[] ar = v.toArray();
            ArrayList s = new ArrayList(Arrays.asList(ar));
            assertEquals(5, ar.length);
            assertTrue(s.contains("A"));
            assertTrue(s.contains("B"));
            assertTrue(s.contains("C"));
            assertTrue(s.contains("D"));
            assertTrue(s.contains("E"));
        }
    }

    /**
     * entrySet.toArray contains all entries
     */
    @Test(timeout = 50000)
    public void testEntrySetToArray() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            writeMessage = "map.entrySet().toArray() first gets the entry set and then converts " +
                    "it to an array";
            yamlLoggger(() -> {
                Set s = map.entrySet();
                s.toArray();
            });

            Set s = map.entrySet();
            Object[] ar = s.toArray();
            assertEquals(5, ar.length);
            for (int i = 0; i < 5; ++i) {
                assertTrue(map.containsKey(((Entry) (ar[i])).getKey()));
                assertTrue(map.containsValue(((Entry) (ar[i])).getValue()));
            }
        }
    }

    /**
     * values collection contains all values
     */
    @Test(timeout = 50000)
    public void testValues() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            writeMessage = "example of getting the values and then calling size()";
            yamlLoggger(() -> {
                Collection s = map.values();
                s.size();
            });

            Collection s = map.values();
            assertEquals(5, s.size());
            assertTrue(s.contains("A"));
            assertTrue(s.contains("B"));
            assertTrue(s.contains("C"));
            assertTrue(s.contains("D"));
            assertTrue(s.contains("E"));
        }
    }

    /**
     * entrySet contains all pairs
     */
    @Test
    public void testEntrySet() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map<Integer, String> map = supplier.get();
            writeMessage = "example of getting and entry set itterator";
            yamlLoggger(() -> {
                Set<Entry<Integer, String>> entrySet = map.entrySet();
                entrySet.iterator();
            });

            Set<Entry<Integer, String>> s = map.entrySet();
            assertEquals(5, s.size());
            for (Entry e : s) {
                assertTrue(
                        (e.getKey().equals(one) && e.getValue().equals("A")) ||
                                (e.getKey().equals(two) && e.getValue().equals("B")) ||
                                (e.getKey().equals(three) && e.getValue().equals("C")) ||
                                (e.getKey().equals(four) && e.getValue().equals("D")) ||
                                (e.getKey().equals(five) && e.getValue().equals("E"))
                );
            }
        }
        System.out.println("finished");
    }

    /**
     * putAll adds all key-value pairs from the given map
     */
    @Test(timeout = 50000)
    public void testPutAll() throws IOException {
        int port = s_port++;
        try (ClosableMapSupplier<Integer, String> emptySupplier = newIntString("test")) {
            final Map<Integer, String> empty = emptySupplier.get();
            try (ClosableMapSupplier<Integer, String> supplier = map5()) {
                final Map map = supplier.get();
                yamlLoggger(() -> empty.putAll(map));
                assertEquals(5, empty.size());
                assertTrue(empty.containsKey(one));
                assertTrue(empty.containsKey(two));
                assertTrue(empty.containsKey(three));
                assertTrue(empty.containsKey(four));
                assertTrue(empty.containsKey(five));
            }
        }
    }

    /**
     * putIfAbsent works when the given key is not present
     */
    @Test(timeout = 50000)
    public void testPutIfAbsent() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            yamlLoggger(() -> map.putIfAbsent(six, "Z"));
            assertTrue(map.containsKey(six));
        }
    }

    /**
     * putIfAbsent does not add the pair if the key is already present
     */
    @Test(timeout = 50000)
    public void testPutIfAbsent2() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            yamlLoggger(() -> assertEquals("A", map.putIfAbsent(one, "Z")));
        }
    }

    /**
     * replace fails when the given key is not present
     */
    @Test(timeout = 50000)
    public void testReplace() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            writeMessage = "example of replace where the value is not known";
            yamlLoggger(() -> assertNull(map.replace(six, "Z")));
            assertFalse(map.containsKey(six));
        }
    }

    /**
     * replace succeeds if the key is already present
     */
    @Test(timeout = 50000)
    public void testReplace2() throws
            IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            writeMessage = "example of replace where the value is known";
            yamlLoggger(() -> assertNotNull(map.replace(one, "Z")));
            assertEquals("Z", map.get(one));
        }
    }

    /**
     * replace value fails when the given key not mapped to expected value
     */
    @Test(timeout = 50000)
    public void testReplaceValue() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            assertEquals("A", map.get(one));
            writeMessage = "example of when then value was not replaced";
            yamlLoggger(() -> assertFalse(map.replace(one, "Z", "Z")));
            assertEquals("A", map.get(one));
        }
    }

    /**
     * replace value succeeds when the given key mapped to expected value
     */

    @Test(timeout = 50000)
    public void testReplaceValue2() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            assertEquals("A", map.get(one));
            writeMessage = "example of replace where the value is known";
            yamlLoggger(() -> assertTrue(map.replace(one, "A", "Z")));
            assertEquals("Z", map.get(one));
        }
    }

    /**
     * remove removes the correct key-value pair from the map
     */
    @Test(timeout = 50000)
    public void testRemove() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            yamlLoggger(() -> map.remove(five));
            assertEquals(4, map.size());
            assertFalse(map.containsKey(five));
        }
    }

    /**
     * remove(key,value) removes only if pair present
     */
    @Test(timeout = 50000)
    public void testRemove2
    () throws IOException {
   /*     try(   ClosableMapSupplier map = map5(8076)) {
        map.remove(five, "E");
    assertEquals(4, map.size());
        assertFalse(map.containsKey(five));
        map.remove(four, "A");
        assertEquals(4, map.size());
        assertTrue(map.containsKey(four));
   */
    }

    /**
     * size returns the correct values
     */
    @Test(timeout = 50000)
    public void testSize() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            try (ClosableMapSupplier<Integer, String> supplier0 = newIntString("testEmpty")) {
                final Map empty = supplier0.get();
                writeMessage = "size on an empty map";
                yamlLoggger(() -> assertEquals(0, empty.size()));
                writeMessage = "size on a map with entries";
                yamlLoggger(() -> assertEquals(5, map.size()));
            }
        }
    }

    /**
     * size returns the correct values
     */
    @Test(timeout = 150000)
    public void testSize2() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            try (ClosableMapSupplier<Integer, String> supplier0 = newIntString("testEmpty")) {
                final Map empty = supplier0.get();
                assertEquals(0, empty.size());
                assertEquals(5, map.size());
            }
        }
    }

    /**
     * size returns the correct values
     */
    @Test(timeout = 50000)
    public void testSize3() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = map5()) {
            final Map map = supplier.get();
            try (ClosableMapSupplier<Integer, String> supplier0 = newIntString("testEmpty")) {
                final Map empty = supplier0.get();
                assertEquals(0, empty.size());
                assertEquals(5, map.size());
            }
        }
    }

    /**
     * get(null) throws NPE
     */
    @Test(timeout = 50000, expected = NullPointerException.class)
    public void testGet_NullPointerException() throws IOException {

        try (ClosableMapSupplier<Integer, String> supplier = newIntString("test")) {
            Map<Integer, String> c = supplier.get();
            writeMessage = "get(null) returns a NullPointerException";
            yamlLoggger(() -> c.get(null));
        }
    }

    /**
     * containsKey(null) throws NPE
     */
    @Test(timeout = 50000, expected = NullPointerException.class)
    public void testContainsKey_NullPointerException() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = newIntString("test")) {
            Map<Integer, String> c = supplier.get();
            writeMessage = "c.containsKey(null) will throw a NullPointerException";
            yamlLoggger(() -> c.containsKey(null));
        }
    }

    /**
     * put(null,x) throws NPE
     */
    @Test(timeout = 50000, expected = NullPointerException.class)
    public void testPut1_NullPointerException() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = newIntString("test")) {
            Map<Integer, String> c = supplier.get();
            writeMessage = "put(null) will throw a NullPointerException";
            yamlLoggger(() -> c.put(null, "whatever"));
        }
    }

    /**
     * put(x, null) throws NPE
     */
    @Test(timeout = 50000, expected = NullPointerException.class)
    public void testPut2_NullPointerException() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = newIntString("test")) {
            Map<Integer, String> c = supplier.get();
            writeMessage = "put(notPresent,null) will throw a NullPointerException";
            yamlLoggger(() -> c.put(notPresent, null));
        }
    }

    /**
     * putIfAbsent(null, x) throws NPE
     */
    @Test(timeout = 50000, expected = NullPointerException.class)
    public void testPutIfAbsent1_NullPointerException() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = newIntString("test")) {
            Map<Integer, String> c = supplier.get();
            writeMessage = "put(null, \"whatever\") will throw a NullPointerException";
            yamlLoggger(() -> c.putIfAbsent(null, "whatever"));
        }
    }

    /**
     * replace(null, x) throws NPE
     */
    @Test(timeout = 50000, expected = NullPointerException.class)
    public void testReplace_NullPointerException() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = newIntString("test")) {
            Map<Integer, String> c = supplier.get();
            c.replace(null, "whatever");
        }
    }

    /**
     * replace(null, x, y) throws NPE
     */
    @Test(timeout = 50000, expected = NullPointerException.class)
    public void testReplaceValue_NullPointerException() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = newIntString("test")) {
            Map<Integer, String> c = supplier.get();
            c.replace(null, "A", "whatever");
        }
    }

    /**
     * putIfAbsent(x, null) throws NPE
     */
    @Test(timeout = 50000, expected = NullPointerException.class)
    public void testPutIfAbsent2_NullPointerException() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = newIntString("test")) {
            Map<Integer, String> c = supplier.get();
            c.putIfAbsent(notPresent, null);
        }
    }

    /**
     * replace(x, null) throws NPE
     */
    @Test(timeout = 50000, expected = NullPointerException.class)
    public void testReplace2_NullPointerException() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = newIntString("test")) {
            Map<Integer, String> c = supplier.get();
            writeMessage = "replace(notPresent,null) will throw a NullPointerException";
            yamlLoggger(() -> c.replace(notPresent, null));
        }
    }

    /**
     * replace(x, null, y) throws NPE
     */
    @Test(timeout = 50000, expected = NullPointerException.class)
    public void testReplaceValue2_NullPointerException() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = newIntString("test")) {
            Map<Integer, String> c = supplier.get();
            c.replace(notPresent, null, "A");
        }
    }

    /**
     * replace(x, y, null) throws NPE
     */
    @Test(timeout = 50000, expected = NullPointerException.class)
    public void testReplaceValue3_NullPointerException() throws IOException {
        try (ClosableMapSupplier<Integer, String> supplier = newIntString("test")) {
            Map<Integer, String> c = supplier.get();
            writeMessage = "replace(notPresent, \"A\", null will throw a NullPointerException";
            yamlLoggger(() -> c.replace(notPresent, "A", null));
        }
    }

    /**
     * remove(null) throws NPE
     */
    @Test(timeout = 50000, expected = NullPointerException.class)
    public void testRemove1_NullPointerException() throws IOException {
        try (ClosableMapSupplier<CharSequence, CharSequence> supplier = newStrStrMap()) {
            Map<CharSequence, CharSequence> c = supplier.get();
            c.put("sadsdf", "asdads");

            writeMessage = "remove(null) will throw a NullPointerException";
            yamlLoggger(() -> c.remove(null));
        }
    }

    /**
     * remove(null, x) throws NPE
     */
    @Test(timeout = 50000, expected = NullPointerException.class)
    public void testRemove2_NullPointerException
    () throws IOException {
        try (ClosableMapSupplier<CharSequence, CharSequence> supplier = newStrStrMap()) {
            Map<CharSequence, CharSequence> c = supplier.get();
            c.put("sadsdf", "asdads");
            writeMessage = "remove(null,whatever) will throw a NullPointerException";
            yamlLoggger(() -> c.remove(null, "whatever"));
        }
    }

    /**
     * remove(x, null) returns false
     */
    @Test(timeout = 50000, expected = NullPointerException.class)
    public void testRemove3() throws IOException {
        try (ClosableMapSupplier<CharSequence, CharSequence> supplier = newStrStrMap()) {
            Map<CharSequence, CharSequence> c = supplier.get();
            c.put("sadsdf", "asdads");
            assertFalse(c.remove("sadsdf", null));
        }
    }

}

