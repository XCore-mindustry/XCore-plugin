# Real-client menu checks

The menus of this plugin rendered in the official Mindustry desktop client, on phone, Android
and desktop screens, with a report of what runs off the screen or out of its place. The renderer
belongs to [mindustry-testkit](https://github.com/XCore-mindustry/mindustry-testkit/tree/main/render);
this plugin supplies the menus.

```sh
tools/ui-render/render                                   # every fixture, phone + android1080 + pc
tools/ui-render/render --profiles phone build/ui-render/packets/settings-de-profile.packet
tools/ui-render/render --host                            # Java 25 and xvfb-run instead of Docker
```

The script finds the testkit beside this plugin (`../mindustry-testkit`) or at
`$MINDUSTRY_TESTKIT`, exports the current code's menus with `./gradlew exportUiFixtures`, and
renders them. Results (PNG, element tree, JSON findings per menu and screen) are under
`build/ui-render/results/`; see the testkit's README for the checks, profiles and exit codes.

Fixtures are `settings-<locale>-profile|chat|language|badges` and `top-<locale>-crowded` for every
locale in `LayoutAssert.LANGUAGES`: a 40-character nickname, a 200-character description, a full
page of long names, badges, seasons and four-digit ranks, with real translations and the
production controllers. `MenuRenderFixtureTest` captures each `MenuBuilderCallPacket` where
`MenuService` hands it to the gateway and writes it with `MenuFixtures`; the export is opt-in and
does not run in `test`.

Keep `LayoutAssert` as the fast first check; this one is the proof on the real client.
