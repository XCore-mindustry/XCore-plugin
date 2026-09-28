package org.xcore.testkit.fixtures;

import mindustry.gen.AnnounceCallPacket;
import mindustry.gen.InfoMessageCallPacket;
import mindustry.gen.KickCallPacket;
import mindustry.gen.KickCallPacket2;
import mindustry.gen.SendMessageCallPacket;
import mindustry.gen.SendMessageCallPacket2;
import mindustry.gen.WarningToastCallPacket;
import mindustry.net.NetConnection;
import mindustry.net.Packets;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Headless in-memory {@link NetConnection} recording sent packets, chat messages,
 * toasts, announcements, kicks, and byte streams without socket I/O.
 */
public class MockNetConnection extends NetConnection {
    private final List<Object> sentPackets = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private final List<String> announcements = new ArrayList<>();
    private final List<String> infoMessages = new ArrayList<>();
    private final List<String> warningToasts = new ArrayList<>();
    private final Map<Integer, Integer> streamTotals = new ConcurrentHashMap<>();
    private final Map<Integer, ByteArrayOutputStream> activeStreams = new ConcurrentHashMap<>();
    private final Map<Integer, byte[]> completedStreams = new ConcurrentHashMap<>();

    private boolean closed = false;
    private String kickReason;

    public MockNetConnection(String address) {
        super(address);
        this.hasConnected = true;
        this.connectTime = System.currentTimeMillis() - 1000L;
    }

    @Override
    public synchronized void send(Object object, boolean reliable) {
        sentPackets.add(object);

        if (object instanceof SendMessageCallPacket2 msg) {
            messages.add(msg.message);
        } else if (object instanceof SendMessageCallPacket msg) {
            messages.add(msg.message);
        } else if (object instanceof AnnounceCallPacket ann) {
            announcements.add(ann.message);
        } else if (object instanceof InfoMessageCallPacket info) {
            infoMessages.add(info.message);
        } else if (object instanceof WarningToastCallPacket toast) {
            warningToasts.add(toast.text);
        } else if (object instanceof KickCallPacket kp) {
            this.kickReason = kp.reason;
        } else if (object instanceof KickCallPacket2 kp2) {
            this.kickReason = kp2.reason != null ? kp2.reason.name() : "unknown";
        } else if (object instanceof Packets.StreamBegin begin) {
            streamTotals.put(begin.id, begin.total);
            activeStreams.put(begin.id, new ByteArrayOutputStream(begin.total));
        } else if (object instanceof Packets.StreamChunk chunk) {
            ByteArrayOutputStream out = activeStreams.get(chunk.id);
            if (out != null) {
                out.write(chunk.data, 0, chunk.data.length);
                int total = streamTotals.getOrDefault(chunk.id, -1);
                if (total > 0 && out.size() >= total) {
                    completedStreams.put(chunk.id, out.toByteArray());
                    activeStreams.remove(chunk.id);
                    streamTotals.remove(chunk.id);
                }
            }
        }
    }

    @Override
    public synchronized void close() {
        this.closed = true;
    }

    public synchronized List<Object> sentPackets() {
        return Collections.unmodifiableList(new ArrayList<>(sentPackets));
    }

    public synchronized List<String> messages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    public synchronized String lastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public synchronized List<String> announcements() {
        return Collections.unmodifiableList(new ArrayList<>(announcements));
    }

    public synchronized String lastAnnouncement() {
        return announcements.isEmpty() ? null : announcements.get(announcements.size() - 1);
    }

    public synchronized List<String> infoMessages() {
        return Collections.unmodifiableList(new ArrayList<>(infoMessages));
    }

    public synchronized String lastInfoMessage() {
        return infoMessages.isEmpty() ? null : infoMessages.get(infoMessages.size() - 1);
    }

    public synchronized List<String> warningToasts() {
        return Collections.unmodifiableList(new ArrayList<>(warningToasts));
    }

    public synchronized String lastWarningToast() {
        return warningToasts.isEmpty() ? null : warningToasts.get(warningToasts.size() - 1);
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    public synchronized String kickReason() {
        return kickReason;
    }

    public synchronized Map<Integer, byte[]> receivedStreams() {
        return Collections.unmodifiableMap(completedStreams);
    }

    public synchronized void clearTranscript() {
        sentPackets.clear();
        messages.clear();
        announcements.clear();
        infoMessages.clear();
        warningToasts.clear();
        streamTotals.clear();
        activeStreams.clear();
        completedStreams.clear();
        kickReason = null;
    }
}
