package org.xcore.protocol.generated.runtime;

import java.util.Map;

/**
 * Canonical wire payload contract for generated XCore protocol models.
 *
 * <p>Every generated protocol record that appears on the wire MUST implement this
 * interface.  The returned {@code Map} includes {@code messageType},
 * {@code messageVersion}, and every non-null field with the exact JSON key the
 * matching Python generated model expects.  Callers serialize the map with
 * their JSON library of choice (Gson, Jackson, etc.).
 */
public interface ProtocolPayload {

    /** Return the canonical wire payload as a flat or nested {@code Map}. */
    Map<String, Object> toPayload();
}
