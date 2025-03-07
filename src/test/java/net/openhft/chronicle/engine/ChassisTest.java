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

import net.openhft.chronicle.engine.api.map.MapEvent;
import net.openhft.chronicle.engine.api.pubsub.InvalidSubscriberException;
import net.openhft.chronicle.engine.api.pubsub.Subscriber;
import net.openhft.chronicle.engine.api.pubsub.TopicPublisher;
import net.openhft.chronicle.engine.api.pubsub.TopicSubscriber;
import net.openhft.chronicle.engine.api.tree.Asset;
import net.openhft.chronicle.engine.api.tree.AssetNotFoundException;
import net.openhft.chronicle.engine.map.InsertedEvent;
import net.openhft.chronicle.engine.map.RemovedEvent;
import net.openhft.chronicle.engine.map.UpdatedEvent;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static net.openhft.chronicle.engine.Chassis.*;
import static net.openhft.chronicle.engine.api.tree.RequestContext.requestContext;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * Created by peter on 22/05/15.
 */
public class ChassisTest {
    @Before
    public void setUp() {
        resetChassis();
    }

    @Test
    public void simpleGetMapView() {
        ConcurrentMap<String, String> map = acquireMap("map-name", String.class, String.class);

        registerTopicSubscriber("map-name", String.class, String.class, (t, e) -> System.out.println("{ key: " + t + ", event: " + e + " }"));

        map.put("Hello", "World");

        ConcurrentMap<String, String> map2 = acquireMap("map-name", String.class, String.class);
        assertSame(map, map2);

        map2.put("Bye", "soon");

        map2.put("Bye", "now.");
    }

    @Test
    public void subscription() throws InvalidSubscriberException {
        ConcurrentMap<String, String> map = acquireMap("map-name?putReturnsNull=true", String.class, String.class);

        map.put("Key-1", "Value-1");
        map.put("Key-2", "Value-2");

        assertEquals(2, map.size());

        // test the bootstrap finds old keys
        Subscriber<String> subscriber = createMock(Subscriber.class);
        subscriber.onMessage("Key-1");
        subscriber.onMessage("Key-2");
        replay(subscriber);
        registerSubscriber("map-name?bootstrap=true", String.class, subscriber);
        verify(subscriber);
        reset(subscriber);

        assertEquals(2, map.size());

        // test the topic publish triggers events
        subscriber.onMessage("Topic-1");
        replay(subscriber);

        TopicPublisher<String, String> publisher = acquireTopicPublisher("map-name", String.class, String.class);
        publisher.publish("Topic-1", "Message-1");
        verify(subscriber);
        reset(subscriber);
        assertEquals(3, map.size());

        subscriber.onMessage("Hello");
        subscriber.onMessage("Bye");
        subscriber.onMessage("Key-1");
        replay(subscriber);

        // test plain puts trigger events
        map.put("Hello", "World");
        map.put("Bye", "soon");
        map.remove("Key-1");
        verify(subscriber);

        assertEquals(4, map.size());

        // check the contents.
        assertEquals("Topic-1=Message-1\n" +
                        "Key-2=Value-2\n" +
                        "Hello=World\n" +
                        "Bye=soon",
                map.entrySet().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("\n")));

        assertEquals("Topic-1, Key-2, Hello, Bye",
                map.keySet().stream()
                        .collect(Collectors.joining(", ")));

