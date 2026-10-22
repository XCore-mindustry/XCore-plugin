# Real-client menu render

Opens server menus in the official Mindustry desktop client and checks how it lays them out.
The other parts of the testkit prove what the client *does* with a menu (`HeadlessMenuClient`,
the actual-client oracle tests); this proves what it *looks like*: a screenshot of every menu on
every screen it is checked for, and a report of what runs off the screen or out of its place.

Nothing here is shipped: it is a tool, compiled against the desktop release, not a library.

## Use

Requirements: Linux x86_64 with Docker, or with Java 25 and `xvfb-run` for `--host`. No display,
GPU, game server or network is needed while rendering.

```sh
render/render [--profiles phone,pc] [--out DIR] [--host] <input|dir>...
render/render selfcheck [--host]
```

An input is a `.packet` (a `MenuBuilderCallPacket` exactly as the server sends it) or a `.msui`
(the menu DSL, opened as a full-screen dialog). A directory stands for every input in it; a
`.msui` beside a `.packet` of the same name is taken for its readable copy and skipped.

Write `.packet` fixtures with `org.xcore.testkit.ui.MenuFixtures` (`testkit:ui`), capturing the
packet where the plugin hands it to the network, so the envelope (title, token, dialog flags) is
the one a player gets. `.msui` suits a menu that has no server behind it, such as the negative
controls in `selfcheck/`.

Results go to `DIR` (default `build/menu-render`), per input and profile:
`<input>-<profile>.png`, `.tree.txt` (every element with its position, size and preferred size)
and `.json` (client, framebuffer, scene, scale, findings). `<profile>.log` holds the client's log.

Exit status: 0 no findings, 1 findings, 2 an input could not be opened or never settled, the
client did not start with its profile, or the harness itself failed.

## Profiles

| Profile | Framebuffer | Scale | UI viewport | Mobile |
| --- | --- | --- | --- | --- |
| phone | 480×800 | 1 | 480×800 | yes |
| android1080 | 1080×2400 | 2 | 540×1200 | yes |
| pc | 1920×1080 | 1 | 1920×1080 | no |
| landscape | 800×480 | 1 | 800×480 | yes |

The framebuffer and scale are fixed before the fonts load, so one client renders one profile and
opens every input in turn: a run costs one client start (~10 s) per profile, then well under a
second per menu. The client checks it got the framebuffer and scale it was asked for. Profiles
model viewport geometry, not particular devices.

## Checks

Once the dialog's geometry has stayed the same for several frames and its actions are done:

| Code | Means |
| --- | --- |
| `STAGE_X`, `STAGE_Y` | an element past the screen |
| `DIALOG_X`, `DIALOG_Y` | an element past its dialog |
| `HSCROLL`, `PANE_CONTENT_X` | a scroll pane that scrolls sideways, or content wider than its pane |
| `TEXT_X`, `TEXT_Y` | text wider or taller than its label |
| `BUTTON_TEXT` | a label sticking out of the button that owns it |

A `TEXT_X` on a wrapped label that holds colour markup is often the client's own doing: Arc
places the first glyph after a colour tag without checking the width, so a coloured word at the
end of a line can stick out by up to a glyph instead of wrapping. Put the space before the word
inside the tag (`you have[accent] { $n }[]`, not `you have [accent]{ $n }[]`), and the whole coloured
run moves to the next line.

Vertical overflow inside a vertical scroll pane is allowed. The policy suits ordinary
axis-aligned menus, not intentional horizontal scrollers, rotated elements or decorative overflow.

Geometry does not prove wording, colour markup, touch targets or good design: look at the PNGs
too. Only the opening show of a menu is replayed; slot updates, clicks, scrolling, streamed images
and an Android keyboard still need checks of their own. No pixel golden images are compared.

`selfcheck` renders three menus that are broken on purpose and one that fits; it passes only if
each broken one is reported with its finding and the one that fits is reported clean. A launch
error or a timeout is not a caught menu. CI runs it on every push.

## Client

The release is pinned with its SHA-256 in `client.env`; the image and `--host` both read it.
`--host` caches the jar and the compiled renderer under `~/.cache/mindustry-menu-render/`.
