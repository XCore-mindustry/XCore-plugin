package org.xcore.plugin.command.controller.server;

import arc.files.Fi;
import com.google.gson.Gson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.service.TopMenuCacheService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataControllerTest {

    @Test
    @DisplayName("editData preserves player _id when saving updated payload")
    void editDataPreservesMongoId() {
        var repository = mock(PlayerDataRepository.class);
        var topMenuCacheService = mock(TopMenuCacheService.class);
        var configFile = mock(Fi.class);
        var config = new Config();
        var gson = new Gson();
        var find = mock(FindService.class);
        var sender = mock(XCoreSender.class);

        var player = new PlayerData("u-1", true);
        player.pid = 0;
        player.nickname = "pizduk";
        player.pvpRating = 123;
        player.id = new ObjectId();

        when(find.playerData("#0")).thenReturn(player);
        when(repository.save(org.mockito.ArgumentMatchers.any(PlayerData.class))).thenReturn(true);

        var controller = new DataController(repository, configFile, config, gson, find, topMenuCacheService);
        controller.editData(sender, "#0", "pvpRating", "0");

        var captor = ArgumentCaptor.forClass(PlayerData.class);
        verify(repository).save(captor.capture());

        var saved = captor.getValue();
        assertThat(saved.id).isEqualTo(player.id);
        assertThat(saved.pvpRating).isEqualTo(0);
        assertThat(saved.uuid).isEqualTo("u-1");
        verify(topMenuCacheService).invalidateAll();
    }
}
