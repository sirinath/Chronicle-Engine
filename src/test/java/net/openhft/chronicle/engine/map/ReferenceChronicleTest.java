package net.openhft.chronicle.engine.map;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.OS;
import net.openhft.chronicle.engine.api.map.KeyValueStore;
import net.openhft.chronicle.engine.api.map.MapView;
import net.openhft.chronicle.engine.api.pubsub.InvalidSubscriberException;
import net.openhft.chronicle.engine.api.pubsub.Subscriber;
import net.openhft.chronicle.engine.api.pubsub.Subscription;
import net.openhft.chronicle.engine.api.pubsub.TopicSubscriber;
import net.openhft.chronicle.engine.api.tree.Asset;
import net.openhft.chronicle.engine.api.tree.AssetTree;
import net.openhft.chronicle.engine.server.ServerEndpoint;
import net.openhft.chronicle.engine.tree.VanillaAssetTree;
import net.openhft.chronicle.network.TCPRegistry;
import net.openhft.chronicle.network.connection.TcpChannelHub;
import net.openhft.chronicle.wire.WireType;
import net.openhft.chronicle.wire.YamlLogging;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by daniel on 16/07/2015. Tests the combination of Reference and Chronicle
 */
public class ReferenceChronicleTest {


    private String hostPortToken;

    @Before
    public void before() throws IOException {
        hostPortToken = this.getClass().getSimpleName() + ".host.port";
        TCPRegistry.createServerSocketChannelFor(hostPortToken);
    }

    @After
    public void after() {
        TcpChannelHub.closeAllHubs();
        TCPRegistry.reset();
    }

    @Ignore("test keeps failing on TC")
    @Test(timeout = 5000)
    public void testRemoteSubscriptionMUFGChronicle() throws IOException {

        AssetTree serverAssetTree = new VanillaAssetTree().forTesting();
        serverAssetTree.root().addWrappingRule(MapView.class, "map directly to KeyValueStore", VanillaMapView::new, KeyValueStore.class);
        serverAssetTree.root().addLeafRule(KeyValueStore.class, "use Chronicle Map", (context, asset) ->
                new ChronicleMapKeyValueStore(context.basePath(OS.TARGET).entries(50).averageValueSize(2_000_000), asset));

        ServerEndpoint serverEndpoint = new ServerEndpoint(hostPortToken, serverAssetTree, WireType.BINARY);
        AssetTree clientAssetTree = new VanillaAssetTree().forRemoteAccess(hostPortToken, WireType.BINARY);

        //noinspection TryFinallyCanBeTryWithResources
        try {
            test(clientAssetTree);
        } finally {
            clientAssetTree.close();
            serverEndpoint.close();
            serverAssetTree.close();
        }

    }

    @Test(timeout = 5000)
    public void testLocalSubscriptionMUFGChronicle() throws IOException {

        AssetTree serverAssetTree = new VanillaAssetTree().forTesting();
        serverAssetTree.root().addWrappingRule(MapView.class, "map directly to KeyValueStore", VanillaMapView::new, KeyValueStore.class);
        serverAssetTree.root().addLeafRule(KeyValueStore.class, "use Chronicle Map", (context, asset) ->
                new ChronicleMapKeyValueStore(context.basePath(OS.TARGET).entries(50).averageValueSize(2_000_000), asset));
        test(serverAssetTree);

    }

    public void test(@NotNull AssetTree assetTree) {
        String key = "subject";
        String _mapName = "group";
        Map map = assetTree.acquireMap(_mapName, String.class, String.class);
        //TODO does not work without an initial put
        map.put(key, "init");

        List<String> events = new ArrayList<>();
        Subscriber<String> keyEventSubscriber = new Subscriber<String>() {
            @Override
            public void onMessage(String s) throws InvalidSubscriberException {
                events.add(s);
            }

            @Override
            public void onEndOfSubscription() {
                events.add("END");
            }
        };

        TopicSubscriber<String, String> topicSubscriber = (t, m) -> {
            events.add(m);
        };

        assetTree.registerSubscriber(_mapName + "/" + key + "?bootstrap=false&putReturnsNull=true", String.class, keyEventSubscriber);
        //serverAssetTree.registerTopicSubscriber(_mapName + "?bootstrap=false&putReturnsNull=true", String.class, String.class, topicSubscriber);

        Jvm.pause(100);
        Asset child = assetTree.getAsset(_mapName).getChild(key);
        //Asset child = serverAssetTree.getAsset(_mapName);

        assertNotNull(child);
        Subscription subscription = child.subscription(false);
        assertEquals(1, subscription.subscriberCount());

        YamlLogging.showServerWrites = true;

        AtomicInteger count = new AtomicInteger();
        map.put(key, "" + count.incrementAndGet());
        map.put(key, "" + count.incrementAndGet());
        map.put(key, "" + count.incrementAndGet());

        while (events.size() != 3) {
            Jvm.pause(1);
        }

        assetTree.unregisterSubscriber(_mapName + "/" + key, keyEventSubscriber);
        //serverAssetTree.unregisterTopicSubscriber(_mapName, topicSubscriber);
        while (events.size() != 4) {
            Jvm.pause(1);
        }

        //assertEquals(0, subscription.subscriberCount());
        assertEquals(4, events.size());
    }
}
