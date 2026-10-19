# Chat Translation Redesign Spec

## 1. Motivation & Context

Chat translation grew feature by feature: a Google provider, then an
OpenAI-compatible provider, a fallback pipeline, a Redis cache, Redis metrics
and an LLM safety layer. Each piece works on its own, but the system as a whole
has no owner for the things players actually notice: **when** a message
arrives, **in what order**, and **whether it arrives at all**.

This spec records a review of the current implementation, the defects found,
and a target architecture with a phased migration.

### 1.1 Current flow

```
NetEventService (chat filter, game thread)
  └─ ChatMessageHandler.handle
       ├─ author.sendMessage(original)
       ├─ TranslatorService.translate(author, text)          // global chat
       │    ├─ group online sessions by translatorLanguage
       │    └─ per language: translate(text, "auto", lang, onResult, onError)
       │         ├─ TranslationCacheService.getAsync (Redis, 500 ms timeout)
       │         └─ TranslationFallbackService (providers in order)
       │              ├─ GoogleTranslationProvider   (virtual thread, 10 s)
       │              └─ OpenAITranslationProvider   (virtual thread, 15 s × retries)
       │         └─ async.main → send formatted + "(translation)" to the group
       └─ network.post(ChatMessageV1)
SocialController (/t) → TranslatorService.translateTeamChat (same shape, copy-pasted)
```

Translated recipients receive **nothing** until their language group's
translation completes or fails. Players with the translator off receive the
original immediately.

## 2. Findings

### 2.1 Defects (fixed in phase 1, this change)

| # | Problem | Impact |
|---|---------|--------|
| D1 | `GoogleTranslationProvider` shared one static `arc.util.serialization.JsonReader` across all translation threads. `JsonReader` keeps parse state in instance fields (`elements`, `lastChild`, `root`, `current`). | Concurrent translations (one per target language for every message) race: a request can return another request's result — a player gets a translation into the wrong language — or throw and fall through to the next provider. |
| D2 | `TranslatorService.translate(text, …)` ran its pipeline inside `CompletableFuture.thenAccept` without a terminal `exceptionally`. | Any exception thrown in the chain was swallowed and neither callback ran: the whole language group silently never received the message. |
| D3 | `metrics.minuteBucketsEnabled` was ignored; the helper that honoured it was dead code. `incrementGlobal` also computed the minute key twice and could `EXPIRE` a different bucket than it incremented at a minute boundary. | Config flag had no effect; occasional minute buckets without TTL. |

### 2.2 Architectural problems (phases 2–5)

**A1. Delivery waits on the slowest provider.** A recipient with the translator
on gets the message only after the pipeline finishes. With the default OpenAI
settings (`timeoutSeconds = 15`, `maxRetries = 1`, backoff up to 8 s) followed
by Google (10 s), the worst case is tens of seconds. During that time:

- the conversation is visibly out of order for translated players
  (later cached / short messages overtake earlier ones);
- with `preserveOriginalMessageOnFailure = false` a failed translation drops
  the message entirely for that group.

The plugin cannot edit a chat line once sent, so "wait for the translation"
and "deliver promptly" are in direct tension and need an explicit policy
(see §3.3).

**A2. No load control.** Every message fans out to one provider request per
distinct target language. There is no concurrency limit (unbounded
virtual-thread executor), no per-author budget and no in-flight
de-duplication. A spammer — or simply a busy server with many languages — can
hit provider rate limits (shared by everyone) and, for LLM providers, run up
cost. The mute check happens before translation, but rate limiting does not.

**A3. Wasted requests.** Nothing filters messages that do not need
translation: numbers, emoji, coordinates, `gg`, single words that are the same
in every language. Google's response already contains the detected source
language, but it is discarded, so a message already in the recipient's
language is still translated and only afterwards discarded by
`hasMeaningfulTranslation`.

**A4. Redis on the hot path for metrics.** One uncached translation for one
target language issues roughly 20 Redis commands (cache get/put plus
`HINCRBY`/`HSET`/`EXPIRE` for ~7 counters). Counters belong in memory with a
periodic flush.

