package org.xcore.plugin.security;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Constrains map file names that arrive from a remote peer before they are used to
 * touch the filesystem.
 *
 * <p>Map file names originate as a Discord attachment name and travel over the protocol
 * inside {@code MapFileSourceV1.fileName}. The publishing service applies its own
 * filtering, but a service on the other end of a Redis stream is not a trust boundary
 * we control, so the server validates the field itself: {@code Fi.child(String)} resolves
 * against a parent directory yet happily traverses out of it via {@code ../} segments.</p>
 *
 * <p>Malformed names are rejected rather than sanitized. A peer that sends
 * {@code ../../etc/cron.d/x.msav} is not trying to name a map, and rewriting it into a
 * legal name would hide the attempt instead of surfacing it.</p>
 */
public final class MapFileNameSanitizer {

    /**
     * Conservative allowlist: a leading alphanumeric, then letters, digits, spaces and a
     * few punctuation marks, ending in {@code .msav}. No leading dot (hidden files), no
     * separators, no traversal tokens, no control characters, and no non-ASCII names
     * whose byte encoding varies by filesystem.
     */
    private static final Pattern SAFE_NAME = Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9 ._()\\-]{0,63}\\.msav$",
            Pattern.CASE_INSENSITIVE);

    /** Names that still resolve to "some directory's entry" but never to a writable file. */
    private static final Set<String> REJECTED_NAMES = Set.of("", ".", "..");

    /** Upper bound accepted before any path parsing: 64 stem characters (1 + 63) plus ".msav". */
    private static final int MAX_NAME_LENGTH = 69;

    private MapFileNameSanitizer() {
    }

    /**
     * Validates a peer-supplied map file name.
     *
     * @param raw the {@code fileName} field exactly as published by the remote peer
     * @return the same name, proven to be a single segment inside the target directory
     * @throws IllegalArgumentException if the name is absent, malformed, or attempts to
     *                                  choose its own location on disk
     */
    public static String requireSafeName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Map file name is empty");
        }
        if (raw.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Map file name exceeds " + MAX_NAME_LENGTH + " characters");
        }

        final String basename;
        try {
            // "." normalises to the empty path, whose file name is null rather than ".".
            Path fileName = Path.of(raw).getFileName();
            basename = fileName == null ? null : fileName.toString();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Map file name is not a valid path segment", e);
        }

        // A name that needed a directory part to resolve is choosing its own destination,
        // even when the part happens to be innocuous ("./x.msav").
        if (basename == null
                || !basename.equals(raw)
                || REJECTED_NAMES.contains(basename)
                || !SAFE_NAME.matcher(basename).matches()) {
            throw new IllegalArgumentException("Unsafe map file name: " + raw);
        }

        return basename;
    }
}
