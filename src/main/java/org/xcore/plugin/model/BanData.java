package org.xcore.plugin.model;

import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@NoArgsConstructor
@SuperBuilder
public class BanData extends Punishment {
    @Builder.Default
    public String ip = null;
}