**A5. Responsibilities are tangled.** `TranslatorService` resolves recipients,
formats chat lines, decides Foo's-client compatibility, records the case
evidence buffer (team chat only), orchestrates cache + pipeline and decides
fallback delivery. Global and team paths are near-duplicates that have already
diverged (team chat checks Foo's compatibility; global chat sends the 3-arg
variant to everyone; only team chat records `RecentChatBuffer` inside the
translator).

**A6. Callback API.** Providers and the pipeline use `Cons<TranslationResult>`
callbacks while the cache uses `CompletionStage`. The mix is what made D2
possible and makes timeouts, de-duplication and composition awkward.

**A7. Smaller issues.**

- The global path looks each recipient up with `Groups.player.find` inside the
  session loop — O(sessions × players) per message — although `Session.player`
  is already available (the team path uses it).
- The cache key's pipeline signature is `name:type`; changing the OpenAI
  `model` or prompt does not invalidate cached translations.
- The cache is keyed per server name although Redis is shared; identical
  messages across servers are translated separately.
- `PROTECTED_TOKEN_PATTERN` treats any `[...]` up to 40 chars as a colour tag,
  so bracketed words (`[help]`) are never translated by LLM providers; the
  Google provider gets no token protection at all and may mangle colour tags.
- Cross-server global chat (`ChatGlobalV1`) and Discord ingress are never
  translated, although they reach the same players.
- `Session.updateTranslatorLanguage` accepts any string; validation lives only
  in the command parser and UI.

## 3. Target Architecture

### 3.1 Components

```
ChatMessageHandler / SocialController / ChatTransportHandler
        │  ChatLine(author, text, channel = GLOBAL | TEAM | REMOTE | DISCORD)
        ▼
ChatDeliveryService                       (game thread; owns recipients + timing)
  ├─ resolves recipients per channel, groups by translatorLanguage
  ├─ sends originals immediately to translator-off recipients
  ├─ requests translations: ChatTranslationService.translate(text, targets)
  └─ applies DeliveryPolicy (deadline / late follow-up), formats via
     TranslatedChatFormatter, sends on the game thread
        ▼
ChatTranslationService                    (thread-agnostic; text → text)
  ├─ TranslationPrefilter        skip text that needs no translation
  ├─ TranslationAdmission        global concurrency + per-author budget
  ├─ in-flight single-flight     same (text, target) shares one future
  ├─ TwoLevelTranslationCache    L1 in-memory LRU → L2 Redis
  └─ TranslationPipeline         ordered providers, CompletionStage API
        ├─ GoogleTranslationProvider    (returns detected source language)
        └─ OpenAITranslationProvider    (supports multi-target requests)
TranslationMetrics                         (LongAdder counters, periodic flush)
```

Rule of thumb: **ChatTranslationService never touches players; ChatDelivery
never touches providers.**

### 3.2 Provider contract

```java
public interface TranslationProvider {
    String name();
    String type();
    boolean supports(String languageCode);
    CompletionStage<TranslationOutcome> translate(TranslationRequest request);
}

public record TranslationRequest(String text, String sourceLanguage, Set<String> targetLanguages) {}

public record TranslationOutcome(String detectedSourceLanguage,
                                 Map<String, String> translations,   // target -> text
                                 Map<String, TranslationFailure> failures) {}
```

- Single-target providers (Google) are adapted by fanning out internally; LLM
  providers translate all targets in one request with a JSON schema of
  `{"translations": {"<lang>": "..."}}` — one call instead of N.
- `detectedSourceLanguage` lets delivery skip recipients whose target equals
  the source and lets the cache store "already in language X" results.
- The pipeline falls back per target: targets that failed on provider 1 are
  retried on provider 2 only.

### 3.3 Delivery policy

Configurable, default `deadline`:

| Mode | Behaviour |
|------|-----------|
| `deadline` (default) | Wait up to `deliveryDeadlineMillis` (e.g. 1500 ms). Translation ready → one line `original (translation)`. Not ready → send the original now; if the translation arrives within `lateTranslationMillis` (e.g. 10 s) send it as a follow-up line `  ↳ translation`; otherwise drop it. |
| `original-first` | Always send the original immediately; translation, if any, follows as `↳` line. Lowest latency, two lines per message. |
| `wait` | Current behaviour (bounded by the pipeline timeout). Kept for compatibility. |

