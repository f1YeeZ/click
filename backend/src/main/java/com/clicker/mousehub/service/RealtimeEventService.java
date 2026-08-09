package com.clicker.mousehub.service;

import com.clicker.mousehub.common.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

@Service
public class RealtimeEventService {
    private static final long TEST_EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final Map<String, Integer> connectionsByAddress = new ConcurrentHashMap<>();
    private final Map<String, RealtimeEvent> pendingEvents = new ConcurrentHashMap<>();
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final AtomicLong eventIds = new AtomicLong();
    private final int maxConnections;
    private final int maxConnectionsPerAddress;
    private final long emitterTimeoutMs;
    private final long heartbeatIntervalMs;
    private final int fanoutChunkSize;
    private final CacheManager cacheManager;
    private final TaskExecutor fanoutExecutor;

    @Autowired
    public RealtimeEventService(@Value("${app.realtime.max-connections:5000}") int maxConnections,
                                @Value("${app.realtime.max-connections-per-address:50}") int maxConnectionsPerAddress,
                                @Value("${app.realtime.emitter-timeout-ms:0}") long emitterTimeoutMs,
                                @Value("${app.realtime.heartbeat-ms:25000}") long heartbeatIntervalMs,
                                @Value("${app.realtime.fanout-chunk-size:128}") int fanoutChunkSize,
                                CacheManager cacheManager,
                                @Qualifier("realtimeFanoutExecutor") TaskExecutor fanoutExecutor) {
        this.maxConnections = Math.max(1, maxConnections);
        this.maxConnectionsPerAddress = Math.max(1, maxConnectionsPerAddress);
        this.emitterTimeoutMs = emitterTimeoutMs <= 0 ? 0 : Math.max(10_000L, emitterTimeoutMs);
        this.heartbeatIntervalMs = Math.max(5_000L, heartbeatIntervalMs);
        this.fanoutChunkSize = Math.max(1, fanoutChunkSize);
        this.cacheManager = cacheManager;
        this.fanoutExecutor = fanoutExecutor;
    }

    public RealtimeEventService(int maxConnections, int maxConnectionsPerAddress) {
        this.maxConnections = Math.max(1, maxConnections);
        this.maxConnectionsPerAddress = Math.max(1, maxConnectionsPerAddress);
        this.emitterTimeoutMs = TEST_EMITTER_TIMEOUT_MS;
        this.heartbeatIntervalMs = 20_000L;
        this.fanoutChunkSize = 128;
        this.cacheManager = null;
        this.fanoutExecutor = new SyncTaskExecutor();
    }

