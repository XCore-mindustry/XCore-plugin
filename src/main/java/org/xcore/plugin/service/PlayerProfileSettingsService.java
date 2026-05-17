package org.xcore.plugin.service;

import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerActiveBadgeChangedCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerBadgeSymbolColorModeChangedCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerCustomNicknameChangedCommandV1;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;

@Singleton
public class PlayerProfileSettingsService {

    /** Vanilla Mindustry name length limit in UTF-8 bytes (see Vars.maxNameLength). */
    public static final int MAX_PLAIN_NAME_BYTES = 40;

    private final SessionService sessionService;
    private final PlayerDataRepository playerDataRepository;
    private final PlayerDisplayService playerDisplayService;
    private final NetworkService network;
    private final Config config;

    @Inject
    public PlayerProfileSettingsService(SessionService sessionService,
                                        PlayerDataRepository playerDataRepository,
                                        PlayerDisplayService playerDisplayService,
                                        NetworkService network,
                                        Config config) {
        this.sessionService = sessionService;
        this.playerDataRepository = playerDataRepository;
        this.playerDisplayService = playerDisplayService;
        this.network = network;
        this.config = config;
    }

    public record NicknameValidationResult(boolean valid, String errorKey, int maxBytes) {
        public static NicknameValidationResult ok() {
            return new NicknameValidationResult(true, null, MAX_PLAIN_NAME_BYTES);
        }

        public static NicknameValidationResult tooLong(int maxBytes) {
            return new NicknameValidationResult(false, "error-nickname-too-long", maxBytes);
        }

        public static NicknameValidationResult badgeGlyph() {
            return new NicknameValidationResult(false, "error-nickname-badge-glyph", MAX_PLAIN_NAME_BYTES);
        }
    }

    public NicknameValidationResult validateCustomNickname(String customNickname) {
        if (customNickname == null || customNickname.isBlank()) {
            return NicknameValidationResult.ok();
        }

        String plain = Strings.stripColors(customNickname);
        if (plain.getBytes(StandardCharsets.UTF_8).length > MAX_PLAIN_NAME_BYTES) {
            return NicknameValidationResult.tooLong(MAX_PLAIN_NAME_BYTES);
        }

        if (containsBadgeLikeGlyphs(plain)) {
            return NicknameValidationResult.badgeGlyph();
        }

        return NicknameValidationResult.ok();
    }

    public void updateCustomNickname(PlayerData targetData, String customNickname, boolean refreshDisplay, boolean sync) {
        mutate(targetData,
                data -> data.customNickname = customNickname,
                data -> playerDataRepository.updateCustomNickname(data.uuid, customNickname),
                data -> new PlayerCustomNicknameChangedCommandV1(data.uuid, data.customNickname, config.server),
                refreshDisplay,
                sync);
    }

    public void updateDescription(PlayerData targetData, String description) {
        mutate(targetData,
                data -> data.description = description,
                data -> playerDataRepository.updateDescription(data.uuid, description),
                null,
                false,
                false);
    }

    public void updateLeaderboard(PlayerData targetData, boolean leaderboard) {
        mutate(targetData,
                data -> data.leaderboard = leaderboard,
                data -> playerDataRepository.updateLeaderboard(data.uuid, leaderboard),
                null,
                false,
                false);
    }

    public void updateLanguage(PlayerData targetData, String language) {
        Session targetSession = sessionService.get(targetData.uuid);
        if (targetSession != null) {
            targetSession.updateLanguage(language);
            return;
        }

        mutate(targetData,
                data -> data.language = language,
                data -> playerDataRepository.updateLanguage(data.uuid, language),
                null,
                false,
                false);
    }

    public void updateTranslatorLanguage(PlayerData targetData, String language) {
        Session targetSession = sessionService.get(targetData.uuid);
        if (targetSession != null) {
            targetSession.updateTranslatorLanguage(language);
            return;
        }

        mutate(targetData,
                data -> data.translatorLanguage = language,
                data -> playerDataRepository.updateTranslatorLanguage(data.uuid, language),
                null,
                false,
                false);
    }

    public void updateGlobalChatVisible(PlayerData targetData, boolean visible) {
        Session targetSession = sessionService.get(targetData.uuid);
        if (targetSession != null) {
            targetSession.updateGlobalChatVisible(visible);
            return;
        }

        mutate(targetData,
                data -> data.globalChatVisible = visible,
                data -> playerDataRepository.updateGlobalChatVisible(data.uuid, visible),
                null,
                false,
                false);
    }

    public void updateDiscordRelayVisible(PlayerData targetData, boolean visible) {
        Session targetSession = sessionService.get(targetData.uuid);
        if (targetSession != null) {
            targetSession.updateDiscordRelayVisible(visible);
            return;
        }

        mutate(targetData,
                data -> data.discordRelayVisible = visible,
                data -> playerDataRepository.updateDiscordRelayVisible(data.uuid, visible),
                null,
                false,
                false);
    }

    public void updateActiveBadge(PlayerData targetData, String badgeId, boolean refreshDisplay, boolean sync) {
        mutate(targetData,
                data -> data.activeBadge = badgeId,
                data -> playerDataRepository.setActiveBadge(data.uuid, badgeId),
                data -> new PlayerActiveBadgeChangedCommandV1(data.uuid, data.activeBadge, config.server),
                refreshDisplay,
                sync);
    }

    public void updateBadgeSymbolColorMode(PlayerData targetData, String mode, boolean refreshDisplay, boolean sync) {
        mutate(targetData,
                data -> data.badgeSymbolColorMode = mode,
                data -> playerDataRepository.updateBadgeSymbolColorMode(data.uuid, mode),
                data -> new PlayerBadgeSymbolColorModeChangedCommandV1(data.uuid, data.badgeSymbolColorMode, config.server),
                refreshDisplay,
                sync);
    }

    private void mutate(PlayerData targetData,
                        Consumer<PlayerData> updater,
                        Consumer<PlayerData> persist,
                        Function<PlayerData, Object> syncFactory,
                        boolean refreshDisplay,
                        boolean sync) {
        Session targetSession = sessionService.get(targetData.uuid);
        if (targetSession != null) {
            updater.accept(targetSession.data);
            persist.accept(targetSession.data);
            if (refreshDisplay) {
                playerDisplayService.refresh(targetSession);
            }
            if (sync && syncFactory != null) {
                network.post(syncFactory.apply(targetSession.data));
            }
        } else {
            updater.accept(targetData);
            persist.accept(targetData);
            if (sync && syncFactory != null) {
                network.post(syncFactory.apply(targetData));
            }
        }
    }

    private boolean containsBadgeLikeGlyphs(String input) {
        return input.codePoints().anyMatch(Badge::containsReservedGlyph);
    }
}
