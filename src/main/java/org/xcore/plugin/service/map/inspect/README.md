# Detached map inspection (P4)

`MapInspectionService.capture()` copies the registered content descriptors, aliases,
team names, new-game rule defaults and inspection settings on the game thread.
`inspect(file, requestedProfile, context)` is a synchronous worker API. The transport
handler dispatches it through `Async`/`StorageExecutor`; response publication also
runs on the worker. At most four inspection RPCs are in flight. Other-server requests
are ignored; executor saturation uses `REJECTED`, and capture/unexpected failures use
the existing `FAILED` RPC error convention.

The response uses the protocol 0.15.0 generated `MapsInspectResponseV1`,
`MapInspectInfoV1` and `MapInspectFindingV1` models and their `toPayload()` methods:

- `ok`: a decoded map, including maps with error findings. A tile-limit result is
  partial: it has `size-out-of-range`, and omits `spawnCount` and `coresByTeam`.
- `download_failed`: URL/HTTP errors, timeout or compressed download size limit.
- `invalid_map`: unreadable containers, unsupported versions/variants, invalid
  lengths/dimensions/content IDs, malformed metadata, checksum/truncation/inflation errors,
  a truncated or malformed shared building base, and inconsistent building footprints.

Downloads have one 30-second deadline across headers, at most five redirects and
the entire body. Declared size and streamed bytes are bounded. Compressed bytes are
hashed with SHA-256. Every returned temporary file is owned by a closeable handle;
both download failures and inspection completion delete it.

## Safety deviation from design 7.9

`SaveVersion.readContentHeader` and `MapIO.generatePreview` install a global temporary
content mapper. `readMap` resolves IDs through that mapper and constructs buildings;
preview also toggles `Vars.world`'s generating flag. A worker lock cannot protect
game-thread readers from those mutations. Inspection therefore uses an independent
decoder, rather than `MapIO.createMap` / `SaveVersion.readMap` with a collecting context.
It never calls `SaveIO.load`, `JsonIO`/engine object/content/entity deserializers or events, and
never writes `Vars.*`, content mappers, tiles, rules, teams, groups or asset caches.
Metadata uses Arc's pure syntax parser with depth, duplicate-field and single-root
checks. This accepts Arc minimal JSON's unquoted strings with spaces or quotation
marks, including campaign objective text and logic code, without resolving content.

The immutable registry is copied from `ContentLoader.getContentMap()`, which bypasses
the temporary mapper even if preview is currently running. Inspection and preview
share the Avaje `DetachedMapReadLock` bean. The lock serializes worker map reads; it
does not claim to fix preview's pre-existing interactions with live game readers.
Preview's generation behavior is preserved within the approved scope.

## Supported formats and bounds

The decoder supports Mindustry v160 save versions 1–13: legacy building headers,
short/int building chunks, old/new tile data, content aliases, unsigned team IDs,
and the version-specific placement of asset regions. It validates entity mapping,
team/build-plan and entity chunk framing without creating entities or resolving plan
references against the live world. Subclass building/entity payloads and UBJSON marker
payloads are opaque, bounded bytes: this is map inspection, not complete validation
of arbitrary gameplay serialization.

### Shared building base

Every building chunk of a block the registry knows as a native building is parsed up to
the end of the base that `BuildingComp.readBase` reads, inside the bounds of its chunk and
without touching the engine. A building is recorded, and a core counted, only after the
whole base was read; any short read is `invalid_map`.

- Versions 4–13 (`BuildingComp.readBase`): revision byte, float health, rotation byte,
  team byte. A set rotation high bit announces the base version byte; version >= 1 adds
  the enabled byte and version >= 2 the module flags byte. Without the high bit the
  module set comes from the block (`hasItems`, `hasPower`, `hasLiquids`, copied into the
  registry snapshot) and the item and liquid modules use legacy one-byte counts and ids. Then: item module
  (count, then id + int amount), power module (link count, int links, float status),
  liquid module (count, then id + float), time scale (two floats) for flag 16, last
  disabler (int) for flag 32, and the tail: one consume byte for version <= 2, two
  efficiency bytes for version >= 3, and the visibility long for version 4.
- Versions 1–3 (`LegacySaveVersion.readLegacyMap`): revision byte, unsigned short health,
  packed team/rotation byte (plus a team byte when the nibble is 8), the block's modules
  in legacy form, and the consume byte.

The detached reader is deliberately stricter than the engine where a value has only one
sane form: base versions above 4, module flag bits 6–7, boolean bytes other than 0/1,
negative module counts, non-finite health, unknown tile flag bits. Those are
`invalid_map`. Bytes after the base belong to the block's own `read` and stay opaque.

### Footprints and the core list

The published core list is meant to equal the cores that exist after the engine reads the
same file. `SaveVersion.readMap` places a centre with `Tile.setBlock`, which first clears
every tile of the footprint (`offset = -(size - 1) / 2`, clipped at the map edge; even
sizes extend right/up from the anchor) and thereby removes any building already there;
a later non-entity record or run does the same to a single tile. One tile is therefore
enough to erase a whole core. Sizes come from the registry (`core-shard` is 3x3).

