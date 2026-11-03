---
name: ship
description: Release an XCore component and get it running in production. Use when a change is merged and has to reach players, for "release", "ship", "выпусти", "выкати", or when bumping a version of XCore, TileLogger, a library or the protocol, deploying the bot, the gateway or the website, or putting a new plugin jar on the game servers.
---

# Ship

Merged code reaches production in up to three steps: release, deploy, rollout.
Do the ones the change needs, in this order. Ask the owner for the SSH host
of the game server; it is not stored in this repository.

| Changed | Release | Deploy | Rollout |
|---|---|---|---|
| XCore, TileLogger | yes | | yes |
| a library, the protocol | yes | | via XCore or a satellite |
| the bot, the gateway, the website | | yes | |

If the protocol changed, read its `CHANGELOG.md` before anything ships: a
breaking change says which side has to be upgraded first.

## 1. Release

Each component has its own version. Find where it lives in the Releasing table
of the root `README.md`.

1. Bump the version in a pull request. For XCore, move the `[Unreleased]`
   entries of `plugins/xcore/CHANGELOG.md` under the new version with today's
   date. For the protocol, add a `## X.Y.Z` section to `protocol/CHANGELOG.md`.
2. After the pull request is merged, tag the merge commit and push the tag:
   `git tag xcore/v5.4.0 <sha> && git push origin xcore/v5.4.0`. Tags are
   `xcore`, `tilelogger`, `flubundle`, `cloud-mindustry`, `xcore-ui`,
   `testkit` or `protocol`, then `/vX.Y.Z`.
3. Watch the Release workflow (`gh run watch`). It fails if the tag and the
   build version differ; fix the version, delete the tag and tag again.
4. Check the result: `gh release view <tag>` lists the jars; libraries appear in
   `https://maven.x-core.org/releases/<group path>/<artifact>/maven-metadata.xml`,
   the protocol also on PyPI.

Satellites (xcore-sentinel, HexedCore-plugin, AdminTools) are not released
here. Renovate opens an "update xcore core" pull request in each of them after
a release; merge it and release the satellite in its own repository.

## 2. Deploy

The bot, the gateway and the website are deployed from `main`, not released:

```sh
ops/deploy <ssh-host> bot|gateway|web|all
```

`ops/README.md` describes the script, the server layout and rollback.

## 3. Rollout of plugin jars

Game servers run in tmux on the same host. Before each start, `~/server-start.sh`
copies into the server's `config/mods`:

- every file in `~/mods`: `XCore-plugin.jar`, `TileLogger-release.jar`,
  `xcore-sentinel.jar`;
- the files from `~/plugins` that the server lists in `config/shared-mods.txt`
  (mode plugins such as `TheSiege.jar` or `hexedcore-plugin.jar`).

So a rollout means replacing the jar in `~/mods` or `~/plugins` under the name
the servers already use. Each server picks it up on its next restart.

```sh
tag=xcore/v5.4.0                      # tilelogger/v2.1.3 → asset TileLogger-linux-x64.jar
asset=XCore-plugin-5.4.0.jar
target=mods/XCore-plugin.jar         # relative to the server home; TileLogger: mods/TileLogger-release.jar
name=${tag%%/*}-${tag##*/v}

ssh <ssh-host> bash -s -- "$tag" "$asset" "$target" "$name" <<'EOF'
set -euo pipefail
tag=$1 asset=$2 target=$HOME/$3 name=$4
new=~/updates/$(date +%Y%m%d)-$name
backup=~/backup_plugins_$(date +%Y%m%d-%H%M%S)-$name
mkdir -p "$new" "$backup"
curl -fsSL -o "$new/$asset" "https://github.com/XCore-mindustry/xcore/releases/download/$tag/$asset"
cp -p "$target" "$backup/"
install -m 644 "$new/$asset" "$target"
sha256sum "$target"
echo "rollback: install -m 644 $backup/$(basename "$target") $target"
EOF
```

Do not restart game servers yourself: a restart drops the players on them. Tell
the owner the rollout is in place and which servers run the plugin; they
restart them when it suits. After a restart, the `sha256sum` of
`~/servers/<name>/config/mods/<jar>` matches the one printed above.
