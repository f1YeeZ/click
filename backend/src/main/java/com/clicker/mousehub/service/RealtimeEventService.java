package com.clicker.mousehub.service;

import com.clicker.mousehub.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RealtimeEventService {
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final AtomicLong eventIds = new AtomicLong();
    private final int maxConnections;
    private final int maxConnectionsPerAddress;

    public RealtimeEventService(@Value("${app.realtime.max-connections:200}") int maxConnections,
                                @Value("${app.realtime.max-connections-per-address:5}") int maxConnectionsPerAddress) {
        this.maxConnections = Math.max(1, maxConnections);
        this.maxConnectionsPerAddress = Math.max(1, maxConnectionsPerAddress);
    }

    public synchronized SseEmitter connect(String remoteAddress) {
        String address = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
        long addressConnections = clients.values().stream().filter(client -> address.equals(client.remoteAddress())).count();
        if (clients.size() >= maxConnections || addressConnections >= maxConnectionsPerAddress) {
            throw new BusinessException("REALTIME_CONNECTION_LIMIT", "实时连接数量过多，请稍后重试", HttpStatus.TOO_MANY_REQUESTS);
        }
        String clientId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        clients.put(clientId, new Client(address, emitter));
        Runnable cleanup = () -> clients.remove(clientId);
        emitter.onCompletion(cleanup); emitter.onTimeout(cleanup); emitter.onError(error -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("ready").id(nextId()).reconnectTime(3000)
                    .data(new RealtimeEvent("ready", null, OffsetDateTime.now())));
        } catch (IOException exception) {
            cleanup.run();
            throw new BusinessException("REALTIME_CONNECT_FAILED", "实时连接建立失败", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return emitter;
    }

    public void publishAfterCommit(String type, UUID mouseId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish(type, mouseId); }
            });
        } else publish(type, mouseId);
    }

    @Scheduled(fixedRateString = "${app.realtime.heartbeat-ms:20000}")
    public void heartbeat() {
        clients.forEach((id, client) -> send(id, client.emitter(),
                SseEmitter.event().comment("heartbeat").id(nextId()).reconnectTime(3000)));
    }

    int connectionCount() { return clients.size(); }

    private void publish(String type, UUID mouseId) {
        RealtimeEvent data = new RealtimeEvent(type, mouseId, OffsetDateTime.now());
        String id = nextId();
        clients.forEach((clientId, client) -> send(clientId, client.emitter(),
                SseEmitter.event().name("resource-update").id(id).reconnectTime(3000).data(data)));
    }

    private void send(String clientId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try { emitter.send(event); }
        catch (IOException | IllegalStateException exception) {
            clients.remove(clientId);
            try { emitter.complete(); } catch (RuntimeException ignored) { }
        }
    }

    private String nextId() { return Long.toString(eventIds.incrementAndGet()); }

    private record Client(String remoteAddress, SseEmitter emitter) {}
    public record RealtimeEvent(String type, UUID mouseId, OffsetDateTime occurredAt) {}
}
