package org.xcore.plugin.service;

import jakarta.inject.Singleton;
import mindustry.gen.Player;

@Singleton
public class ClientCompatibilityService {

    private static final byte FOO_USER = (byte) 0b10101010;

    public boolean isLikelyFoosClient(Player player) {
        if (player == null) {
            return false;
        }

        return isEmbedded(player.mouseX, FOO_USER) || isEmbedded(player.mouseY, FOO_USER);
    }

    private boolean isEmbedded(float value, byte marker) {
        int bits = Float.floatToIntBits(value);
        int markerBits = Byte.toUnsignedInt(marker);
        return (bits & markerBits) == markerBits;
    }
}
