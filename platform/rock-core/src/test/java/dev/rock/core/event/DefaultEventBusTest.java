package dev.rock.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rock.api.event.AbstractCancellable;
import dev.rock.api.event.Event;
import dev.rock.api.event.EventPriority;
import dev.rock.api.event.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DefaultEventBusTest {

    private final DefaultEventBus bus = new DefaultEventBus(Executors.newVirtualThreadPerTaskExecutor());

    record TestEvent(String payload) implements Event {
    }

    static final class CancellableTestEvent extends AbstractCancellable {
    }

    @Test
    void listenersRunInPriorityOrder() {
        List<String> order = new ArrayList<>();
        bus.subscribe(TestEvent.class, EventPriority.LAST, e -> order.add("LAST"));
        bus.subscribe(TestEvent.class, EventPriority.FIRST, e -> order.add("FIRST"));
        bus.subscribe(TestEvent.class, EventPriority.NORMAL, e -> order.add("NORMAL"));
        bus.subscribe(TestEvent.class, EventPriority.EARLY, e -> order.add("EARLY"));
        bus.subscribe(TestEvent.class, EventPriority.LATE, e -> order.add("LATE"));

        bus.publish(new TestEvent("x"));

        assertEquals(List.of("FIRST", "EARLY", "NORMAL", "LATE", "LAST"), order);
    }

    @Test
    void cancellationAtFirstHidesEventFromNormalListeners() {
        AtomicBoolean normalSawIt = new AtomicBoolean(false);
        AtomicBoolean optInSawIt = new AtomicBoolean(false);

        bus.subscribe(CancellableTestEvent.class, EventPriority.FIRST, AbstractCancellable::cancel);
        bus.subscribe(CancellableTestEvent.class, EventPriority.NORMAL, e -> normalSawIt.set(true));
        bus.subscribe(CancellableTestEvent.class, EventPriority.LAST, true, e -> optInSawIt.set(true));

        CancellableTestEvent event = bus.publish(new CancellableTestEvent());

        assertTrue(event.cancelled());
        assertFalse(normalSawIt.get(), "NORMAL listener must not see a cancelled event");
        assertTrue(optInSawIt.get(), "receiveCancelled listener must still see it");
    }

    @Test
    void throwingListenerDoesNotStopRemainingListeners() {
        AtomicInteger delivered = new AtomicInteger();
        bus.subscribe(TestEvent.class, EventPriority.EARLY, e -> {
            throw new IllegalStateException("boom");
        });
        bus.subscribe(TestEvent.class, EventPriority.NORMAL, e -> delivered.incrementAndGet());

        bus.publish(new TestEvent("x"));

        assertEquals(1, delivered.get());
    }

    @Test
    void closedSubscriptionStopsReceiving() {
        AtomicInteger count = new AtomicInteger();
        Subscription subscription = bus.subscribe(TestEvent.class, e -> count.incrementAndGet());

        bus.publish(new TestEvent("a"));
        subscription.close();
        bus.publish(new TestEvent("b"));

        assertEquals(1, count.get());
        assertFalse(subscription.active());
    }

    @Test
    void publishAsyncDeliversOffCallingThread() throws Exception {
        Thread caller = Thread.currentThread();
        AtomicBoolean differentThread = new AtomicBoolean(false);
        bus.subscribe(TestEvent.class, e -> differentThread.set(Thread.currentThread() != caller));

        bus.publishAsync(new TestEvent("x")).get();

        assertTrue(differentThread.get());
    }

    @Test
    void publishReturnsSameInstanceForInspection() {
        TestEvent event = new TestEvent("payload");
        assertEquals(event, bus.publish(event));
    }
}
