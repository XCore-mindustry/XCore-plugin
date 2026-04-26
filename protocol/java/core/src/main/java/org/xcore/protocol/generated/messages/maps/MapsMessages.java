package org.xcore.protocol.generated.messages.maps;

import java.util.List;
import java.util.Objects;
import org.xcore.protocol.generated.shared.MapEntryV1;
import org.xcore.protocol.generated.shared.MapFileSourceV1;

public final class MapsMessages {
    private MapsMessages() {
        throw new AssertionError("No org.xcore.protocol.generated.messages.maps.MapsMessages instances");
    }

    public record MapsListRequestV1(
            String server
    ) {
        public static final String MESSAGE_TYPE = "maps.list.request";
        public static final int MESSAGE_VERSION = 1;

        public MapsListRequestV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
        }
    }

    public record MapsListResponseV1(
            String server,
            List<MapEntryV1> maps
    ) {
        public static final String MESSAGE_TYPE = "maps.list.response";
        public static final int MESSAGE_VERSION = 1;

        public MapsListResponseV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            maps = Objects.requireNonNull(maps, "maps must not be null");
            maps = List.copyOf(maps);
            for (MapEntryV1 item : maps) {
                Objects.requireNonNull(item, "maps[] must not be null");
            }
        }
    }

    public record MapsLoadCommandV1(
            String server,
            List<MapFileSourceV1> files
    ) {
        public static final String MESSAGE_TYPE = "maps.load.command";
        public static final int MESSAGE_VERSION = 1;

        public MapsLoadCommandV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            files = Objects.requireNonNull(files, "files must not be null");
            files = List.copyOf(files);
            if (files.size() < 1) {
                throw new IllegalArgumentException("files must contain at least 1 item(s)");
            }
            for (MapFileSourceV1 item : files) {
                Objects.requireNonNull(item, "files[] must not be null");
            }
        }
    }

    public record MapsRemoveRequestV1(
            String server,
            String fileName
    ) {
        public static final String MESSAGE_TYPE = "maps.remove.request";
        public static final int MESSAGE_VERSION = 1;

        public MapsRemoveRequestV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(fileName, "fileName must not be null");
        }
    }

    public record MapsRemoveResponseV1(
            String server,
            String result
    ) {
        public static final String MESSAGE_TYPE = "maps.remove.response";
        public static final int MESSAGE_VERSION = 1;

        public MapsRemoveResponseV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(result, "result must not be null");
            if (result.length() < 1) {
                throw new IllegalArgumentException("result must be at least 1 characters");
            }
        }
    }
}