The policy is to reject, not to reconcile. `invalid_map` is returned when

- two native building footprints overlap, whichever team they belong to;
- a later record or run that is not a part record lands on an earlier centre's
  footprint (versions 1–3 keep the engine's exception: air or the same block on an
  occupied part tile is skipped there);
- a part record lies outside every footprint, or names a different known block than
  the centre that covers it.

Rejecting is stricter than the engine, which loads such files with fewer cores: orphan
and mismatched part records are harmless to it. Reconciling would mean re-implementing
`Tile.setBlock`'s removal order for a file no editor writes; a rejected submission
simply has to be re-saved. A footprint clipped by the map edge and a core centre without
an entity chunk (placed for the derelict team) are valid and reported as the engine
places them. The PvP team count includes derelict cores, as `World`'s own check does.

### Known limit: surplus bytes in a native building chunk

`SaveFileReader.readChunk` hands the stream to the building's reader and never compares
what was consumed with the chunk length; only `skipChunk` (unknown blocks) uses the
length. Because subclass bytes are opaque here, a hostile file with otherwise valid
framing can hide tile records in the tail of a native building chunk: the detached reader
skips them with the chunk, the engine reads them as the next records. With one such
record claiming a "skipped" chunk, the engine swallows a core centre the detached reader
has already counted, and the map region length still matches.
`EngineCoreOracleTest.knownLimitSurplusBytesInANativeChunkCanHideTileRecordsFromDetachedReading`
pins this with a wall chunk. Closing it needs the exact `read` length of every block
class and revision, which is outside detached inspection. The check result is advisory
for staff; a map should still be loaded on a server once before it enters rotation.

Gameplay patches and embedded/external gameplay content assets are rejected with
`invalid_map`. Applying their changes would violate detached inspection, while
silently ignoring them could produce misleading core/road findings. Image, bundle,
sound and music asset framing is accepted without applying assets or fetching hashes.
Versions 1–3 with unknown block names are rejected because their unflagged building
format cannot safely be determined. In modern versions, missing names remain unknown
instead of being silently replaced with native content ID zero.

Configured defaults are 4 MiB compressed / 360,000 tiles. Independent safety ceilings
are 64 MiB compressed and inflated, 32 MiB per region, 1 MiB metadata, 4 MiB content
header, 1,000,000 allocated tiles, 256 metadata tags, 65,536 header names/region entries
and JSON nesting depth 64. Nested lengths must fit their containing regions. Counts,
dimensions and RLE runs are checked before tile arrays or entity reading. Oversized
maps stop after confirming metadata and map dimensions, so their remaining payload
is deliberately not inspected.

## Check policy

Requested profiles are used verbatim; absent profiles use `Rules.mode()`'s priority
(`pvp`, `editor`, `attack`, `sandbox`, `survival`). Tower Defence requires an explicit
`tower-defense` profile. Unknown profile names receive common checks only.

All profiles warn for `missing-name` / `missing-author` and error for
`size-out-of-range`, `requires-mods`, `unknown-content`, `no-core`. Required mod names
and missing/incompatible content names are included in diagnostics. `no-core` uses
the saved default team; `no-spawn` applies to survival and Tower Defence. PvP requires
two core teams (`pvp-single-team`); attack requires a wave-team core
(`attack-no-enemy-core`).

Tower Defence uses floors under spawn overlays as roads. Multiple floor types error
(`td-road-floor-mixed`); a configured floor mismatch warns
(`td-road-floor-unexpected`). A four-neighbour flood fill must reach a tile adjacent
to a default-team core footprint (`td-road-disconnected`, error); spawn tiles with
more than one road neighbour warn (`td-spawn-not-at-road-start`). Border checks count
unique non-road neighbours, excluding core footprints, against the configured wall
and tolerance (`td-border-wall-missing`, warning). The first five row-major missing
coordinates are reported, plus total count and the first x/y. Nonzero drop-zone
radius warns when configured (`td-drop-zone-radius`); waves OR waveTimer is info
(`td-waves-enabled`). There is no empty-waves validation; spawn-group count describes
saved metadata, before the engine's default-wave replacement.

Optional profile data is respected. Empty road floor accepts any single road type;
empty border wall disables the border check; `require_drop_zone_radius_zero = false`
disables the drop-zone check. Names, legacy aliases and numeric ID strings resolve
against the immutable registry. Details are deterministic, stripped of color/control
formatting and bounded to 300 UTF-16 units.

### Configuration

All keys live in the shared `secrets.toml` and are captured into the immutable
`InspectionSettings` on the game thread, per request.

| Key | Default | Meaning |
|-----|---------|---------|
| `maps.checks.severities` | `{}` | check id -> `error` / `warning` / `info`; replaces the default severity of that finding |
| `maps.checks.profiles.<id>.min_width`, `max_width`, `min_height`, `max_height` | `0` | inclusive size bounds in tiles for maps checked under profile `<id>`; `0` = no bound |

