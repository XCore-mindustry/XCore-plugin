# Migrating from FluBundle 1.x to 2.0

2.0 separates the three jobs `Bundle` used to do:

- **catalog and formatting** stay in `Bundle` (`format`, `has`, `keys`, `variables`, `locale(player)`);
- **delivery to players** moves to `com.ospx.flubundle.mindustry.Messenger` / `Audience`;
- **messages as values** are `Text` (key plus arguments, rendered later).

## Removed API and replacements

| 1.x | 2.0 |
|---|---|
| `bundle.send(player, id[, args])` | `messenger.to(player).send(id[, args])` |
| `bundle.announce/infoMessage/toast/kick(player, ...)` | `messenger.to(player).announce/infoMessage/toast/kick(...)` |
| `bundle.setHud(player, id, args)` | `messenger.to(player).hud(id, args)` |
| `bundle.label(player, duration, x, y, id, args)` | `messenger.to(player).label(id, args, duration, x, y)` |
| `bundle.popup(player, duration, align, top, left, bottom, right, id, args)` | `messenger.to(player).popup(id, args, Popup.at(align).duration(duration).margin(top, left, bottom, right))` |
| `bundle.send(id, args)` and other broadcasts | `messenger.all().send(id, args)` |
| `bundle.context(player)` / `BundleContext` | `messenger.to(player)` for delivery, `bundle.localizer(player)` for formatting |
| `bundle.context(player, locale)` | `bundle.localizer(locale)` to format, then `player.sendMessage(...)` |
| `DefaultValueFactory`, `setDefaultValueFactory` | `MissingKeyPolicy`, `setMissingKeyPolicy` |
| `DefaultValueFactory.logMissing(f)` | `MissingKeyPolicy.logOnce(policy)` |
| `format(locale, id, args, DefaultValueFactory)` | `format(locale, id, args, MissingKeyPolicy)` |
| `formatStrict(..., DefaultValueFactory)` | `formatStrict(locale, id, args)` (uses the bundle's policy) |
| `Bundle.numArgs(a, b)` | `Args.of("a0", a, "a1", b)` |
| `bundle.defaultLocale` (field) | `getDefaultLocale()` / `setDefaultLocale(...)` |
| `bundle.defaultValueFactory` (field) | `getMissingKeyPolicy()` / `setMissingKeyPolicy(...)` |
| `Bundle.INSTANCE = ...` | `INSTANCE` is final; build your own `new Bundle(...)` for isolated use |
| `formatStrict` threw `RuntimeException` | it throws `IllegalStateException` |

`Bundle.args(...)` stays for call sites with many pairs; prefer `Args.of(...)` (up to six pairs)
or `Args.builder()`.

## New in 2.0

- `Text.of(key[, args])`, `bundle.format(locale, text)`, `text.render(bundle, locale)`,
  `localizer.format(text)`.
- `Messenger.of(bundle)` with `to(player)`, `to(players)`, `all()`, `team(team)`, `filter(predicate)`.
  Every `Audience` renders a message once per locale. `Audience.deliver(text, transport)` plugs in a
  custom transport (UI library, Discord bridge, tests).
- `Popup` value object instead of six positional `int`/`float` arguments.
- `Localizer.has(key)` and `Localizer.bundle()`.
- `Args.builder()` with `put`, `putIf`, `putAll`.
- `compiler.KeyConstantsGenerator`: generates `String` constants for the keys of the base locale
  (see the README for a Gradle task).

## Shared bundle in Mindustry plugins

Plugins that compile against a plugin which shades FluBundle (for example XCore) use the FluBundle
classes inside that jar at runtime. Move to 2.0 together with that plugin's release that ships 2.0;
until then keep the 1.x calls.
