# Envelopes

Envelope definitions describe wire-level metadata shared across events, commands, RPC messages, and dead-letter records.

## Canonical Policy
- Envelope fields use **snake_case**.
- Payload remains inside `payload_json`.
- Canonical message identity lives in the envelope via:
  - `message_kind`
  - `message_type`
  - `message_version`
- Payloads may also carry their own `messageType` and `messageVersion` during the migration period.

## Time Rules
- `created_at`, `expires_at`, `responded_at`, and `failed_at` use epoch milliseconds.

## Transition Notes
The current Redis transport in `XCore-plugin` uses older field names such as:
- `event_type`
- `rpc_type`
- `event_id`
- `request_id`
- `requested_by`

The canonical target model in `xcore-protocol` intentionally converges these into a smaller, more explicit set of envelope fields. Compatibility adapters can map current Redis envelopes into this canonical shape during migration.
