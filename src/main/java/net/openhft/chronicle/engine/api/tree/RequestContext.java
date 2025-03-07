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

package net.openhft.chronicle.engine.api.tree;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.BytesStore;
import net.openhft.chronicle.engine.api.collection.ValuesCollection;
import net.openhft.chronicle.engine.api.map.MapEvent;
import net.openhft.chronicle.engine.api.map.MapView;
import net.openhft.chronicle.engine.api.pubsub.*;
import net.openhft.chronicle.engine.api.session.Heartbeat;
import net.openhft.chronicle.engine.api.set.EntrySetView;
import net.openhft.chronicle.engine.api.set.KeySetView;
import net.openhft.chronicle.engine.map.ObjectKVSSubscription;
import net.openhft.chronicle.engine.map.RawKVSSubscription;
import net.openhft.chronicle.engine.query.Filter;
import net.openhft.chronicle.engine.tree.TopologicalEvent;
import net.openhft.chronicle.engine.tree.TopologySubscription;
import net.openhft.chronicle.wire.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

import static net.openhft.chronicle.core.pool.ClassAliasPool.CLASS_ALIASES;

/**
 * Created by peter on 24/05/15.
 */
public class RequestContext implements Cloneable {
    static {
        addAlias(MapView.class, "Map");
        addAlias(MapEvent.class, "MapEvent");
        addAlias(TopologicalEvent.class, "TopologicalEvent");
        addAlias(EntrySetView.class, "EntrySet");
        addAlias(KeySetView.class, "KeySet");
        addAlias(ValuesCollection.class, "Values");
        addAlias(Replication.class, "Replication");
        addAlias(Publisher.class, "Publisher, Pub");
        addAlias(TopicPublisher.class, "TopicPublisher, TopicPub");
        addAlias(ObjectKVSSubscription.class, "Subscription");
        addAlias(TopologySubscription.class, "topologySubscription");
        addAlias(Reference.class, "Reference, Ref");
        addAlias(Heartbeat.class, "Heartbeat");
        addAlias(Filter.class, "Filter");
        addAlias(net.openhft.chronicle.engine.query.Operation.class, "Operation");
    }

    private String pathName;
    private String name;
    private Class viewType, type, type2;
    private String basePath;
    private Function<Bytes, Wire> wireType = WireType.TEXT;
    @Nullable
    private Boolean putReturnsNull = null,
            removeReturnsNull = null,
            nullOldValueOnUpdateEvent = null,
            endSubscriptionAfterBootstrap = null,
            bootstrap = null;
    private double averageValueSize;
    private long entries;
    private Boolean recurse;
    private boolean sealed = false;
    private String cluster = "cluster";

    private RequestContext() {
    }

    public RequestContext(String pathName, String name) {
        this.pathName = pathName;
        this.name = name;
    }

    private static void addAlias(Class type, @NotNull String aliases) {
        CLASS_ALIASES.addAlias(type, aliases);
    }

    @NotNull
    public static RequestContext requestContext() {
        return new RequestContext();
    }

    // todo improve this !
    @NotNull
    public static RequestContext requestContext(@NotNull CharSequence uri) {
        return requestContext(uri.toString());
    }

    @NotNull
    public static RequestContext requestContext(@NotNull String uri) {

        int queryPos = uri.indexOf('?');
        String fullName = queryPos >= 0 ? uri.substring(0, queryPos) : uri;
        String query = queryPos >= 0 ? uri.substring(queryPos + 1) : "";
        int lastForwardSlash = fullName.lastIndexOf('/');
        if (lastForwardSlash > 0 && fullName.length() == lastForwardSlash + 1) {
            fullName = fullName.substring(0, fullName.length() - 1);
            lastForwardSlash = fullName.lastIndexOf('/');
        }
        String pathName = lastForwardSlash >= 0 ? fullName.substring(0, lastForwardSlash) : "";
        String name = lastForwardSlash >= 0 ? fullName.substring(lastForwardSlash + 1) : fullName;
        return new RequestContext(pathName, name).queryString(query);
    }

    static Class lookupType(@NotNull CharSequence typeName) throws IllegalArgumentException {
        return CLASS_ALIASES.forName(typeName);
    }

    @NotNull
    public RequestContext cluster(String clusterTwo) {
        this.cluster = clusterTwo;
        return this;
    }

    @NotNull
    public String cluster() {
        return this.cluster;
    }

    @NotNull
    public RequestContext seal() {
        sealed = true;
        return this;
    }

