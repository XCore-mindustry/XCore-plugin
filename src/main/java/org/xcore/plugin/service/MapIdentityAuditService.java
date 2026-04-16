package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class MapIdentityAuditService {

    private final MapDataRepository mapDataRepository;
    private final PlayerDataRepository playerDataRepository;

    @Inject
    public MapIdentityAuditService(MapDataRepository mapDataRepository,
                                   PlayerDataRepository playerDataRepository) {
        this.mapDataRepository = mapDataRepository;
        this.playerDataRepository = playerDataRepository;
    }

    public MapIdentityAuditReport audit() {
        List<MapData> maps = mapDataRepository.findAll();
        List<PlayerData> players = playerDataRepository.findAllWithMapVotes();

        Map<String, List<MapData>> byLegacyKey = maps.stream()
                .collect(Collectors.groupingBy(
                        map -> legacyKey(map.name, map.gameMode),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<ConflictGroup> conflictGroups = byLegacyKey.values().stream()
                .map(group -> buildConflictGroup(group, players))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt(ConflictGroup::affectedVoteReferences).reversed()
                        .thenComparing(Comparator.comparingInt((ConflictGroup group) -> group.maps().size()).reversed())
                        .thenComparing(ConflictGroup::legacyKey))
                .toList();

        int conflictingMaps = conflictGroups.stream()
                .mapToInt(group -> group.maps().size())
                .sum();

        int affectedPlayers = conflictGroups.stream()
                .flatMap(group -> group.affectedPlayers().stream())
                .collect(Collectors.toSet())
                .size();

        int affectedVoteReferences = conflictGroups.stream()
                .mapToInt(ConflictGroup::affectedVoteReferences)
                .sum();

        return new MapIdentityAuditReport(
                maps.size(),
                players.size(),
                conflictGroups,
                conflictingMaps,
                affectedPlayers,
                affectedVoteReferences
        );
    }

    private ConflictGroup buildConflictGroup(List<MapData> maps, List<PlayerData> players) {
        if (maps == null || maps.size() < 2) {
            return null;
        }

        Set<String> distinctFiles = maps.stream()
                .map(map -> normalize(map.fileName))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> distinctAuthors = maps.stream()
                .map(map -> normalize(map.author))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (distinctFiles.size() < 2 && distinctAuthors.size() < 2) {
            return null;
        }

        Set<String> mapIds = maps.stream()
                .map(map -> map.id == null ? null : map.id.toHexString())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> affectedPlayers = new LinkedHashSet<>();
        int affectedVoteReferences = 0;
        List<AffectedMapVote> affectedVotes = new ArrayList<>();

        for (PlayerData player : players) {
            if (player.mapVotes == null || player.mapVotes.isEmpty()) {
                continue;
            }

            List<String> overlappingVotes = player.mapVotes.keySet().stream()
                    .filter(mapIds::contains)
                    .sorted()
                    .toList();

            if (overlappingVotes.isEmpty()) {
                continue;
            }

            affectedPlayers.add(player.uuid);
            affectedVoteReferences += overlappingVotes.size();
            affectedVotes.add(new AffectedMapVote(player.uuid, player.pid, player.nickname, overlappingVotes));
        }

        List<MapIdentitySnapshot> snapshots = maps.stream()
                .map(map -> new MapIdentitySnapshot(
                        map.id == null ? "<missing>" : map.id.toHexString(),
                        map.name,
                        map.fileName,
                        map.author,
                        map.gameMode,
                        map.like,
                        map.dislike,
                        map.reputation
                ))
                .sorted(Comparator.comparing(MapIdentitySnapshot::mapId))
                .toList();

        affectedVotes.sort(Comparator
                .comparingInt(AffectedMapVote::voteReferenceCount).reversed()
                .thenComparing(AffectedMapVote::pid));

        MapData first = maps.getFirst();
        return new ConflictGroup(
                legacyKey(first.name, first.gameMode),
                first.name,
                first.gameMode,
                snapshots,
                List.copyOf(affectedPlayers),
                affectedVoteReferences,
                affectedVotes
        );
    }

    private String legacyKey(String name, String mode) {
        return normalize(name) + "|" + normalize(mode);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "<blank>" : value;
    }

    public record MapIdentityAuditReport(int mapsScanned,
                                         int playersScanned,
                                         List<ConflictGroup> conflictGroups,
                                         int conflictingMapCount,
                                         int affectedPlayerCount,
                                         int affectedVoteReferenceCount) {

        public boolean hasConflicts() {
            return !conflictGroups.isEmpty();
        }
    }

    public record ConflictGroup(String legacyKey,
                                String mapName,
                                String gameMode,
                                List<MapIdentitySnapshot> maps,
                                List<String> affectedPlayers,
                                int affectedVoteReferences,
                                List<AffectedMapVote> affectedVotes) {
    }

    public record MapIdentitySnapshot(String mapId,
                                      String name,
                                      String fileName,
                                      String author,
                                      String gameMode,
                                      int like,
                                      int dislike,
                                      int reputation) {
    }

    public record AffectedMapVote(String playerUuid,
                                  int pid,
                                  String nickname,
                                  List<String> conflictingMapIds) {

        public int voteReferenceCount() {
            return conflictingMapIds == null ? 0 : conflictingMapIds.size();
        }
    }
}