    public SseEmitter connect(String remoteAddress) {
        String address = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
        reserveConnection(address);

        String clientId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(emitterTimeoutMs);
        Client client = new Client(address, emitter, new AtomicLong(initialHeartbeatAt()));
        clients.put(clientId, client);
        Runnable cleanup = () -> removeClient(clientId, client);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("ready").id(nextId()).reconnectTime(3000)
                    .data(new RealtimeEvent("ready", null, OffsetDateTime.now())));
        } catch (IOException | IllegalStateException exception) {
            cleanup.run();
            throw new BusinessException("REALTIME_CONNECT_FAILED", "实时连接建立失败", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return emitter;
    }

    public void publishAfterCommit(String type, UUID mouseId) {
        if ("review.changed".equals(type) || "mouse.changed".equals(type)) clearRealtimeCaches();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { enqueue(type, mouseId); }
            });
        } else {
            enqueue(type, mouseId);
        }
    }

    @Scheduled(fixedDelayString = "${app.realtime.event-coalesce-ms:100}")
    public void flushPendingEvents() {
        List<RealtimeEvent> batch = new ArrayList<>();
        pendingEvents.forEach((type, event) -> {
            if (pendingEvents.remove(type, event)) batch.add(event);
        });
        if (batch.isEmpty()) return;
        batch.forEach(this::broadcast);
    }

    @Scheduled(fixedDelayString = "${app.realtime.heartbeat-tick-ms:1000}")
    public void heartbeat() {
        long now = System.currentTimeMillis();
        List<Map.Entry<String, Client>> due = new ArrayList<>();
        clients.forEach((id, client) -> {
            long scheduledAt = client.nextHeartbeatAt().get();
            if (scheduledAt <= now && client.nextHeartbeatAt().compareAndSet(scheduledAt, Long.MAX_VALUE)) {
                due.add(Map.entry(id, client));
            }
        });
        if (due.isEmpty()) return;

        String id = nextId();
        fanOut(due, client -> SseEmitter.event().comment("heartbeat").id(id).reconnectTime(3000));
    }

    int connectionCount() {
        return activeConnections.get();
    }

    int connectionCount(String remoteAddress) {
        return connectionsByAddress.getOrDefault(remoteAddress, 0);
    }

    RealtimeEvent pendingEvent(String type) {
        return pendingEvents.get(type);
    }

    private void reserveConnection(String address) {
        int total = activeConnections.incrementAndGet();
        if (total > maxConnections) {
            activeConnections.decrementAndGet();
            rejectConnection();
        }

        AtomicBoolean accepted = new AtomicBoolean();
        connectionsByAddress.compute(address, (ignored, count) -> {
            int current = count == null ? 0 : count;
            if (current >= maxConnectionsPerAddress) return count;
            accepted.set(true);
            return current + 1;
        });
        if (!accepted.get()) {
            activeConnections.decrementAndGet();
            rejectConnection();
        }
    }

    private void rejectConnection() {
        throw new BusinessException("REALTIME_CONNECTION_LIMIT", "实时连接数量过多，请稍后重试", HttpStatus.TOO_MANY_REQUESTS);
    }

    private void removeClient(String clientId, Client client) {
        if (!clients.remove(clientId, client)) return;
        activeConnections.decrementAndGet();
        connectionsByAddress.computeIfPresent(client.remoteAddress(),
                (ignored, count) -> count <= 1 ? null : count - 1);
    }

    private void enqueue(String type, UUID mouseId) {
        pendingEvents.compute(type, (ignored, current) -> {
            UUID mergedMouseId = current == null || java.util.Objects.equals(current.mouseId(), mouseId)
                    ? mouseId : null;
            return new RealtimeEvent(type, mergedMouseId, OffsetDateTime.now());
        });
    }

    private void clearRealtimeCaches() {
        if (cacheManager == null) return;
        for (String name : List.of("recommendations", "catalog", "reviewSummaries", "supportSummaries")) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        }
    }

    private void broadcast(RealtimeEvent data) {
        if (clients.isEmpty()) return;
        String id = nextId();
        fanOut(new ArrayList<>(clients.entrySet()), client ->
                SseEmitter.event().name("resource-update").id(id).reconnectTime(3000).data(data));
    }

    private void fanOut(List<Map.Entry<String, Client>> targets,
                        Function<Client, SseEmitter.SseEventBuilder> eventFactory) {
        for (int start = 0; start < targets.size(); start += fanoutChunkSize) {
            int end = Math.min(start + fanoutChunkSize, targets.size());
            List<Map.Entry<String, Client>> chunk = List.copyOf(targets.subList(start, end));
            fanoutExecutor.execute(() -> chunk.forEach(entry ->
                    send(entry.getKey(), entry.getValue(), eventFactory.apply(entry.getValue()))));
        }
    }

    private void send(String clientId, Client client, SseEmitter.SseEventBuilder event) {
        try {
            client.emitter().send(event);
            scheduleNextHeartbeat(client);
        } catch (IOException | IllegalStateException exception) {
            removeClient(clientId, client);
            try {
                client.emitter().complete();
            } catch (RuntimeException ignored) {
                // The emitter may already have completed on another fan-out worker.
            }
        }
    }

    private long initialHeartbeatAt() {
        long halfInterval = Math.max(1L, heartbeatIntervalMs / 2);
        return System.currentTimeMillis() + halfInterval + ThreadLocalRandom.current().nextLong(halfInterval);
    }

    private void scheduleNextHeartbeat(Client client) {
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1L, heartbeatIntervalMs / 4));
        client.nextHeartbeatAt().set(System.currentTimeMillis() + heartbeatIntervalMs + jitter);
    }

    private String nextId() {
        return Long.toString(eventIds.incrementAndGet());
    }

    private record Client(String remoteAddress, SseEmitter emitter, AtomicLong nextHeartbeatAt) {}
    public record RealtimeEvent(String type, UUID mouseId, OffsetDateTime occurredAt) {}
}
