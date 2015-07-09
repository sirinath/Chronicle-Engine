package net.openhft.chronicle.engine.pubsub;

import net.openhft.chronicle.core.pool.ClassAliasPool;
import net.openhft.chronicle.engine.api.pubsub.InvalidSubscriberException;
import net.openhft.chronicle.engine.api.pubsub.Reference;
import net.openhft.chronicle.engine.api.pubsub.Subscriber;
import net.openhft.chronicle.engine.api.tree.Asset;
import net.openhft.chronicle.engine.api.tree.AssetNotFoundException;
import net.openhft.chronicle.engine.api.tree.RequestContext;
import net.openhft.chronicle.engine.server.internal.PublisherHandler.EventId;
import net.openhft.chronicle.network.connection.AbstractAsyncSubscription;
import net.openhft.chronicle.network.connection.AbstractStatelessClient;
import net.openhft.chronicle.network.connection.CoreFields;
import net.openhft.chronicle.network.connection.TcpChannelHub;
import net.openhft.chronicle.wire.ValueIn;
import net.openhft.chronicle.wire.WireIn;
import net.openhft.chronicle.wire.WireOut;
import net.openhft.chronicle.wire.Wires;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static net.openhft.chronicle.engine.server.internal.PublisherHandler.EventId.registerSubscriber;

/**
 * Created by Rob Austin
 */
public class RemoteReference<E> extends AbstractStatelessClient<EventId> implements Reference<E> {
    private final Class<E> messageClass;

    public RemoteReference(RequestContext requestContext, Asset asset) {
        this(asset.findView(TcpChannelHub.class), requestContext.messageType(), asset.fullName());
    }

    public RemoteReference(TcpChannelHub hub, Class<E> messageClass, String fullName)
            throws AssetNotFoundException {
        super(hub, (long) 0, toUri(fullName, messageClass));

        this.messageClass = messageClass;
    }

    private static String toUri(String fullName, Class messageClass) {
        StringBuilder uri = new StringBuilder("/" + fullName + "?view=reference");

        if (messageClass != String.class)
            uri.append("&messageType=").append(ClassAliasPool.CLASS_ALIASES.nameFor(messageClass));

        return uri.toString();
    }

    @Override
    public void set(final E event) {
        checkEvent(event);
        sendEventAsync(EventId.publish, valueOut -> valueOut.object(event));
    }

    @Override
    public E get() {
        // TODO CE-101 pass to the server
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public E getAndSet(E e) {
        // TODO CE-101 pass to the server
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public void remove() {
        // TODO CE-101 pass to the server
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public E getAndRemove() {
        // TODO CE-101 pass to the server
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public void unregisterSubscriber(Subscriber<E> subscriber) {
        // TODO CE-101 pass to the server
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public int subscriberCount() {
        // TODO CE-101 pass to the server
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public void registerSubscriber(boolean bootstrap, @NotNull final Subscriber subscriber) throws AssetNotFoundException {

        if (hub.outBytesLock().isHeldByCurrentThread())
            throw new IllegalStateException("Cannot view map while debugging");

        final AbstractAsyncSubscription asyncSubscription = new AbstractAsyncSubscription(hub, csp + "&bootstrap=" + bootstrap) {

            @Override
            public void onSubscribe(@NotNull final WireOut wireOut) {
                wireOut.writeEventName(registerSubscriber).text("");
            }

            @Override
            public void onConsumer(@NotNull final WireIn w) {
                w.readDocument(null, d -> {
                    final StringBuilder eventname = Wires.acquireStringBuilder();
                    final ValueIn valueIn = d.readEventName(eventname);

                    if (EventId.onEndOfSubscription.contentEquals(eventname)) {
                        subscriber.onEndOfSubscription();
                        hub.unsubscribe(tid());
                    } else if (CoreFields.reply.contentEquals(eventname)) {
                        valueIn.marshallable(m -> {
                            final E message = m.read(() -> "message").object(messageClass);
                            RemoteReference.this.onEvent(message, subscriber);
                        });
                    }

                });
            }

        };

        hub.subscribe(asyncSubscription);
    }

    private void onEvent(@Nullable E message, @NotNull Subscriber<E> subscriber) {
        try {
            if (message == null) {
                // todo
            } else
                subscriber.onMessage(message);
        } catch (InvalidSubscriberException noLongerValid) {
            // todo
        }
    }

    private void checkEvent(@Nullable Object key) {
        if (key == null)
            throw new NullPointerException("event can not be null");
    }
}
