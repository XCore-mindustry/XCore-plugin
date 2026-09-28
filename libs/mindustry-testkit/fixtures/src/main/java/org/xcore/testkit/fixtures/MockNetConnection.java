package org.xcore.testkit.fixtures;

import mindustry.gen.AnnounceCallPacket;
import mindustry.gen.InfoMessageCallPacket;
import mindustry.gen.InfoPopupCallPacket;
import mindustry.gen.InfoPopupCallPacket2;
import mindustry.gen.InfoPopupReliableCallPacket;
import mindustry.gen.InfoPopupReliableCallPacket2;
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
 * toasts, announcements, positioned HUD popups, kicks, and byte streams without socket I/O.
 */
public class MockNetConnection extends NetConnection {
    /** Which {@code Call} overload produced a popup, so assertions can target one variant. */
    public enum PopupVariant { MESSAGE, POPUP, RELIABLE, KEYED, KEYED_RELIABLE }

    /** Common shape of every {@code Call.infoMessage}/{@code Call.infoPopup*} variant. */
    public record InfoPopup(String message, float duration, int align, String id, PopupVariant variant) {}

    private final List<Object> sentPackets = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private final List<String> announcements = new ArrayList<>();
    private final List<String> infoMessages = new ArrayList<>();
    private final List<InfoPopup> infoPopups = new ArrayList<>();
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
        // A closed connection is detached: MockNet still holds the reference in its registry
        // (nothing unregisters on close), but nothing sent to it may reach the transcript.
        if (closed) return;

        sentPackets.add(object);

        if (object instanceof SendMessageCallPacket2 msg) {
            messages.add(msg.message);
        } else if (object instanceof SendMessageCallPacket msg) {
            messages.add(msg.message);
        } else if (object instanceof AnnounceCallPacket ann) {
            announcements.add(ann.message);
        } else if (object instanceof InfoMessageCallPacket info) {
            infoMessages.add(info.message);
            infoPopups.add(new InfoPopup(info.message, 0f, -1, null, PopupVariant.MESSAGE));
        } else if (object instanceof InfoPopupCallPacket popup) {
            infoPopups.add(new InfoPopup(popup.message, popup.duration, popup.align, null, PopupVariant.POPUP));
        } else if (object instanceof InfoPopupReliableCallPacket popup) {
            infoPopups.add(new InfoPopup(popup.message, popup.duration, popup.align, null, PopupVariant.RELIABLE));
        } else if (object instanceof InfoPopupCallPacket2 popup) {
            infoPopups.add(new InfoPopup(popup.message, popup.duration, popup.align, popup.id, PopupVariant.KEYED));
        } else if (object instanceof InfoPopupReliableCallPacket2 popup) {
            infoPopups.add(new InfoPopup(popup.message, popup.duration, popup.align, popup.id, PopupVariant.KEYED_RELIABLE));
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

    public synchronized List<InfoPopup> infoPopups() {
        return Collections.unmodifiableList(new ArrayList<>(infoPopups));
    }

    public synchronized InfoPopup lastInfoPopup() {
        return infoPopups.isEmpty() ? null : infoPopups.get(infoPopups.size() - 1);
    }

    /** Popup texts in wire order, spanning every {@code Call.infoMessage}/{@code Call.infoPopup*} variant. */
    public synchronized List<String> infoPopupTexts() {
        List<String> texts = new ArrayList<>(infoPopups.size());
        for (InfoPopup popup : infoPopups) {
            texts.add(popup.message);
        }
        return Collections.unmodifiableList(texts);
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
        infoPopups.clear();
        warningToasts.clear();
        streamTotals.clear();
        activeStreams.clear();
        completedStreams.clear();
        kickReason = null;
    }
}