    @NotNull
    public Class<Subscription> getSubscriptionType() {
        Class elementType = elementType();
        return elementType == TopologicalEvent.class
                ? (Class) TopologySubscription.class
                : elementType == BytesStore.class
                ? (Class) RawKVSSubscription.class
                : (Class) ObjectKVSSubscription.class;
    }

    @NotNull
    public RequestContext queryString(@NotNull String queryString) {
        if (queryString.isEmpty())
            return this;
        WireParser parser = getWireParser();
        Bytes bytes = Bytes.from(queryString);
        QueryWire wire = new QueryWire(bytes);
        while (bytes.readRemaining() > 0)
            parser.parse(wire);
        return this;
    }

    @NotNull
    public WireParser getWireParser() {
        WireParser parser = new VanillaWireParser();
        parser.register(() -> "cluster", v -> v.text((Consumer<String>) x -> this.cluster = x));
        parser.register(() -> "view", v -> v.text((Consumer<String>) this::view));
        parser.register(() -> "bootstrap", v -> v.bool(b -> this.bootstrap = b));
        parser.register(() -> "putReturnsNull", v -> v.bool(b -> this.putReturnsNull = b));
        parser.register(() -> "removeReturnsNull", v -> v.bool(b -> this.removeReturnsNull = b));
        parser.register(() -> "nullOldValueOnUpdateEvent",
                v -> v.bool(b -> this.nullOldValueOnUpdateEvent = b));
        parser.register(() -> "basePath", v -> v.text((Consumer<String>) x -> this.basePath = x));
        parser.register(() -> "viewType", v -> v.typeLiteral(x -> this.viewType = x));
        parser.register(() -> "topicType", v -> v.typeLiteral(x -> this.type = x));
        parser.register(() -> "keyType", v -> v.typeLiteral(x -> this.type = x));
        parser.register(() -> "valueType", v -> v.typeLiteral(x -> this.type2 = x));
        parser.register(() -> "messageType", v -> v.typeLiteral(x -> this.type2 = x));
        parser.register(() -> "elementType", v -> v.typeLiteral(x -> this.type = x));
        parser.register(() -> "endSubscriptionAfterBootstrap", v -> v.bool(b -> this.endSubscriptionAfterBootstrap = b));
        parser.register(WireParser.DEFAULT, ValueIn.DISCARD);
        return parser;
    }

    @NotNull
    RequestContext view(@NotNull String viewName) {
        try {
            Class clazz = lookupType(viewName);
            viewType(clazz);
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("Unknown view name:" + viewName);
        }
        return this;
    }

    @NotNull
    public RequestContext type(Class type) {
        checkSealed();
        this.type = type;
        return this;
    }

    @NotNull
    public RequestContext keyType(Class type) {
        checkSealed();
        this.type = type;
        return this;
    }

    public Class type() {

        if (type == null)
            return String.class;
        return type;
    }

    public Class elementType() {
        return type2 == null ? type : type2;
    }

    public Class keyType() {
        if (type == null)
            return String.class;
        return type;
    }

    public Class valueType() {
        if (type2 == null)
            return String.class;
        return type2;
    }

    public Class topicType() {

        if (type == null)
            return String.class;
        return type;
    }

    public Class messageType() {

        if (type2 == null)
            return String.class;
        return type2;
    }

    @NotNull
    public RequestContext valueType(Class type2) {
        checkSealed();
        this.type2 = type2;
        return this;
    }

    @NotNull
    public RequestContext type2(Class type2) {
        checkSealed();
        this.type2 = type2;
        return this;
    }

    public Class type2() {
        if (type == null)
            return String.class;
        return type2;
    }

    @NotNull
    public String fullName() {
        return pathName.isEmpty() ? name : (pathName + "/" + name);
    }

    @NotNull
    public RequestContext basePath(String basePath) {
        checkSealed();
        this.basePath = basePath;
        return this;
    }

    public String basePath() {
        return basePath;
    }

    @NotNull
    public RequestContext wireType(Function<Bytes, Wire> writeType) {
        checkSealed();
        this.wireType = writeType;
        return this;
    }

    public Function<Bytes, Wire> wireType() {
        return wireType;
    }

    public String namePath() {
        return pathName;
    }

    public String name() {
        return name;
    }

    public double getAverageValueSize() {
        return averageValueSize;
    }

    @NotNull
    public RequestContext averageValueSize(double averageValueSize) {
        checkSealed();
        this.averageValueSize = averageValueSize;
        return this;
    }

    public long getEntries() {
        return entries;
    }

    @NotNull
    public RequestContext entries(long entries) {
        checkSealed();
        this.entries = entries;
        return this;
    }