With nothing configured the severities above and the global `max_tiles` apply unchanged.
The check ids are exactly the sixteen finding codes of this section. A violated bound is
reported as `size-out-of-range` with the violated key in the detail
(`5 x 5 width < min_width=6`); it does not make the inspection partial, and it cannot
raise `max_tiles` or the parser ceilings. `<id>` is any profile name (`pvp`, `survival`,
`attack`, `sandbox`, `editor`, `tower-defense`); the road, border and drop-zone keys act
only under `tower-defense`. The `tower-defense` defaults are always present, also when
the file defines another profile's table only; switch its checks off with the empty
strings / `false` above.

An invalid value never stops start-up. It is repaired when the config is loaded and
reported once per value as a `[Config] maps.checks: ...` warning in the server log:

- unknown check id -> the entry is ignored;
- unknown severity (levels are trimmed and case-insensitive) -> the entry is ignored and
  the default severity applies;
- negative bound -> `0`, that bound is off;
- maximum below its minimum -> the maximum is raised to the minimum, so only that exact
  size passes.

A value of the wrong TOML type for a bound (`max_width = "wide"`) is a config load error
like everywhere else in `secrets.toml`. Unknown keys are ignored, as for every section.

Tests include a generated wire scenario for every finding, every save version,
adversarial lengths/truncations/inflation, local HTTP limits/deadlines/cleanup,
all 114 bundled v160 maps, every build-plan config wire type, typed and complete
local-download RPCs, Avaje wiring, concurrent inspection/
preview serialization, and concurrent live reads with an installed temporary mapper.
`BuildingValidationTest` truncates every base layout at every length and covers the
footprint rules with literal expectations. `EngineCoreOracleTest` is the independent
oracle for them: it reads the map region of the same generated bytes with the real v160
`SaveVersion.readMap` into a disposable headless world (test code only) and requires the
detached reader either to agree on every core and block or to reject the file.
No user-supplied production map, server, external download or staging/Discord workflow is exercised.

## File inventory

New production files, relative to `src/main/java/org/xcore/plugin/`:

- `service/map/MapInspectionService.java`
- `service/map/inspect/ContentRegistrySnapshot.java`
- `service/map/inspect/DetachedMapReadLock.java`
- `service/map/inspect/DetachedMsavReader.java`
- `service/map/inspect/InspectionSettings.java`
- `service/map/inspect/MapCheck.java`
- `service/map/inspect/MapChecks.java`
- `service/map/inspect/MapDownloader.java`
- `service/map/inspect/MapFinding.java`
- `service/map/inspect/MapInspectionResult.java`
- `service/map/inspect/MapRules.java`
- `service/map/inspect/MapSnapshot.java`
- `service/map/inspect/README.md` (this report)

Modified production files:

- `service/map/MapPreviewService.java` (inject shared lock)
- `event/transport/MapTransportHandler.java` (inspection responder)
- `service/network/MapsProtocolMapper.java` (typed inspection response mapping)

New tests, relative to `src/test/java/org/xcore/plugin/`:

- `service/map/MapInspectionServiceTest.java`
- `event/transport/MapInspectTransportTest.java`
- `service/map/inspect/MsavFixture.java`
- `service/map/inspect/MapChecksTest.java`
- `service/map/inspect/DetachedMsavReaderTest.java`
- `service/map/inspect/MapDownloaderTest.java`
- `service/map/inspect/BundledMsavInspectionTest.java`
- `service/map/inspect/BuildingValidationTest.java`
- `service/map/inspect/EngineCoreOracleTest.java`
- `service/map/inspect/InspectionPolicyTest.java`
- `config/MapInspectionConfigTest.java`

Existing tests updated only for the new constructor dependencies:

- `service/map/MapPreviewServiceTest.java`
- `service/map/MapCatalogTriggerTest.java`
- `event/transport/MapTransportHandlerTest.java`

New resources are `src/test/resources/maps/inspection/{fork,fortress,caldera}.msav`
plus `bundled-v160.zip` and their `README.md` provenance note. The original three
maps use save version 4. The full corpus has versions 2, 4, 5, 7, 8, 11 and 13;
versions 1–13 are additionally exercised by the generated wire fixtures.

The corpus has three deliberately unsupported maps: `default/canyon.msav` uses
unknown legacy block names whose building framing is ambiguous;
`serpulo/hidden/242.msav` and `serpulo/littoralShipyard.msav` contain gameplay patches.
Those fail closed with `invalid_map`; the other 111 decode completely and run both
inferred-profile and Tower Defence checks. Work remains uncommitted on `feat/cases`;
the existing P0/P1/P2/P3 edits were retained.

Validation after the review corrections (2026-10-13): `./gradlew test --console=plain`
passed in 1m 35s. The 296 JUnit XML reports total 2,379 tests, zero failures,
zero errors and zero skips (2,252 before the corrections). P4 contributes 318 tests:
74 reader, 35 building base/footprint, 31 engine oracle, 25 checks, 11 policy,
6 configuration, 5 download, 114 bundled-map, 8 service/concurrency/Avaje and 9 RPC
tests. Targeted tests passed before this full run. `git diff --check` and a whitespace
check of new source files passed. The durable review handoff is at
`/home/ospx/workspace/xcore/.claude/cases-p4-handoff.md`.
