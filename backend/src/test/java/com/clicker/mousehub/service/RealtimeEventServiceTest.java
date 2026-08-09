package com.clicker.mousehub.service;

import com.clicker.mousehub.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeEventServiceTest {
    @Test
    void concurrentAdmissionNeverExceedsThePerAddressLimit() throws Exception {
        RealtimeEventService events = new RealtimeEventService(100, 20);
        AtomicInteger accepted = new AtomicInteger();
        int attempts = 200;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(attempts);
        var workers = Executors.newFixedThreadPool(32);

        for (int index = 0; index < attempts; index++) {
            workers.execute(() -> {
                try {
                    start.await();
                    events.connect("203.0.113.10");
                    accepted.incrementAndGet();
                } catch (BusinessException ignored) {
                    // Expected once the address has reserved all of its slots.
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }

        start.countDown();
        assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue();
        workers.shutdownNow();
        assertThat(accepted).hasValue(20);
        assertThat(events.connectionCount()).isEqualTo(20);
        assertThat(events.connectionCount("203.0.113.10")).isEqualTo(20);
    }

    @Test
    void concurrentAdmissionNeverExceedsTheGlobalLimit() throws Exception {
        RealtimeEventService events = new RealtimeEventService(40, 5);
        AtomicInteger accepted = new AtomicInteger();
        int attempts = 120;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(attempts);
        var workers = Executors.newFixedThreadPool(24);

        for (int index = 0; index < attempts; index++) {
            String address = "198.51.100." + index;
            workers.execute(() -> {
                try {
                    start.await();
                    events.connect(address);
                    accepted.incrementAndGet();
                } catch (BusinessException ignored) {
                    // Expected once the process-wide capacity is reserved.
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }

        start.countDown();
        assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue();
        workers.shutdownNow();
        assertThat(accepted).hasValue(40);
        assertThat(events.connectionCount()).isEqualTo(40);
    }

    @Test
    void rapidChangesOfTheSameTypeCollapseIntoOneGlobalInvalidation() {
        RealtimeEventService events = new RealtimeEventService(10, 10);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        events.publishAfterCommit("mouse.changed", first);
        assertThat(events.pendingEvent("mouse.changed").mouseId()).isEqualTo(first);

        events.publishAfterCommit("mouse.changed", first);
        assertThat(events.pendingEvent("mouse.changed").mouseId()).isEqualTo(first);

        events.publishAfterCommit("mouse.changed", second);
        assertThat(events.pendingEvent("mouse.changed").mouseId()).isNull();
    }
}
