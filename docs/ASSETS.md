# Fortnite assets

## Where to get art

**Use [fortnite.gg/assets](https://fortnite.gg/assets) — "Beyond Assets" — first.** Any UI
element or icon this mod needs should be taken from there rather than redrawn by hand or
traced out of a screenshot. Its `Icons`, `UI`, `Device Icons`, `Graphics` and `Mini-Maps`
categories carry the real in-game art, so the accurate version is normally one download
away.

If something genuinely is not there, fall back in this order: the Fortnite Wiki (below),
then FModel against an installed copy of the game.

Record the source of anything you add in the table below.

## Current state

The real Fortnite ping sounds and marker icons are **already installed**. Nothing here
needs doing unless you want to change or add something.

| Asset | Source | In the mod as |
|---|---|---|
| `Marking (Neutral)` | Fortnite Wiki | `sounds/neutral.ogg` |
| `Marking (Consumable)` | Fortnite Wiki | `sounds/item.ogg` |
| `Marking (Enemy)` | Fortnite Wiki | `sounds/enemy.ogg` |
| `Marking (Enemy - Alt)` | Fortnite Wiki | `sounds/enemy_alt.ogg` |
| `Marking (DBNO)` | Fortnite Wiki | `sounds/dbno.ogg` |
| `Marking (Scan - v31.00)` | Fortnite Wiki | `sounds/scan.ogg` |
| `Marker` (the white pin) | Fortnite Wiki | `textures/ping/marker.png` |
| Eliminations skull | extracted from a capture, see below | `textures/ping/skull.png` |
| Players bust | extracted from a capture, see below | `textures/ping/person.png` |
| Player pointer | extracted from a capture, see below | `textures/ping/arrow.png` |
| Shield glyph | Fortnite Wiki | `textures/ping/hud_shield.png` |
| Health cross glyph | Fortnite Wiki | `textures/ping/hud_health.png` |

### Why the three HUD glyphs are extracted, not downloaded

The order in [AGENTS.md](../AGENTS.md) is Beyond Assets first, then the wiki, then FModel.
That order was followed and all three steps failed for these glyphs specifically:

1. **`fortnite.gg/assets` does not hold the in-game HUD glyph atlas.** It is a creator asset
   library. Searches for `skull`, `elimination`, `elims`, `players`, `hud`, `marker`,
   `minimap`, `compass`, `waypoint`, `ping` and `crosshair` return cosmetic art —
   stickers, badges, backblings — not the HUD icons. The `Icons` and `UI` categories are
   creator icons and overlays.
2. **The wiki is Cloudflare-gated** for automated fetching, and the file it does host,
   `Eliminations - Icon - Fortnite.png`, is a progression badge rather than the HUD skull.
3. **FModel needs an installed copy of the game** plus the current AES key, which is not
   available here.

So they were extracted pixel-exactly from a Fortnite capture: threshold the white glyph,
keep the largest connected blob (two glyphs overlap in frame — and note the *bust is two
blobs*, a head and a body that never touch, so that one keeps every significant component
rather than only the largest), fill enclosed holes so the skull's sockets stay punched, and
crop to the content box. The results cross-check — the pointer came out 12×16 against an
independently measured 12×17, and the skull 27×32 against a measured 19×22 *box*.

They are then **upscaled 4× with smoothing** before shipping, so that drawing them at their
real size averages four source pixels per output pixel instead of dropping three of them —
which is the difference between an icon that reads as low-resolution and one that reads as
drawn. Each `.png.mcmeta` sets `blur` and `clamp`, so they sample with linear filtering
rather than the nearest-neighbour default.

**This is not a lossy trace.** At Minecraft's GUI scale 3 the drawn sizes are *smaller*
than the extracted ones, so the source has more resolution than the drawing uses. If
higher-resolution originals are ever obtained, they can simply replace these files — the
renderer only needs the asset's pixel size, which is a one-line constant per texture.


They came from the wiki's [Marking](https://fortnite.fandom.com/wiki/Marking) page,
which hosts the marker audio and icons as public files. The audio needed no conversion
at all — it is already Ogg Vorbis, which is exactly Minecraft's format. Five of the six
tones are mono; `scan` was stereo and was downmixed to mono.

Note the wiki serves PNG files transcoded to WebP, so the icons were converted back to
PNG with ffmpeg. Any image you drop in here must be a genuine PNG.

### One pin, tinted

The mod ships **one** marker image: the white pin, cropped to its content box and
re-centred on a square 128×128 canvas so the tail tip sits exactly at bottom-centre.
The renderer anchors each icon by its bottom edge, so the tip lands precisely on the
marked point and the pin stands over it.

Colour is **not** baked into the artwork. The white source is tinted at draw time using
Minecraft's colour-modulating `blit`, which means:

- changing any marker's colour is a one-line edit in `PingType` — no new art;
- the tint's alpha component also carries the fade, so occluded markers now dim
  properly instead of only fading their label.

Colours are **not** per-type. This is a Minecraft-meets-Fortnite build, so a marker
takes its author's **locator bar colour**, whatever that happens to be. Vanilla derives
that colour from the player's UUID on the fly:

```java
ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9F)
```

The mod calls those same two vanilla functions, so a player's ping is always pixel-identical
to their pip on the locator bar, with no palette to keep in sync and nothing to configure.
It is also why two players can end up on the same colour: only 24 bits of hash survive, so
collisions are expected rather than a bug.

Because the colour is a pure function of the UUID, it never travels over the wire — the
receiving client re-derives it from the sender's UUID.

Enemy and danger markers are currently forced red like Fortnite's danger marker:
`PingColors.THREAT_OVERRIDE` is `0xFFE8383D`. Set it to `0` to let threats wear their
author's own colour like every other marker.

The wiki also hosts the older coloured diamond-and-tail markers
(`Marker (Old Blue)`, `Marker (Old Red)`, `Marker (Old Purple)`, `Marker (Old Yellow)`,
`Marker (Old Green)`). They are not used, but they are where the squad palette above
comes from if you would rather match them exactly.

## Extracting more yourself

If you want assets the wiki does not host — other UI art, the wheel icons, more sounds
— you need **FModel**, a free standalone Windows app, plus the current Fortnite AES
key that the datamining community publishes on every patch. Point it at:

```
C:\Program Files\Epic Games\Fortnite\FortniteGame\Content\Paks
```

For sounds and textures you can **skip the .usmap mappings step** — that is only needed
to read Blueprint properties, and it is the fiddliest part of the process.

Fortnite's marker sounds have stable internal names, so search these exactly:

| Event | Fortnite asset name |
|---|---|
| `neutral` | `Fort_UI_Sight_Neutral_01` |
| `item` | `Fort_UI_Sight_BigPositive_03` |
| `enemy` | `Fort_UI_Sight_Enemy_03_Alt` |
| `enemy_alt` | `Fort_UI_Sight_Enemy_08` |
| `dbno` | `Fort_UI_Sight_DBNO_02` |
| `scan` | `MarkingScan_Activate` |

Also nearby if you want them: `HUD_Marked_Opponent_Caret_Placement_01`,
`HUD_Marked_Targeted_By_Opponent_01`.

FModel exports `.wav`. Convert with:

```
ffmpeg -i neutral.wav -ac 1 -c:a libvorbis -q:a 5 neutral.ogg
```

## Font

Fortnite's UI font is the **Burbank** family by House Industries. The styles the game
uses are `Burbank Big Condensed Black` for headings and `Burbank Small Medium/Bold/Black`
for HUD text. A community archive of the extracted game fonts exists as
`github.com/Djihads80/FortniteFonts` — third-party, not affiliated with Epic, and the
fonts are commercially licensed, so private use only.

The archive is the easier route; pulling a usable `.ttf` out of a pak is fiddly.

Drop it at:

```
src/main/resources/assets/ping/burbank.ttf
```

**Not wired up yet.** Ping labels currently use Minecraft's default font. Wiring it in
means adding a font provider at `assets/ping/font/ping.json` pointing at that file
and passing the font to the label draw call. Scoped to ping labels only, so it will not
repaint the rest of the game's text.

## Reference material (not shipped)

These are **not** in the mod. They are the crops the health/shield and feed numbers in
[FIDELITY.md](FIDELITY.md) were measured from, kept so those numbers can be re-derived or
replaced instead of taken on trust. They live in [docs/reference/](reference/).

Source: the **Fortnite Wiki** at `fortnite.weirdgloop.org` (the Weird Gloop mirror — the
Fandom original returns 403 to automated fetches). Fetched October 2026. Wiki content is
**CC BY-NC-SA 3.0**, and Fortnite art is Epic's, so these are for private measurement use
and must not ship in the jar. Every file below should be treated as disposable: if a better
capture arrives, replace the file and update the numbers it fed.

| File | What it is | Why it is here |
|---|---|---|
| `fn-hud-chapter7-health-bar.png` | Chapter 7 health + shield meter | the primary measurement target for the health/shield HUD |
| `fn-hud-c3s2-health-shield-meter.png` | Chapter 3: Season 2 meter | the pre-Chapter-7 baseline, and the second sample that exposes the cross-crop scale problem |
| `fn-hud-chapter1-health-shield-bar.png` | Chapter 1 meter | history only; not measured in depth |
| `fn-hud-chapter2-early-health-shield-bar.png` | Chapter 2: S1–S5 meter | history only |
| `fn-hud-chapter2-health-shield-bar.png` | Chapter 2: S6–Chapter 3: S1 meter | history only |
| `fn-hud-chapter7-npc-health-bar.png` | Chapter 7 **NPC/boss** bar | different meter from the player's; downloaded for contrast, not measured |
| `fn-hud-elimination-feed-v32.png` | Message Feed, v32.00 | the feed's text metrics — line pitch and cap height |
| `fn-icon-health.png` | health glyph | candidate icon art |
| `fn-icon-shield.png` | shield glyph | candidate icon art |
| `fn-icon-overshield.png` | overshield glyph | candidate icon art |
| `fn-icon-marker-pin.png` | the white marker pin | cross-check against the pin already shipped from the same wiki |
| `fn-settings-video-tab.png` | Fortnite's settings screen, VIDEO tab | the design reference for this mod's settings screen — background, tab bar, rows, tick bars, the description panel, the bottom pill buttons |
| `fn-settings-audio-tab.png` | Fortnite's settings screen, AUDIO tab | the same screen again, which is what makes the shared furniture trustworthy rather than a one-off — the tab bar, the left rail, the row rhythm and the button row are identical across both |

The two settings captures came from the mod's author rather than the wiki, and are
**2000 × 1125** — a clean 16:9 frame, which is a better basis than the wiki's earlier crops
of unknown scale. Still a capture rather than the game's own values, so the numbers read
off it are measurements *of a frame*, not of the game, and are labelled that way.

The three glyph files are the reason the HUD icon slot is not a blocker: unlike the
skull/bust/pointer glyphs (which had to be extracted from a capture, see above), the
health, shield and overshield icons are hosted as public files.

## The heart sprites were removed

An earlier revision tinted Minecraft's own heart sprites green to stand in for Fortnite's
health. That was the wrong shape for the job — a recoloured heart is a Minecraft HUD in
green, not Fortnite's meter — so it was replaced by `HealthShieldHud`, which draws
Fortnite's two bars, glyphs and values directly. The three derived greyscale heart PNGs
came out of the jar with it.

Recorded so the idea is not re-tried: **vanilla's heart sprites are drawn untinted and
pre-coloured** (`full.png` is `#FF1313`, `absorbing_full.png` is gold), so tinting one can
only ever darken it and no green can be made from a red one by colouring. That is a
property of the assets, not of the approach, and it is why the earlier attempt had to ship
derived sprites at all.

The glyphs now used are the wiki's own shield and health icons, above — real art, nothing
re-drawn or derived.

## Keep a file in every slot

Icon textures load synchronously the first time they are drawn, so a slot with no file
at all is worse than a *wrong* file. Every slot already has art in it — overwrite, do
not delete.
