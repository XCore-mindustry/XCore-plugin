package org.xcore.plugin.modules.votes;

import mindustry.maps.Map;

public interface VoteRtvFactory {
    VoteRtv create(Map target, boolean isManualSelection);
}
