package org.xcore.plugin.utils.models;

import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@NoArgsConstructor
@SuperBuilder
public class BanData extends Punishment {
    @Builder.Default
    public String ip = null;
}