    @NotNull
    public RequestContext name(String name) {
        this.name = name;
        return this;
    }

    @NotNull
    public RequestContext viewType(Class assetType) {
        checkSealed();
        this.viewType = assetType;
        return this;
    }

    @Nullable
    public Class viewType() {
        return viewType;
    }

    @NotNull
    public RequestContext fullName(@NotNull String fullName) {
        int dirPos = fullName.lastIndexOf('/');
        this.pathName = dirPos >= 0 ? fullName.substring(0, dirPos) : "";
        this.name = dirPos >= 0 ? fullName.substring(dirPos + 1) : fullName;
        return this;
    }

    @Nullable
    public Boolean putReturnsNull() {
        return putReturnsNull;
    }

    @Nullable
    public Boolean removeReturnsNull() {
        return removeReturnsNull;
    }

    @Nullable
    public Boolean nullOldValueOnUpdateEvent() {
        return nullOldValueOnUpdateEvent;
    }

    @Nullable
    public Boolean bootstrap() {
        return bootstrap;
    }

    @NotNull
    public RequestContext bootstrap(boolean bootstrap) {
        checkSealed();
        this.bootstrap = bootstrap;
        return this;
    }


    @NotNull
    public RequestContext endSubscriptionAfterBootstrap(boolean endSubscriptionAfterBootstrap) {
        checkSealed();
        this.endSubscriptionAfterBootstrap = endSubscriptionAfterBootstrap;
        return this;
    }


    public Boolean endSubscriptionAfterBootstrap() {
        return endSubscriptionAfterBootstrap;
    }


    void checkSealed() {
        if (sealed) throw new IllegalStateException();
    }

    @NotNull
    @Override
    public String toString() {
        return "RequestContext{" +
                "pathName='" + pathName + '\'' +
                ", name='" + name + '\'' +
                ", viewType=" + viewType +
                ", type=" + type +
                ", type2=" + type2 +
                ", basePath='" + basePath + '\'' +
                ", wireType=" + wireType +
                ", putReturnsNull=" + putReturnsNull +
                ", removeReturnsNull=" + removeReturnsNull +
                ", bootstrap=" + bootstrap +
                ", averageValueSize=" + averageValueSize +
                ", entries=" + entries +
                ", recurse=" + recurse +
                ", endSubscriptionAfterBootstrap=" + endSubscriptionAfterBootstrap +
                '}';
    }

    public Boolean recurse() {
        return recurse;
    }

    @NotNull
    public RequestContext recurse(Boolean recurse) {
        this.recurse = recurse;
        return this;
    }

    @NotNull
    public RequestContext clone() {
        try {
            RequestContext clone = (RequestContext) super.clone();
            clone.sealed = false;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @NotNull
    public RequestContext putReturnsNull(Boolean putReturnsNull) {
        this.putReturnsNull = putReturnsNull;
        return this;
    }

    public String toUri() {
        StringBuilder sb = new StringBuilder();
        if (pathName != null && !pathName.isEmpty()) {
            sb.append("/").append(pathName);
        }
        sb.append("/").append(name);
        String sep = "?";
        if (viewType != null) {
            sb.append(sep).append("view=").append(CLASS_ALIASES.nameFor(viewType()));
            sep = "&";
        }
        if (keyType() != null && keyType() != String.class) {
            sb.append(sep).append("keyType=").append(CLASS_ALIASES.nameFor(keyType()));
            sep = "&";
        }
        if (valueType() != null && valueType() != String.class) {
            sb.append(sep).append("valueType=").append(CLASS_ALIASES.nameFor(valueType()));
            sep = "&";
        }
        if (putReturnsNull() != null) {
            sb.append(sep).append("putReturnsNull=").append(putReturnsNull);
            sep = "&";
        }
        if (removeReturnsNull() != null) {
            sb.append(sep).append("removeReturnsNull=").append(putReturnsNull);
            sep = "&";
        }
        if (bootstrap() != null) {
            sb.append(sep).append("bootstrap=").append(bootstrap);
            sep = "&";
        }
        return sb.toString();
    }

    public enum Operation {
        END_SUBSCRIPTION_AFTER_BOOTSTRAP, BOOTSTRAP;

        public void apply(RequestContext rc) {
            switch (this) {
                case END_SUBSCRIPTION_AFTER_BOOTSTRAP:
                    rc.endSubscriptionAfterBootstrap(true);
                    break;
                case BOOTSTRAP:
                    rc.bootstrap(true);
                    break;
            }
        }
    }

}
