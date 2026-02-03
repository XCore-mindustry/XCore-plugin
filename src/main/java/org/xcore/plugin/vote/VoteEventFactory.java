package org.xcore.plugin.vote;

import org.xcore.plugin.model.EventData;

public interface VoteEventFactory {
    VoteEvent create(EventData target);
}
