package org.xcore.plugin.vote;

import mindustry.maps.Map;

public interface VoteRtvFactory {
    VoteRtv create(Map target, boolean isManualSelection);
}
