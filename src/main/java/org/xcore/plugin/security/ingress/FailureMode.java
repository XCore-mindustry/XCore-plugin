package org.xcore.plugin.security.ingress;

/**
 * What an ingress check does when its own execution throws.
 *
 * <p>An exception here means the check never reached a verdict, so the connection was
 * never cleared by it. Treating that as "allowed" silently converts a broken dependency
 * into a moderation bypass — which is exactly the condition under which the server is
 * most likely to be under attack.</p>
 */
public enum FailureMode {

    /** A check that cannot run has not cleared the connection: deny it. */
    FAIL_CLOSED,

    /** A check that cannot run is treated as absent: allow the connection. */
    FAIL_OPEN
}
