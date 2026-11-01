package org.xcore.protocol.generated.messages.maps;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;
import org.xcore.protocol.generated.shared.MapEntryV1;
import org.xcore.protocol.generated.shared.MapFileSourceV1;
import org.xcore.protocol.generated.shared.MapInspectFindingV1;
import org.xcore.protocol.generated.shared.MapInspectInfoV1;
import org.xcore.protocol.generated.shared.MapUploadResultV1;

public final class MapsMessages {
    private MapsMessages() {
        throw new AssertionError("No org.xcore.protocol.generated.messages.maps.MapsMessages instances");
    }

    public record MapsInspectRequestV1(
            String server,
            MapFileSourceV1 file,
            String profile
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "maps.inspect.request";
    public static final int MESSAGE_VERSION = 1;

        public MapsInspectRequestV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(file, "file must not be null");
            if (profile != null) {
                Objects.requireNonNull(profile, "profile must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            payload.put("file", file.toPayload());
            if (profile != null) {
                payload.put("profile", profile);
            }
            return payload;
        }
    }

    public record MapsInspectResponseV1(
            String server,
            MapsInspectResponseV1Status status,
            String detail,
            MapInspectInfoV1 info,
            List<MapInspectFindingV1> findings
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "maps.inspect.response";
    public static final int MESSAGE_VERSION = 1;

        public MapsInspectResponseV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(status, "status must not be null");
            if (detail != null) {
                Objects.requireNonNull(detail, "detail must not be null");
            }
            if (info != null) {
                Objects.requireNonNull(info, "info must not be null");
            }
            findings = Objects.requireNonNull(findings, "findings must not be null");
            findings = List.copyOf(findings);
            for (MapInspectFindingV1 item : findings) {
                Objects.requireNonNull(item, "findings[] must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            if (status != null) {
                payload.put("status", status.toString());
            }
            if (detail != null) {
                payload.put("detail", detail);
            }
            if (info != null) {
                payload.put("info", info.toPayload());
            }
            if (findings != null) {
                payload.put(
        "findings",
        findings.stream()
            .map(item -> item.toPayload())
            .toList()
    );
            }
            return payload;
        }
    }

    public record MapsListRequestV1(
            String server
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "maps.list.request";
    public static final int MESSAGE_VERSION = 1;

        public MapsListRequestV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            return payload;
        }
    }

    public record MapsListResponseV1(
            String server,
            List<MapEntryV1> maps
    ) implements ProtocolPayload {
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

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            if (maps != null) {
                payload.put(
        "maps",
        maps.stream()
            .map(item -> item.toPayload())
            .toList()
    );
            }
            return payload;
        }
    }

    public record MapsLoadCommandV1(
            String server,
            List<MapFileSourceV1> files
    ) implements ProtocolPayload {
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

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            if (files != null) {
                payload.put(
        "files",
        files.stream()
            .map(item -> item.toPayload())
            .toList()
    );
            }
            return payload;
        }
    }

    public record MapsRemoveRequestV1(
            String server,
            String fileName
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "maps.remove.request";
    public static final int MESSAGE_VERSION = 1;

        public MapsRemoveRequestV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(fileName, "fileName must not be null");
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            payload.put("fileName", fileName);
            return payload;
        }
    }

    public record MapsRemoveResponseV1(
            String server,
            String result
    ) implements ProtocolPayload {
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

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            payload.put("result", result);
            return payload;
        }
    }

    public record MapsUploadRequestV1(
            String server,
            List<MapFileSourceV1> files
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "maps.upload.request";
    public static final int MESSAGE_VERSION = 1;

        public MapsUploadRequestV1 {
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

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            if (files != null) {
                payload.put(
        "files",
        files.stream()
            .map(item -> item.toPayload())
            .toList()
    );
            }
            return payload;
        }
    }

    public record MapsUploadResponseV1(
            String server,
            List<MapUploadResultV1> results
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "maps.upload.response";
    public static final int MESSAGE_VERSION = 1;

        public MapsUploadResponseV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            results = Objects.requireNonNull(results, "results must not be null");
            results = List.copyOf(results);
            if (results.size() < 1) {
                throw new IllegalArgumentException("results must contain at least 1 item(s)");
            }
            for (MapUploadResultV1 item : results) {
                Objects.requireNonNull(item, "results[] must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            if (results != null) {
                payload.put(
        "results",
        results.stream()
            .map(item -> item.toPayload())
            .toList()
    );
            }
            return payload;
        }
    }
}
