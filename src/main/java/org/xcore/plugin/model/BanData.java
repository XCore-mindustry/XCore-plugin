package org.xcore.plugin.model;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BanData extends Punishment {
    @Builder.Default public String ip = null;
}