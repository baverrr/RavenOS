# RavenOS glass theme

Settings (all its sub-pages, its tabs and its search page) and the keyboard share one glass look, inspired by
Apple Home: a soft colored backdrop, frosted grouped cards, and no hard bars between the
toolbar, the content and the keyboard. Ten color variants are kept in the tree. The Echo
Show 8 build ships **Obsidian** only.

> [!TIP]
> To build a different variant, set `RAVENOS_GLASS_THEME` (a folder name under `themes/`):
>
> ```bash
> RAVENOS_GLASS_THEME=tide bash vendor/ravenos/build-ravenos.sh Settings
> ```

Mockups of all ten, with the original plan: [docs/glass-theme-variants.html](docs/glass-theme-variants.html).

## Variants

| Folder     | Name            | Look                                                          |
| ---------- | --------------- | ------------------------------------------------------------- |
| `aurora`   | Midnight Aurora | Navy with teal and violet glows, cyan accent; the source default |
| `dusk`     | Raven Dusk      | RavenHome's slate with an amber glow, amber accent             |
| `hearth`   | Hearth          | Warm brown and ember, closest to the Apple Home wallpaper      |
| `frost`    | Frost           | Light: pale sky and lilac, milky glass, dark text              |
| `obsidian` | Obsidian        | **Shipped.** Near-black, very faint glass, amber only on toggles and enter  |
| `tide`     | Tide            | Deep teal water, aqua accent, rounder cards and keys           |
| `sage`     | Sage            | Muted grey-green, matte glass, mint accent                     |
| `rosewood` | Rosewood        | Plum and mauve, rose accent                                    |
| `graphite` | Graphite        | Grey only, white accent, highest contrast                      |
| `daynight` | Day / Night     | Frost in light mode, a dimmed warm Dusk in night mode          |

## How it fits together

```
vendor/ravenos/ravenos.mk
  RAVENOS_GLASS_THEME ?= obsidian
  PRODUCT_PACKAGE_OVERLAYS += vendor/ravenos/themes/$(RAVENOS_GLASS_THEME)/overlay
                                   │
          ┌────────────────────────┴────────────────────────┐
          ▼                                                 ▼
packages/apps/Settings/res/values/raven_glass.xml   packages/inputmethods/LatinIME/java/res/values/raven_glass.xml
  (Aurora defaults, from the Settings patch)          (Aurora defaults, from the LatinIME patch)
```

- **Patches carry the code.** `patches/packages/apps/Settings/0002-*.patch` and
  `patches/packages/inputmethods/LatinIME/0001-*.patch` add the theme itself, with Aurora values
  in `res/values/raven_glass.xml`. Apply them with `patches/apply.sh` as usual.
- **Variants are color overlays.** Each `themes/<variant>/overlay/` replaces the
  `raven_glass.xml` files of Settings, SettingsIntelligence (the search page) and LatinIME at
  build time. Only the selected folder is added to
  `PRODUCT_PACKAGE_OVERLAYS`, so the other nine never reach the image. That folder is also
  excluded from RRO enforcement, so its colors are compiled into the APKs rather than
  shipped as separate runtime overlays.
- **The search page** is its own app, `packages/apps/SettingsIntelligence`, patched by
  `patches/packages/apps/SettingsIntelligence/0001-*.patch`: the same backdrop, a glass search
  pill, and the results in one glass card in the same centred column as Settings.
- **One palette list.** `themes/generate.py` holds all ten palettes and writes every overlay.
  Edit a palette there, never the generated XML, then run:

  ```bash
  python3 vendor/ravenos/themes/generate.py
  ```

  `--check` reports overlays that no longer match the script.

### What the Settings patch changes

- Settings uses a dark base theme with every color taken from `raven_glass.xml`, so it keeps
  the glass look whatever the system dark-theme switch says (except `daynight`, whose colors
  change with it).
- `raven_backdrop` is the window background for every page. Status bar, navigation bar and
  action bar are transparent, so one backdrop runs edge to edge.
- `GlassGroupDecoration` draws each preference group as a rounded glass card, with hairline
  separators that start under the title. Category titles and footers sit outside the cards.
  Content is centred in a column at most 760 dp wide. The homepage is split into cards at
  Apps, Privacy, Accounts and System.
- The homepage search bar is a glass pill. The switch bar ("Use Wi-Fi") is a glass card that
  tints with the accent when on.
- Personal/Work tabs are a segmented pill control.
- Dialogs use a solid `raven_surface`. With no live blur, see-through dialogs are hard to read.

### What the LatinIME patch changes

- Adds keyboard theme **RavenOS Glass** (`THEME_ID_RAVEN_GLASS = 6`) and makes it the default.
  Material Light, Material Dark and Follow system are still in the keyboard's theme setting.
- Glass letter keys, darker functional keys, a pill spacebar, a solid enter key, and a glass
  key-press bubble and long-press panel.
- The keyboard background is the bottom band of the Settings backdrop.
- A remote can type on it: while the keyboard is shown, the D-pad moves an accent-outlined
  focused key and OK types it (patch `0004`). Touch hides the outline again.

## Known limits

- **No live blur.** Android 11 has no backdrop blur (it arrived in Android 12), so the frost is
  baked into the gradient. Content doesn't blur as it scrolls under the toolbar.
- **Same glow layout for every variant.** Variants change colors and corner radii; the
  position and size of the three backdrop glows are shared.
- **Keyboard icons stay white.** Shift, delete and enter icons come from the stock dark
  keyboard. They read well on the dark variants; on `frost` and on `daynight` by day they are
  low contrast.
- **Enter key.** The enter icon is white, so each variant's enter key uses a deeper shade of
  its accent rather than the bright accent itself.
- **Pages with their own theme stay stock:** setup-wizard pages, lock and fingerprint screens,
  and slice panels.
- **Saved keyboard choice wins.** If a theme was picked in the keyboard's settings before, that
  choice is kept; select RavenOS Glass there once.

## Touch glow

When the screen is touched, the warm glow in the backdrop moves to the finger and follows it
around, then fades where the finger lifted. Part of the theme as a whole; Obsidian's is a warm
amber.

It ships in Settings, its search page, the keyboard, RavenHome, EchoParts, LineageParts,
PermissionController and DocumentsUI, each with a copy of the same class (see CHANGELOG.md).
Quick Settings doesn't have it.

How it works:

- **Behind content, per app.** `GlowBackdrop` (canonical copy in Settings,
  `widget/GlowBackdrop.java`) draws the glass backdrop plus one movable glow. A pass-through
  `Window.Callback` shows it the window's touches without consuming them, so taps still reach
  buttons. The glow eases toward the finger (40 ms), fades in over 80 ms, and fades out over 600 ms after lift. The
  keyboard wraps its own background in it and feeds it from `MainKeyboardView.onTouchEvent`.
- **Colors from the variant.** Each app's `<prefix>_touch_glow` (default Aurora `#8814B8A6`) is
  overridden per variant by a `touch_glow.xml` that `generate.py` writes for the apps in
  `TOUCH_GLOW_APPS`. A transparent color turns the glow off. An app joins the list only once
  its own `res/values` defines the color: framework-res and RavenHome build without
  `--auto-add-overlay`, so an overlay can't add a color the app lacks.
- **Why not one system-wide overlay.** An overlay window from SystemUI could follow touches in
  every app, but it would draw *over* the content, not behind it, and catching touches system-
  wide needs privileged input monitoring.
- **Cost.** One radial gradient, built once per window size and moved with the canvas, redrawn
  while a finger is down; nothing redraws otherwise. The animation stops if the window or
  keyboard goes away mid-touch.
