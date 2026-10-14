# Cases Messages

## Canonical V1 Messages
- `cases.report.submitted` — durable report event with player references and captured evidence. Deduplicate on `submissionId`.
- `cases.notice.command` — wake up delivery of a durable notice stored in `case_notices`; contains no rendered text.
- `cases.evidence.request` — request live evidence for one player from one server.
- `cases.evidence.response` — return online state and captured evidence, allowing empty chat and an absent block summary.

## Field Policy
- Payload fields use camelCase.
- Report `reporter` and `target` reuse `PlayerRefV1`; their `ip` field must be omitted. The report schema enforces this restriction.
- Report `reason` and map inspection finding `code` remain open strings constrained by patterns.
- Chat PIDs follow the shared signed PID policy, including zero and negative values.
- Optional evidence request bounds are `chatLines` 1–30 and `windowSeconds` 60–1800.
- JSON Schema remains authoritative for all bounds, formats and contextual restrictions; generated models enforce the repository's supported subset.
