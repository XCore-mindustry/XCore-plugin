package tilelogger;

import arc.math.geom.Point2;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.ai.UnitCommand;
import mindustry.ctype.Content;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.type.UnitType;
import mindustry.world.Block;

import java.nio.ByteBuffer;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Both directions share one checked contract. 0..7 remain readable for v1. */
public final class ConfigCodec {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int UNSUPPORTED = 9;
    private static long nextWarning;
    private ConfigCodec() {}
    public record Encoded(int type, int scalar, byte[] bytes) {
        public Encoded {
            if (type < 0 || type > UNSUPPORTED || bytes == null || bytes.length > MAX_BYTES)
                throw new IllegalArgumentException("Invalid configuration representation");
            bytes = bytes.clone();
        }
        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
        int byteSize() {
            return bytes.length;
        }
        // Copy into call-owned memory without exposing or cloning the immutable backing array.
        void copyBytesTo(MemorySegment target) {
            MemorySegment.copy(bytes, 0, target, ValueLayout.JAVA_BYTE, 0, bytes.length);
        }
        public boolean supported() {
            return type != UNSUPPORTED;
        }
    }
    private static Encoded scalar(int type, int value) {
        return new Encoded(type, value, new byte[0]);
    }
    private static Encoded blob(int type, byte[] bytes) {
        return bytes.length > MAX_BYTES ? unsupported("payload exceeds 1 MiB") : new Encoded(type, 0, bytes);
    }
    public static Encoded encode(Object value) {
        return switch (value) {
            case null -> scalar(0, 0);
            case Integer number -> scalar(1, number);
            case Boolean flag -> scalar(2, flag ? 1 : 0);
            case Item item -> named("item", item.name);
            case Liquid liquid -> named("liquid", liquid.name);
            case UnitType unit -> named("unit", unit.name);
            case Block block -> named("block", block.name);
            case UnitCommand command -> named("command", command.name);
            case Point2 point -> scalar(4, point.pack());
            case byte[] bytes -> blob(5, bytes);
            case String text -> blob(6, text.getBytes(StandardCharsets.UTF_8));
            case Point2[] points -> {
                if (points.length > MAX_BYTES / 4)
                    yield unsupported("point array exceeds 1 MiB");
                var data = ByteBuffer.allocate(points.length * 4);
                boolean valid = true;
                for (var point : points) {
                    if (point == null) {
                        valid = false;
                        break;
                    }
                    data.putInt(point.pack());
                }
                yield valid ? blob(7, data.array()) : unsupported("null point");
            }
            default -> unsupported(value.getClass().getName());
        };
    }
    private static Encoded named(String kind, String name) {
        return blob(8, (kind + ":" + name).getBytes(StandardCharsets.UTF_8));
    }
    private static Encoded unsupported(String description) {
        if (System.nanoTime() >= nextWarning) {
            Log.warn("[TileLogger] Configuration cannot be restored: @", description);
            nextWarning = System.nanoTime() + 30_000_000_000L;
        }
        return new Encoded(UNSUPPORTED, 0, description.getBytes(StandardCharsets.UTF_8));
    }
    public static Object decode(int type, Object value) {
        if (type < 0 || type > UNSUPPORTED)
            throw new IllegalArgumentException("Unknown configuration kind");
        if (type <= 4 && !(value instanceof Integer))
            throw new IllegalArgumentException("Expected scalar configuration");
        if (type >= 5 && (!(value instanceof byte[] bytes) || bytes.length > MAX_BYTES))
            throw new IllegalArgumentException("Invalid configuration payload");
        return switch (type) {
            case 0 -> null;
            case 1 -> value;
            case 2 -> {
                int flag = (Integer) value;
                if (flag != 0 && flag != 1)
                    throw new IllegalArgumentException("Invalid boolean configuration");
                yield flag == 1;
            }
            case 3 -> legacyContent((Integer) value);
            case 4 -> Point2.unpack((Integer) value);
            case 5 -> ((byte[]) value).clone();
            case 6 -> utf8((byte[]) value);
            case 7 -> {
                byte[] bytes = (byte[]) value;
                if (bytes.length % 4 != 0)
                    throw new IllegalArgumentException("Invalid point array");
                var input = ByteBuffer.wrap(bytes);
                var points = new Point2[bytes.length / 4];
                for (int i = 0; i < points.length; i++) points[i] = Point2.unpack(input.getInt());
                yield points;
            }
            case 8 -> namedContent(utf8((byte[]) value));
            default -> throw new IllegalArgumentException("Unsupported stored configuration");
        };
    }
    private static String utf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (java.nio.charset.CharacterCodingException invalid) {
            throw new IllegalArgumentException("Invalid UTF-8 configuration", invalid);
        }
    }
    private static Object namedContent(String reference) {
        int separator = reference.indexOf(':');
        if (separator < 1 || separator == reference.length() - 1)
            throw new IllegalArgumentException("Invalid content reference");
        String kind = reference.substring(0, separator), name = reference.substring(separator + 1);
        Object found = switch (kind) {
            case "item" -> Vars.content.item(name);
            case "liquid" -> Vars.content.liquid(name);
            case "unit" -> Vars.content.unit(name);
            case "block" -> Vars.content.block(name);
            case "command" -> Vars.content.unitCommands().find(command -> command.name.equals(name));
            default -> null;
        };
        if (found == null)
            throw new IllegalArgumentException("Content unavailable: " + reference);
        return found;
    }
    private static Object legacyContent(int index) {
        if (index < 0 || Vars.content == null)
            throw new IllegalArgumentException("Invalid legacy content index");
        for (Seq<?> values : new Seq<?>[] {Vars.content.items(), Vars.content.liquids(), Vars.content.units(),
                     Vars.content.blocks(), Vars.content.unitCommands()}) {
            if (index < values.size)
                return values.get(index);
            index -= values.size;
        }
        throw new IllegalArgumentException("Legacy content index out of range");
    }
    public static String manifest() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (Seq<? extends Content> values :
                    java.util.List.<Seq<? extends Content>>of(Vars.content.items(), Vars.content.liquids(),
                            Vars.content.units(), Vars.content.blocks(), Vars.content.unitCommands())) {
                for (Content content : values)
                    digest.update((content.getContentType() + ":" + content.id + ":"
                            + ((mindustry.ctype.MappableContent) content).name + "\n")
                                    .getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
    public static String display(int type, Object value) {
        if (type == UNSUPPORTED)
            return "[unrestorable]";
        try {
            Object decoded = decode(type, value);
            if (decoded == null)
                return "";
            if (decoded instanceof byte[] bytes)
                return bytes.length + "b";
            if (decoded instanceof Point2[] points) {
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < Math.min(points.length, 16); i++) result.append(points[i]).append(' ');
                if (points.length > 16)
                    result.append('…');
                return result.toString();
            }
            String text = decoded.toString();
            return text.length() <= 256
                    ? text
                    : text.substring(0,
                              text.offsetByCodePoints(
                                      0, Math.min(256, text.codePointCount(0, text.length()))))
                            + "…";
        } catch (IllegalArgumentException | ClassCastException failure) {
            return "[unrestorable]";
        }
    }
}