In every mode a failure delivers the original; `preserveOriginalMessageOnFailure`
is removed (dropping chat is never the right outcome).

Ordering: within one recipient, lines are delivered in message order. A
per-recipient-group queue holds a message until all earlier messages for that
group were sent (the deadline bounds the wait), so a cached reply can no longer
overtake the message it answers.

### 3.4 Load control

- `TranslationAdmission`: a semaphore of `maxConcurrentRequests` provider calls
  (default 16) plus a token bucket per author (e.g. 6 translated messages /
  10 s). Rejected requests complete immediately as "not translated" → original
  delivered.
- Single-flight map `(normalizedText, source, target, pipelineVersion) →
  CompletableFuture`, removed on completion.
- `TranslationPrefilter`: skip if the text (after stripping colour tags) has
  fewer than 2 letters, is only digits/punctuation/emoji, is a command, or is on
  a small configurable "universal words" list (`gg`, `ok`, `lol`, ...).

### 3.5 Cache

- L1: bounded in-process LRU (e.g. 2,000 entries, 10 min) — repeated phrases
  ("gg", "go b", "?") never leave the JVM.
- L2: Redis as today, but keyed by a **pipeline version** that includes model
  and prompt version, and shared across servers (no server name in the key).
- Negative entries: "source language == target" is cached too.

### 3.6 Metrics

`TranslationMetrics` keeps `LongAdder` counters and latency histograms in
memory; a scheduled task flushes deltas to the existing Redis hashes every
15 s in one pipelined batch. `trstats` reads local counters directly (no
blocking `join()` on the main thread) and the Redis totals for the
cross-server view.

## 4. Implementation Status

All phases are implemented. Where the code differs from §3, this section wins.

| Phase | Scope | Where |
|-------|-------|-------|
| 1 | D1–D3 fixes. | `GoogleTranslationProvider`, `TranslationMetricsService` |
| 2 | `CompletionStage` provider/pipeline API with per-target fallback; `ChatTranslationService` with prefilter, single-flight, admission control and the in-memory cache. | `localization/TranslationProvider`, `TranslationOutcome`, `service/TranslationFallbackService`, `translation/*` |
| 3 | `ChatDeliveryService` replaces `TranslatorService`: delivery modes, per-language lanes, late follow-up lines, Foo's-client handling for team chat only (as before), `RecentChatBuffer` recorded by `ChatMessageHandler` and `SocialController`. | `chat/ChatDeliveryService` |
| 4 | Detected language from Google (`[["text","en"]]`) and from LLM JSON; multi-target LLM requests with a per-target JSON schema; provider `signature()` (model, API mode, prompt version) in the cache key; Redis cache shared across servers (`xcore:translation:cache:v2:*`); metrics buffered in memory and flushed every 15 s. | `OpenAIRequestFactory`, `TranslationSafetyService`, `TranslationCacheService`, `TranslationMetricsService` |
| 5 | `translation.channels.remote_global` / `discord` route relayed chat through `ChatDeliveryService.deliverRemote`. | `event/transport/ChatTransportHandler` |

### 4.1 Decisions

- Default delivery mode is `deadline` with `deadline_millis = 1500` and
  `late_translation_millis = 10000`. Late translations arrive as
  `[lightgray]» Name: translation`; `»` (Latin-1) is used rather than `↳`,
  which the game font may not have.
- `preserve_original_message_on_failure` is removed. The config loader ignores
  unknown keys, so existing files keep loading; a failed translation always
  delivers the original.
- Cross-server and Discord translation are off by default.
- Colour tags and icons are stripped before translation (and before the cache
  key). Arc only strips colours it knows, so bracketed words such as `[help]`
  stay part of the text.
- The per-author budget is charged once per message that misses the in-memory
  cache, before joining another author's in-flight request, so a rate-limited
  author never makes another author's message untranslated.
- Admission rejects instead of queueing: a queued chat translation is
  useless by the time it would run.
- `max_concurrent_requests` is read at startup; the other settings apply on
  the next message after `xconfig` changes them.
- Ordering lanes are per target language, not per recipient. A team message
  and a global message for the same language share a lane; the wait is still
  bounded by the deadline.
