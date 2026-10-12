# Discord map submission attribution

A map's file author and Discord submission are independent. `author` remains the value
embedded in `.msav`, used by existing map identity and game statistics. An uploader's
choice of creator is a declaration, not verified authorship.

`/map upload` records the interaction user's Discord ID and display name automatically.
The optional `creator` user selector applies to every attached file (up to three). Maps
by different creators should be sent separately. Omission does not infer the uploader
as creator. Names are snapshots at submission time; IDs remain usable after a rename,
leaving the guild, or removal of a local game-account link. No Discord membership
lookup or game-account linking is required to render either interface.

The protocol adds optional `discordUpload` to each `MapFileSourceV1` and `MapEntryV1`:

```json
{
  "uploadedBy": {"discordId": "123456789012345678", "discordUsername": "Reviewer"},
  "uploadedAt": 1791806400000,
  "creator": {"discordId": "234567890123456789", "discordUsername": "Mapper"}
}
```

`uploadedAt` is Unix epoch milliseconds. `creator` is omitted when unspecified;
`discordUpload` is omitted for old or non-Discord submissions. The same submission
snapshot accompanies all attached files, while each has its own file author.

After downloads finish, the server reloads the map registry on the game thread. Only
successfully written files present in that registry are attributed. It captures their
names, file names, authors and current game mode before dispatching storage work.
The `maps` document stores an optional `discord_upload` POJO and the SHA-256 of the exact
bytes. Original `.msav` bytes and embedded tags are unchanged. File-name lookup takes
precedence over legacy display-name aliases, and an alias match for a different file
cannot expose that file's Discord submission in the Discord map list.

Installation writes are serialized per exact filename on the HTTP workers. Each
successful write gets an installation token; late batches and queued metadata work
must still match that token, even when a newer submission used identical map bytes.
Database writes for one file run in order, while other files remain independent.
Failed storage writes do not block their successors.

Submission updates write only metadata, preserving concurrent ratings and telemetry.
A first submission creates the map record for the current game mode. Attribution updates
apply to every existing statistics partition of the file; reads and newly created game-mode
records can inherit attribution from another partition without inheriting its statistics.
Reupload replaces the latest submission, including clearing an omitted creator. Loading the same file
through a legacy command without attribution clears its previous Discord attribution.
Failed downloads are not attributed. Duplicate file names in one transport batch use
the first source. Existing records have no fabricated history or automatic backfill.
Consolidation into `MapEntity` retains the most recent non-null submission. Write
lookups and updates use exact physical filenames, keeping case-only filenames
distinct. Case-insensitive aliases remain a read convenience only when unambiguous.
Both game interfaces verify the installed filename before displaying attribution;
statistics resolved through another filename's display alias cannot expose its
submitter or creator.

The existing `maps` collection partitions records by file name and game mode. This
change keeps statistics partitioned while treating submission attribution as file-level
metadata: it does not add a submission audit log or establish new map identity rules.
Database failures are logged and do not invent attribution;
the map can still be available while metadata persistence has failed.

In the reactive `/map` dialog, the file author is identified in the header and the
submission appears below the map description. It includes the declared creator when
present, the submitter, their stored IDs, and the upload date in UTC. The legacy menu
shows the same attribution block. Labels exist in all nine bundles. Long names are
sanitized and wrapped for small screens.

Discord `/map list` retains its sorting controls and shows file author, declared
creator, submitter and upload date. Stored names link to Discord profiles without
sending mentions. Markdown and mention characters are escaped. Pages contain at most
15 entries and are packed to keep descriptions below Discord's 4096-character limit.
The upload response says "Queued", because the command enqueues work and does not
acknowledge successful installation.

## Validation and release

Consumers pin protocol 0.13.0. The bot uses the annotated `v0.13.0` tag and `uv.lock`
locks its peeled commit; the plugin uses `org.xcore:xcore-protocol-java:0.13.0`.
Publish that protocol release before deploying either consumer. No database migration
or backfill is required.

Rollout order:

1. Publish `xcore-protocol` 0.13.0 and verify the Java artifact and Python package.
2. Pause the Discord bot's map commands while upgrading the map servers.
3. Deploy the updated plugin, then restart the updated bot; its normal slash-command
   sync publishes the optional `creator` parameter.
4. Upload a test map and check attribution with `/map` and Discord `/map list`.

Older strict decoders reject the new fields, while 0.13.0 decoders accept old payloads.
Coordinate the server and bot rollout instead of running the new uploader against an
old server. Stored map attribution does not require rollback of a database migration.

Normal validation after the release (or after installing the release artifact in Maven
Local and preparing the exact tagged Git revision for the bot):

```sh
# xcore-protocol
uv run python -m generators check
uv run pytest
./gradlew build validateRelease -PxcorePublishVersion=0.13.0

# XCore-discord-bot
uv sync --locked --all-groups
uv run pytest
uv run ruff check .

# XCore-plugin
./gradlew validateCi
```

For further development against an unpublished sibling protocol checkout, the optional
`gradle/local-protocol.init.gradle` init script remains available. Release validation
and ordinary builds do not depend on it.
