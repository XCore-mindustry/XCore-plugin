# Real-client menu checks

Requirements: Java 25 and Docker on Linux x86_64. No display, GPU, game server,
MongoDB or Redis is needed. The first run downloads the official Mindustry v160.7
desktop jar (verified by SHA-256) and builds an Xvfb/Mesa image. Rendering runs
with networking disabled and a fresh settings directory inside each container.

From the plugin root:

```sh
tools/ui-render/render settings-de-profile phone
tools/ui-render/render top-ru-crowded pc
tools/ui-render/render matrix
tools/ui-render/render selfcheck
```

Each command exports the current code's localized packets with
`./gradlew exportUiFixtures` and builds the renderer (both cached on repeat runs).
Fixtures are `settings-<locale>-profile|chat|language|badges` and
`top-<locale>-crowded`, for every locale in `LayoutAssert.LANGUAGES`.
Use bundle codes such as `uk_UA` and `pt_BR`. Settings uses a 40-byte nickname
and 200-character description; Top has a full page with long names, badges,
seasons and four-digit ranks. These are fixed test data, with real translations
and production controllers/layouts.

The fixture gateway captures the complete `MenuBuilderCallPacket` passed by
`MenuService`, including title, token and dialog flags. Every export asserts a
binary round trip and writes a readable `.msui` alongside the `.packet`.
The desktop client decodes that packet and calls its normal client handler,
opening the genuine `Menus.MenuDialog` with the release's fonts and styles.
The test process and fixtures are excluded from the server JAR; export tests
are opt-in and do not run in `test`.

| Profile | Framebuffer | Scl | UI viewport | Mobile |
| --- | --- | --- | --- | --- |
| phone | 480×800 | 1 | 480×800 (SMALL) | yes |
| android1080 | 1080×2400 | 2 | 540×1200 (NARROW) | yes |
| pc | 1920×1080 | 1 | 1920×1080 (WIDE) | no |
| landscape | 800×480 | 1 | 800×480 (WIDE) | yes |

The client checks the actual framebuffer and Scl before opening the packet.
Scaling is configured before fonts load. Profiles model viewport geometry;
they are not measurements of a particular Android device.

`matrix` renders six fixtures across phone, android1080 and pc (18 captures).
`selfcheck` requires three deliberately broken menus to fail with the expected
findings: a row whose cells exceed its width, a label narrower than its text,
and a label taller than its enclosing button.
A launch error or timeout does not count as a successful negative control.
To render every exported locale, run the same command for each fixture, or use
`docker run` with the arguments shown in `render` to reuse the prepared image.

Results are under `build/ui-render/`: `packets/` contains input packets/DSL;
`results/` contains PNG, element tree, findings, metrics and client logs.
Exit codes: 0 = no findings, 1 = geometry failure, 2 = harness failure.
Docker errors keep their own exit status; a client that never settles times out
after 45 seconds (124). Failed geometry checks still save screenshots.

The scanner waits for stable geometry and completed dialog actions, then checks
stage/dialog bounds, horizontal scroll overflow, glyph bounds and label bounds
inside buttons. Vertical overflow inside a vertical scroll pane is allowed.
This policy targets ordinary axis-aligned XCore menus, not intentional horizontal
scrollers, rotated elements or decorative overflow.

Keep `LayoutAssert` as the fast first check. Inspect PNGs as well: geometry does
not prove wording, colour markup, usable touch targets or good visual design.
This first version replays initial Show packets; slot updates, clicks, scrolling,
streamed images and Android keyboard/cutouts still need separate verification.
No pixel golden comparisons are used.
