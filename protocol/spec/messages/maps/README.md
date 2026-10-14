# Maps RPC Messages

Maps request/response contracts belong here.

## Canonical V1 Messages
- `maps.list.request` — request the current custom map inventory for one server.
- `maps.list.response` — return canonical map entries for one server.
- `maps.remove.request` — request removal of one `.msav` map file from one server.
- `maps.remove.response` — return the server result for a remove request.
- `maps.upload.request` — upload maps and await per-file installation/attribution results.
- `maps.upload.response` — report installed, failed, superseded or rejected files in request order.
- `maps.inspect.request` — inspect one `.msav` without installing it; an absent profile is inferred from map rules.
- `maps.inspect.response` — return inspection status, optional map metadata and open-code findings.
- `maps.load.command` — command one server to download and load uploaded `.msav` files.

## Field Policy
- Payload fields use camelCase.
- Legacy aliases such as `file_name` and legacy upload key `urls` are consumer migration concerns, not canonical protocol fields.
- Map filenames are represented as `.msav` basenames, not paths.