        assertEquals("Message-1, Value-2, World, soon",
                map.values().stream()
                        .collect(Collectors.joining(", ")));
    }

    @Test
    public void keySubscription() throws InvalidSubscriberException {
        ConcurrentMap<String, String> map = acquireMap("map-name?putReturnsNull=true", String.class, String.class);

        map.put("Key-1", "Value-1");
        map.put("Key-2", "Value-2");

        assertEquals(2, map.size());

        // test the bootstrap finds the old value
        Subscriber<String> subscriber = createMock(Subscriber.class);
        subscriber.onMessage("Value-1");
        replay(subscriber);
        registerSubscriber("map-name/Key-1?bootstrap=true", String.class, subscriber);
        assertTrue(getAsset("map-name/Key-1").isSubAsset());
        verify(subscriber);
        reset(subscriber);

        assertEquals(2, map.size());

        // test the topic publish triggers events
        subscriber.onMessage("Message-1");
        replay(subscriber);
/*
todo fix this test
        TopicPublisher<String, String> publisher = acquireTopicPublisher("map-name", String.class, String.class);
        publisher.publish("Key-1", "Message-1");
        publisher.publish("Key-2", "Message-2");
        verify(subscriber);
        reset(subscriber);

        subscriber.onMessage("Bye");
        subscriber.onMessage(null);
        replay(subscriber);

        // test plain puts trigger events
        map.put("Key-1", "Bye");
        map.put("Key-3", "Another");
        map.remove("Key-1");
        verify(subscriber);
*/
    }

    @Test
    public void topicSubscription() throws InvalidSubscriberException {
        ConcurrentMap<String, String> map = acquireMap("map-name?putReturnsNull=true", String.class, String.class);

        map.put("Key-1", "Value-1");
        map.put("Key-2", "Value-2");

        assertEquals(2, map.size());

        // test the bootstrap finds old keys
        TopicSubscriber<String, String> subscriber = createMock(TopicSubscriber.class);
        subscriber.onMessage("Key-1", "Value-1");
        subscriber.onMessage("Key-2", "Value-2");
        replay(subscriber);
        registerTopicSubscriber("map-name?bootstrap=true", String.class, String.class, subscriber);
        verify(subscriber);
        reset(subscriber);

        assertEquals(2, map.size());

        // test the topic publish triggers events
        subscriber.onMessage("Topic-1", "Message-1");
        replay(subscriber);

        TopicPublisher<String, String> publisher = acquireTopicPublisher("map-name", String.class, String.class);
        publisher.publish("Topic-1", "Message-1");
        verify(subscriber);
        reset(subscriber);
        assertEquals(3, map.size());

        subscriber.onMessage("Hello", "World");
        subscriber.onMessage("Bye", "soon");
        subscriber.onMessage("Key-1", null);
        replay(subscriber);

        // test plain puts trigger events
        map.put("Hello", "World");
        map.put("Bye", "soon");
        map.remove("Key-1");
        verify(subscriber);

        assertEquals(4, map.size());

        // check the contents.
        assertEquals("Topic-1=Message-1\n" +
                        "Key-2=Value-2\n" +
                        "Hello=World\n" +
                        "Bye=soon",
                map.entrySet().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("\n")));

        assertEquals("Topic-1, Key-2, Hello, Bye",
                map.keySet().stream()
                        .collect(Collectors.joining(", ")));

        assertEquals("Message-1, Value-2, World, soon",
                map.values().stream()
                        .collect(Collectors.joining(", ")));
    }

    @Test
    public void entrySubscription() throws InvalidSubscriberException {
        ConcurrentMap<String, String> map = acquireMap("map-name?putReturnsNull=true", String.class, String.class);

        map.put("Key-1", "Value-1");
        map.put("Key-2", "Value-2");

        assertEquals(2, map.size());

        // test the bootstrap finds old keys
        Subscriber<MapEvent<String, String>> subscriber = createMock(Subscriber.class);
        subscriber.onMessage(InsertedEvent.of("/map-name", "Key-1", "Value-1"));
        subscriber.onMessage(InsertedEvent.of("/map-name", "Key-2", "Value-2"));
        replay(subscriber);
        registerSubscriber("map-name?bootstrap=true", MapEvent.class, (Subscriber) subscriber);
        verify(subscriber);
        reset(subscriber);

        assertEquals(2, map.size());

        // test the topic publish triggers events
        subscriber.onMessage(UpdatedEvent.of("/map-name", "Key-1", "Value-1", "Message-1"));
        subscriber.onMessage(InsertedEvent.of("/map-name", "Topic-1", "Message-1"));
        replay(subscriber);

        TopicPublisher<String, String> publisher = acquireTopicPublisher("map-name", String.class, String.class);
        publisher.publish("Key-1", "Message-1");
        publisher.publish("Topic-1", "Message-1");
        verify(subscriber);
        reset(subscriber);
        assertEquals(3, map.size());

        subscriber.onMessage(InsertedEvent.of("/map-name", "Hello", "World"));
        subscriber.onMessage(InsertedEvent.of("/map-name", "Bye", "soon"));
        subscriber.onMessage(RemovedEvent.of("/map-name", "Key-1", "Message-1"));
        replay(subscriber);

        // test plain puts trigger events
        map.put("Hello", "World");
        map.put("Bye", "soon");
        map.remove("Key-1");
        verify(subscriber);

        assertEquals(4, map.size());
    }

    @Test
    public void testStringString() throws IOException, InterruptedException {
        final ConcurrentMap<String, String> mapProxy = Chassis.acquireMap("testStringString", String.class, String.class);
        mapProxy.put("hello", "world");
        Assert.assertEquals("world", mapProxy.get("hello"));
        assertEquals(1, mapProxy.size());
    }

    @Test
    public void newNode() {
        Asset group = acquireAsset("group");
        Asset subgroup = acquireAsset("group/sub-group");
        assertEquals("/group/sub-group", subgroup.fullName());

        Asset group2 = acquireAsset("/group2/sub-group");
        assertEquals("/group2/sub-group", group2.fullName());
    }

    @Test()
    public void noAsset() {
        registerTopicSubscriber("topic-name", String.class, String.class, (t, e) -> System.out.println("{ key: " + t + ", event: " + e + " }"));
        TopicPublisher<String, String> publisher = acquireTopicPublisher("topic-name", String.class, String.class);
        publisher.publish("hi", "there");

        // TODO should send a message.
    }

    @Test(expected = AssetNotFoundException.class)
    public void noInterceptor() {
        Asset asset = acquireAsset("");

        asset.acquireView(requestContext("").viewType(MyInterceptor.class));
    }

    @Test
    public void generateInterceptor() {
        Asset asset = acquireAsset("");

        asset.addLeafRule(MyInterceptor.class, "test", (context, asset2) -> {
            assertEquals(MyInterceptor.class, context.viewType());
            return new MyInterceptor();
        });
        MyInterceptor mi = asset.acquireView(requestContext("").viewType(MyInterceptor.class));
        MyInterceptor mi2 = asset.acquireView(requestContext("").viewType(MyInterceptor.class));
        assertNotNull(mi);
        assertSame(mi, mi2);
    }

    static class MyInterceptor {
    }
}
