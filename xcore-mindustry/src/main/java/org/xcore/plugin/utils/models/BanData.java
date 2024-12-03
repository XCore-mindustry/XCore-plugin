package org.xcore.plugin.utils.models;

import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.xcore.plugin.PluginVars.database;

@NoArgsConstructor
@SuperBuilder
public class BanData extends Punishment {
    @Builder.Default
    public String ip = null;

    @Override
    public void save() {
        database.banDatas.save(this);
    }
}