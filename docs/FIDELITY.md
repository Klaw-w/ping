# Fidelity notes

What is actually measured about Fortnite's marker, what is inferred, and what is still
unknown. Kept so these questions are not re-opened from scratch.

## How the numbers were derived

Six reference captures were measured. They are **crops of unknown original resolution**,
so raw pixel counts from them are not absolute. Two anchors were used to compare crops:

- the HUD ammo digits (27 px cap height in the crops that share a scale)
- the crosshair, which is pixel-identical between two of them

Three crops share one pixel scale. Converting to a fraction of screen assumed a
**1920×1080 frame**; if the capture was a different resolution, every "fraction of
screen" number below scales with it. That assumption is the single biggest caveat here.

## Measured — safe to build on

### The pin

| Property | Value |
|---|---|
| Size | 18 × 28–30 px in-crop; **18 × 29** nominal |
| Aspect (w/h) | 0.60–0.64 |
| Height as fraction of screen | **2.7 %** |
| Scales with distance? | **No.** Identical at 1 m, 18 m and 114 m — under 7 % variation across a 114× distance change |
| Glow | **None.** Pixels outside the 2 px outline are plain background |
| Outline | **2 px**, near-black (`#000A1E` waypoint, `#16041E` item) |
| Inner hole | ∅ 6–7 px, circular, centred 8–8.5 px below the pin's top |
| Fill (waypoint) | `#167DE9` |
| Fill (item) | `#CE59FF` |
| Horizontal anchor | **Centred on the beam axis / marked point** (verified in 4 crops, Δ ≤ 2.5 px) |

The 18:29 aspect is carried by the artwork: the pin PNG is cropped to its content and
centred in a square canvas, giving 0.61 — matching the measurement.

### The distance label

- Format `<integer>m`, no space, no decimals.
- **Cap height 13 px = 1.2 % of screen height** — about half the HUD ammo digit.
- Flat `#FFFFFF`. 1 px dark outline plus a ~2 px downward drop shadow.
- **Left edge = pin's right edge + 2–3 px.**
- **Vertical centre sits ~4 px above the pin's tip** — level with the bottom third of
  the pin. Not centred on the pin, not above it.
- Fully opaque at both 1 m and 114 m; no range fade observed.

### The beam

- **Two radial layers**: a broad translucent saturated-blue band and a brighter cyan core.
- **Contains no red at all** — `R = 0` in every sampled beam pixel. Measured core
  `#00B1EF`.
- **Hard flat base** (1–2 px falloff), **soft fading top**.
- **Tapers — apex up.** Full width for the lower stretch, opening out over the top ~25 %.
- **Core width is constant at 16–17 px from 1 m to 114 m** (~0.94 × the pin width). A
  world-space object could not do that, so the core is screen-space or
  distance-compensated.
- The **outer band** measured ~19 px at 1 m but 39–42 px at 114 m. Flagged as
  inconsistent with the core's constancy; only two distance samples exist.
- Near the player the cross-section reads as a **hollow tube** — the silhouette edges are
  brighter than the centre. Only relevant inside the near range, which this build does
  not draw.
- The axis is a genuine world-vertical line: two far captures drift in opposite
  directions but extrapolate to a **common vanishing point**, which is camera roll, not a
  tilted beam.

## Contradicted or unsupported — do not treat as settled

### Marker lifetime and the re-mark cooldown

Not from a capture — nothing in the reference material shows a timer — but from a player
measurement and Fortnite's own documentation, which is stronger than a guess and weaker
than a measurement:

| Property | Value | Source |
|---|---|---|
| Item marker lifetime | **45 s** | player-measured, and reported as diagnostic: "a pinged item's ping lasts for 45 seconds… if the ping goes away before 45 seconds that means someone picked it up" |
| Existence of a re-mark cooldown | **yes** | Fortnite wiki, *Marking*: markers "have a short cooldown before using them repeatedly" |
| Location marker lifetime | **assumed equal to an item marker's** | the wiki says only "a decent amount of time" |
| Enemy/danger marker lifetime | **unknown** | the only hint is a player watching the world-marker demo describe them as vanishing "after a few seconds, just like danger markers", which suggests single-digit seconds |
| Cooldown length | **unknown** | documented as existing, never as a duration |

The build uses 45 s for every non-threat marker — one measured number rather than a
measured one and an invented one beside it — and 500 ms for the cooldown, which is a
tuned estimate and labelled as one in `PingConstants`. The threat markers keep a
deliberately shorter 10 s, which is also unsourced and sits at the top of the "a few
seconds" hint rather than inside it.

**Trap, recorded so it is not walked into twice.** There is a Fortnite patch note reading
"Increased cooldown from 6 seconds to 10 seconds". It is not about markers; it is the
Save the World **Throwing Stars** ability (v8.00). Any future write-up that cites it as the
marker cooldown will be wrong, and it will look right.

Still worth measuring: whether location and item marks really share a lifetime, the enemy
marker's lifetime, and the real cooldown length.

### "The beam appears at 50 m"

**Not supported by the captures, and not contradicted by them either.**

- The only waypoint samples are at **1 m** and **114 m**; there is nothing in between.
- The only sub-50 m sample with no beam is an **item** marker, and item markers have a
  completely different visual set (icon in a ring, no pin glyph, no beam at all).
- A beam **is** present at 1 m on the waypoint markers.

Epic's own **Player Marker** device documents a `Hide Distant Marker Distance` default of
**50 Meters**, and the wiki says the beam *"can be seen across the map and will fade as
the player gets closer"*. So 50 m is plausible as the pivot, but the captures neither
confirm nor refute it.

The build currently gates the beam at 45–55 blocks per the brief. **Captures at 10, 30,
45, 55 and 70 m would settle it.**

### Item markers have no beam

In the two captures that show an item marker at 1 m and 18 m, an explicit search for beam
pixels found none. This build draws a beam for every marker type. If the goal is strict
1:1, item markers should not get one.

### Vertical anchor of the pin

**Unresolved, and the biggest hole in the spec.** One 114 m capture implies the pin is
*centred* on the marked point (its centre sat 3 px from the crosshair, its tip 16.5 px
below it). The 1 m captures show the pin's tip sitting 200–250 px above whatever else is
visible, which no world geometry explains. Either those near captures show a *different*
device (they do carry a `WAYPOINT` popup), or a camera-proximity fade is suppressing the
near-range beam, or the pin was mid-animation.

The build therefore uses a **tunable lift** (`PingConstants.MARKER_LIFT`) rather than
claiming a rule. Set it to `0.0F` for tip-anchoring.

## Still unknown

- **Absolute scale in real pixels or degrees.** Everything is relative to an assumed
  1080p frame.
- The beam's **world height**, and whether it reaches the skybox. It never does in the
  captures, but screen y cannot be converted to altitude without a horizon reference.
- Beam **height versus distance**: measured 0.14 of screen height at 1 m but 0.88 at
  114 m — roughly 6× growth for a 114× distance change, which is neither a fixed world
  height nor a fixed screen height. **Inexplicable from these samples.** The build uses a
  flat 0.5 pending better data.
- **Off-screen indicator / edge clamping** — never observed; every marker was in frame.
- **Marker lifetime and cooldown** — no timer or cooldown state appears in any capture.
  The durations are known from other evidence; see *Marker lifetime and the re-mark
  cooldown* above, which also lists what is still unknown.
- The **danger marker** variant (double-tap) — not present in any capture, so no colour
  or shape data exists for it.
- Where the **distance label** goes at extreme range.

## The minimap

**The chrome is measured**, from a Fortnite capture at 1919×1079, using the same
scan-line methodology as the marker measurements above. So unlike an earlier revision of
this file, the geometry below is read off the frame rather than chosen:

| Property | Measured | In the build |
|---|---|---|
| Map edge | 252 px, exactly square | 252/1079 = 0.2335 × screen height |
| Top margin | 32 px | 32/1079 |
| Right margin | 31 px — the map does **not** touch the screen edge | 31/1079 |
| Frame | none: no border, no plate, no shadow | none |
| Corners | square, radius 0 | square |
| Readout column | right-aligned against the map's left edge; glyph right edges 25 px out, numbers 61 px out; row pitch 36.5 px | same proportions |
| Readout glyphs | 19×22 (skull), 20×21 (players bust) | one box, each drawn at its own aspect |
| Readout text | `#FFFFFF` | white |
| Location label | white, cap height 21.5 px, ≈3 px black outline, 32 px above the map's foot, right-aligned and clipped at the map edge | **removed — see the note below** |
| Player pointer | 12×17, white with a dark halo | the same artwork |
| Teammate pointers | same shape, own colour | same, ally-only |
| Enemy marker | ≈38 px disc, crimson `#D24E49`, white skull punched over it | the same artwork and colour |

Deliberate deviations from the capture, and why:

- **Readout text size.** Fortnite's 14 px cap height is about 4.7 GUI pixels at Minecraft's
  GUI scale 3 — below what Minecraft's font can draw legibly. The readouts keep Fortnite's
  positions and row rhythm but render at Minecraft's own font size, so they read slightly
  larger than the capture. Matching the pixel height would be matching something nobody
  could read.
- **The label's content — and then the label.** This one reverses twice and is worth
  reading before anyone re-adds it. First the slot was filled with the **biome**, on the
  reasoning that Minecraft has no named locations. Then named locations were built (a
  banner you right-click and type a name into) and took the slot, with the biome as the
  fallback. Then **both were removed**: the person who actually plays the game reports that
  no such text sits under the corner minimap at all.
  **That contradicts the row above**, which records a location label measured off a
  minimap capture 32 px above the map's foot. Either the capture is from an era that had
  it and current Fortnite does not, or the measurement was attributed to the wrong element.
  Until a fresh capture settles it, the element is off — and the numbers
  (`LABEL_FRACTION`, `LABEL_BOTTOM_FRACTION`) are left in `MinimapConstants` as measurements
  looking for something to describe rather than deleted, because the measurement itself is
  probably still real even if its owner is not what was assumed.
  Named places still appear, but **on the map at their own coordinates**, which is where a
  name has a position and can be read against the place it names.
- **The storm timer is gone.** The third readout is a storm countdown and Minecraft has no
  storm. Rather than fake a countdown, the row was dropped; the column is two rows.
- **Item marks keep their icon on the map.** Still the ambiguous one: see the row below.
- **The "N" is gone.** Earlier revisions drew a north indicator on the frame. The capture
  has none and no source describes one, so it was removed rather than kept as an
  unlabelled invention — a north-up map with a compass strip elsewhere does not need it.
- **The label is drawn as a real outline**, not a drop shadow, because the capture's 3 px
  black edge on all sides is what makes it read as a label.

The glyphs themselves are **the real artwork**, extracted from that capture at native
resolution as white alpha masks. At Minecraft's GUI scale the drawn sizes are *smaller*
than the extracted ones, so extraction is not a lossy trace of the art — it is more
resolution than is used. Why extraction rather than a download is recorded in
[docs/ASSETS.md](ASSETS.md).

The behaviours are sourced separately from the geometry:

| Behaviour | Verdict | Source |
|---|---|---|
| The map does **not** rotate with the player | **sourced, and matched** | players asking for rotation are told there is none; Epic's UEFN *Map Controller* device exposes "Minimap Lock Player Rotation" as an authoring toggle, default off, which only makes sense if the default is fixed |
| Which corner it sits in | "in the corner of the screen" is all Epic says | Epic's minimap documentation; players report top-right today, and top-left historically |
| Markers appear on the minimap, not just in the world | **sourced, and matched** | v7.00 patch history, "Updated marker appearance on the Minimap and Map"; the *Map Indicator* device "displays on both the minimap and the overview map" |
| A marker past the frame is shown rather than dropped | **sourced, and matched** | v8.00, "World Markers now have an off-screen indicator and display the distance your character is from them" |
| The player's own marker is a white arrow | **sourced, and matched** | players refer to "the white arrow"; v16.00 rounded and shortened the player markers on the minimap and map |
| Teammates are arrows in their own colours | **sourced, and matched** | players describe their own arrow being "covered up by… my teammates' arrows"; the mark takes the player's assigned colour |
| Marks take a fixed team palette (blue/red/purple/yellow/green) | **not implemented, on purpose** | the wiki gives the order but admits the assignment is unknown, and players dispute the second colour (red vs purple). A marker wears the author's Minecraft *locator bar* colour here, so a ping matches that player's pip — changing it would mean changing the in-world markers too, and a colour that differs between map and world is worse than either |
| Item marks do not appear on the **compass** | recorded, not applicable yet | "Markers and danger markers will also display on the compass, but item marks will not." There is no compass strip in the build; when there is, item marks stay off it |
| Item marks on the **minimap** | **ambiguous, and this build shows them** | nothing authoritative either way. The compass line implies item marks are shown *somewhere*, but a feature request titled "Make pinged items appear in the minimap through an option" implies they are not on the minimap today. Flagged rather than resolved: showing the icon is more useful, and one community request is thin evidence to remove it |
| A coordinate grid on the full map | **unsourced — an addition** | the only grid maps found are community-made for POI callouts, which implies the game does not draw one. Fortnite does name locations on the map; Minecraft has none to name, so a numeric grid takes that job |
| Threat marks are always red | **community convention, not documented** | the wiki's only colour rule is that a mark takes its player's team colour. Players and press consistently describe the enemy/danger mark as red, and the capture shows the crimson disc measured above |

Three things the map does that Fortnite's does not have to, because Minecraft is not a 7×7 km
island: the map is bounded by what the client has loaded rather than by the edge of the
world, so columns with no data are drawn as unknown rather than as terrain; the map's
terrain is re-read on a timer rather than on block updates, so a build or a hole appears
within a few seconds rather than instantly; and the minimap is off by a toggle, because a
corner map is a preference in a way that Fortnite's fixed HUD is not.

## The full-size map screen

Measured from the second capture (1250×702), same methodology. The chrome is:

| Property | Measured | In the build |
|---|---|---|
| Backdrop | opaque blue gradient `#0B1F76` → `#031150`; the world is **not** visible behind it | same |
| Tab strip | top-left; the active tab is a white slab with a 9 px `#FAFF16` stripe; idle labels `#5DA2F0` | same |
| Level + XP bar | badge at x 0.72, bar x 0.752→0.978 at y 0.043, in ~29 px segments, violet→light-blue fill | same, from the player's real XP |
| Left panel | x 0.030, y 0.161, width 0.232; header strip `#1250A7` to y 0.221, body `#031F51`; row pitch 0.037 | same |
| Map area | (336,115)–(872,647) → x 0.269→0.698, y 0.164→0.922 | same |
| Reset | (566,658)–(696,687) → x 0.453, y 0.937 | same, and it re-centres the map |
| Hints | right-aligned at x 0.975, 26 px pitch from y 0.795 | same, with this mod's real controls |

**Fortnite's rows, and what fills them here.** Three of its fields have no Minecraft
equivalent, so rather than invent data the slot is reused for the real thing it most closely
matches, or dropped:

| Fortnite field | This build | Why |
|---|---|---|
| `LVL` + XP bar + "27,691 XP to LVL 73" | the player's XP level, progress and XP remaining | identical in both games; no translation needed |
| `PLAYERS (SQUADS) LEFT 99 (33)` | `PLAYERS ONLINE`, the tab-list count | the client knows who is online; it does not know squad sizes |
| `STORM DAMAGE 1 DPS` (the row with a clock icon) | `TIME`, the in-game clock | Minecraft has no storm. A countdown would be fabricated; the time of day is real, useful under a map, and takes the row Fortnite gives a clock |
| `ELIMS` rows | your own count from the server statistic; everyone else listed with a dash | **the client cannot know another player's kills.** The server pushes stats for the local player only, and no clientbound packet carries a killer, so a number there would be made up |
| `BARS 4,368` | dropped | a Fortnite currency with no Minecraft counterpart |
| `PAN` / `ZOOM` / `PLACE MARKER` / `Exit` | the same, with this mod's real bindings | the capture's hint column, so drag-to-pan had to be implemented |

Two things are decorative and known to be: the tab strip shows all four of Fortnite's tabs
with only `MAP` doing anything, because the capture has all four and removing three would
stop matching it; and the labels use Minecraft's font rather than **Burbank**, which
`docs/ASSETS.md` covers — it is a commercial font and is not wired up.

## The health and shield meter, Chapter 7

Not previously covered in this file. Measured from reference crops downloaded to
[docs/reference/](reference/); see [ASSETS.md](ASSETS.md) for where each came from.

**Caveat first, and it is the big one: every pixel number below is crop-relative.** The
crops are of unknown original resolution, exactly like the marker captures in the first
half of this file, and there is only **one** Chapter 7 sample. Ratios *within* a crop are
sound; cross-crop comparisons are not, and the two crops disagree in a way that proves it
(see the last table row). Nothing here should be turned into an absolute pixel size.

### Chapter 7 (crop 363 × 86)

| Property | Measured |
|---|---|
| Order | shield bar **above** health bar |
| Left column | two near-white glyphs, `#E8ECF7`, x ≈ 10–28: a shield (top) and a cross (bottom) |
| Bars | x ≈ 32 → 305, i.e. **274 px** wide |
| Shield bar height | ≈ **19 px** (rows 21–39) |
| Gap between bars | ≈ **4 px** |
| Health bar height | ≈ **30 px** (rows 44–73) |
| Health ÷ shield height | ≈ **1.6** |
| Shield fill (50 shown) | ends x ≈ 161 = **47 %** of the bar — consistent with the printed 50 |
| Shield fill colour | blue gradient, left ≈ `#039AEF` → right ≈ `#0AA9EF` |
| Shield empty track | **solid** `#211C3D`; it is a fill, not a transparency |
| Health fill (100 shown) | the whole bar |
| Health fill colour | gradient left ≈ `#25CA45` → right ≈ `#A9F43E`, and brighter at the **top** of the bar (`#A9F43E` top → `#90D433` bottom) |
| Bar-end cap | a pure yellow block, `#EEF926` → `#F2FF2D`, the last ≈ 4–6 px of the health bar **only**, visible because health is full |
| Values | white, bold, right of the bar, right-aligned ≈ x 340 — about 35 px clear of the bar's end |
| Backdrop | none. No plate, no panel; the meter sits directly over the world |

The yellow cap is the one thing here whose *rule* is not settled by a single full-health
sample: it is either a fixed end-cap or the terminus of the fill's own green→yellow ramp.
The pre-Chapter-7 crop shows a bright `#9CFF61` at the same place, which fits the ramp
reading better. **A capture at partial health would settle it.**

### Pre-Chapter 7 baseline (Chapter 3: Season 2, crop 476 × 74)

Kept because it is the design Chapter 7 is a change *from*, and because it is the second
sample that exposes the scale problem.

| Property | Measured |
|---|---|
| Shield bar height | ≈ **15 px** (rows 11–25) |
| Gap | ≈ **6 px** |
| Health bar height | ≈ **30 px** (rows 32–61) |
| Health ÷ shield height | **2.0** |
| Shield fill gradient | `#007ECE` → `#00B7F9` |
| Health fill gradient | `#09BD21` → `#3ED823`, ending `#9CFF61` |
| Bars | x ≈ 32 → 424 (full width for both) |

**Why the two crops cannot be compared.** The health bar is 30 px in *both*; the shield bar
is 19 px in one and 15 px in the other. Either the shield bar genuinely got fatter in
Chapter 7, or the two crops are at different scales and the 30 px agreement is a
coincidence. With n = 1 each, there is no way to tell. This is the same trap the marker
measurements hit, and it is why the fractions in the mod must be derived from the *ratio
within one crop*, never from a raw pixel count.

### Sourced, not measured

From the Fortnite Wiki's *Health (Battle Royale)* page, which is documentation rather than
a capture — stronger than a guess, weaker than a measurement:

| Fact | Source |
|---|---|
| Health is the **green** bar, shield the **blue** one | wiki prose |
| Shield is consumed before health; overshield before shield | wiki prose |
| **Storm and fall damage bypass shield** and hit health directly | wiki prose; also names Alien Parasites and Raz's magic |
| v11.00 (Chapter 2: Season 1) — the meter moved to the **bottom left** of the HUD | update history |
| v11.30 — the meter **shakes** when the player takes damage | update history |
| v20.00 (Chapter 3: Season 2) — "returned to a straight bar instead of slanted similar to Chapter 1 **except on the left instead of the middle**"; Overshield added in Zero Build | update history |
| Overshield = **50** on top of the 200 HP threshold, regenerates over time, damaged first | wiki prose |
| Riding a vehicle draws a separate **orange** health bar above the player's shield bar | wiki prose |
| v40.40 (Chapter 7: Season 2) — lethal fall damage leaves 1 health instead of eliminating | update history |
| v39.00 (Chapter 7: Season 1) — hitmarker and elimination sounds updated, with a **Legacy** toggle under Audio | update history |

### What this means for the mod

**Built as `HealthShieldHud`, which replaces vanilla's `health` element.** The four ratios
above are what the constants in that class hold, with one chosen scale on top:

| Ratio | Measured | Constant |
|---|---|---|
| health ÷ shield height | 1.58 | 16 ÷ 10 |
| gap ÷ health height | 0.13 | 2 ÷ 16 |
| bar width ÷ health height | 9.13 | 146 ÷ 16 |
| icon column ÷ health height | 0.60 | 12 ÷ 16 |

The scale itself (health bar 16 GUI px tall) is **chosen, not measured**, because the
capture's absolute resolution is unknown — the same caveat as everywhere else in this file.

Two presentation decisions, both deliberate and both visible:

- **The values read 0-100, as Fortnite's do.** Minecraft's health is out of 20 and
  absorption has no fixed cap, so a raw number would print "20" where Fortnite prints
  "100". Instead both bars print the fraction that is filled, as a percentage, so the
  number and the fill always agree and a full bar reads 100. It is a percentage, not a
  hit-point count. Printing raw hit points is the honest alternative and is a one-line
  change if the percentage is ever judged misleading — but it does not look like Fortnite.
- **Position is Epic's own.** Their documentation states it directly: health is a green bar
  at the bottom left with a plus icon to its left, and the blue bar above it is shield.
  That is the layout implemented, and it is also why chat has to move — see below.

Three divergences are worth knowing rather than discovering later:

- **Fall damage and storm-equivalent damage bypass shield in Fortnite but not in
  Minecraft.** In vanilla, absorption soaks fall damage. Matching Fortnite exactly would
  mean subtracting fall damage from health first, which changes survival in a way a
  HUD-only feature has no business doing. Recorded as a deliberate divergence.
- **Fortnite has a third layer, Overshield, with no Minecraft counterpart.** Absorption is
  temporary and non-regenerating; overshield regenerates. Mapping overshield onto a second
  band of absorption would be inventing behaviour, so it is not done.
- **The terminator is a fill-end cap, and that reading is inferred.** Each bar ends in a
  bright block: a yellow tip on health, a thin cyan sliver on shield. Both captures that
  show it have the bar at full, where "end of the fill" and "end of the bar" are the same
  pixel — so the capture alone cannot separate them. The shield bar decides it: at 50% its
  sliver sits at 47%, which no fixed bar-end cap explains. The cap therefore tracks the
  fill and is drawn at every value, not only when full. A capture of a *part-filled* health
  bar would confirm it directly; until then this is the inference and it is labelled as
  one.

### Chat and the meter share a corner

Fortnite puts the meter bottom-left; Minecraft puts chat there. They print over each other
until one moves, and the meter is the one Epic has a documented position for, so chat is
what moves. `ChatOffsetHud` replaces the vanilla `chat` element with the same
`ChatComponent` drawn through the same call inside a translated matrix — chat's own
behaviour is untouched, only where its output lands. The offset comes from
`HealthShieldHud.clearance()`, so resizing the meter moves chat with it instead of the two
drifting apart.

This happens to land the arrangement Fortnite actually has: the message feed above the
health bar.

## The ping feed, and where it sits

Sourced and citable, which matters because the roadmap had this filed as "needs a capture":

- The wiki's **Message Feed** page: "a string of text in the **bottom left side** of the
  player's screen". That is the position, in prose, from the wiki — not a guess and not
  inferred from a crop.
- v34.30 (Chapter 6: Season 2): **"Markers now cause a message to be displayed in the
  Message Feed."** So the ping feed is a real, dated feature, and it reuses the same feed
  as eliminations rather than being a panel of its own.
- The feed's ping strings are listed under **Squad Comms**: "Player 1 requests health",
  "Player 1 requests a ride", **"Player 1 marked an enemy location"**, **"Player 1 marked a
  destination"**. Those are the actual game strings, and they are what this mod's feed
  should be echoing, not an invented vocabulary.
- Feed styling, from the same page: the victim's name is drawn in red; a Victory Crown
  wearer's name in yellow; long-range eliminations append a distance in metres.

### Measured, from the feed illustration (crop 535 × 230)

| Property | Measured |
|---|---|
| Lines | 5 |
| Glyph cap height | **14 px**, identical on every line |
| Line pitch | **32–33 px**, i.e. ≈ **2.3 ×** the cap height |
| Left margin | text ink begins x ≈ 74 of 535 — ≈ **14 %** in from the left edge |
| Body text | white, flat, with a dark drop shadow / outline |
| Highlight | victim name in red; sampled ≈ `#BE4537`–`#CA453F` (low sample count — antialiasing against the backdrop pulls it, so treat the exact value as soft) |
| Backdrop | **none** — no plate, no panel, text straight over the world |

**Treat the newline rhythm as the transferable number and the position as sourced from the
prose instead.** The backdrop of this crop is a flat, uniform grass texture across all 535
px, which is what a composited illustration looks like rather than a frame of gameplay. The
text metrics survive that (they are the author's layout), but any screen position derived
from it would not. Hence: pitch and cap height from the image, bottom-left from the wiki.

## The compass

Sourced from the wiki's *Compass* page:

- It is part of the HUD and can be **turned off** — relevant, because this mod's minimap is
  already a toggle.
- It shows facing direction, cardinal or in degrees.
- An opponent firing a **non-suppressed** weapon puts a **red blip** on the compass, larger
  the closer they are. Suppressed weapons put no blip. Grenades blip. Melee does not.
- From the *Marking* page, and already recorded above: markers and danger markers display on
  the compass, **item marks do not**.

The gunfire blip is the one compass behaviour with no Minecraft equivalent yet, and unlike
the rest of the compass it is not free: it needs a client-side hook on incoming sound
packets. Whether Fabric exposes one is **not yet checked** — this is a lead, not a plan.

## The marker wheel, and the POI sounds

Two small, dated facts worth having, both from the *Marking* page:

- v22.10 (Chapter 3: Season 4): **"Added new marking options to the marker wheel."** The
  wheel is where `PingType.DEFEND` and `PingType.GOING` belong.
- The wheel's known options, from the Squad Comms strings: **request health, request a ride,
  mark an enemy location, mark a destination**. Plus, separately, holding the aim button
  while marking produces the danger marker "with default settings (this can be turned off)".

And for the POI work: the wiki lists two **midmatch-objective ping sounds**,
`S24_Rift_POI_Ping` and `MMObj_RadioTower_Ping`. A dedicated POI ping tone exists and is
named, so the discovery cue does not have to be borrowed from the marking tones.

## The settings screen

**This section reversed.** It used to say "entirely a working value, nothing here is
measured" — there was no capture of Fortnite's settings screen anywhere in the repo, so the
first version was spaced to the map screen's rhythm and shipped as a guess. It looked
nothing like Fortnite and the author said so. Two captures of the real screen now exist
(`fn-settings-video-tab.png`, `fn-settings-audio-tab.png`, both 2000 × 1125, recorded in
[ASSETS.md](ASSETS.md)), and the screen was rebuilt from them.

**The chrome is its own, not the map screen's.** The map screen's furniture came from a map
capture; this screen's comes from a settings capture. They are two different screens in
Fortnite and they look different, so they have two different sets of numbers. Do not
"unify" them.

**Measured, from those two frames** — a clean 16:9 capture, so ratios of a known frame
rather than of a crop of unknown scale, which is better footing than the marker work above:

| | |
|---|---|
| Background | `#0D4C6B` at the left → `#0F0D1B` at the bottom, navy `#0F1A38` at the right |
| Tab bar plate | `#080A19` |
| Active tab highlight | `#B8D5E5` |
| Chevron blue | `#47649E` |
| Headings, labels, values | `#FFFFFF` |
| "OPTIONS" button face | `#D2D1D8` |

**Chosen by eye, and labelled as such in the code**: the slider fill, the tick-bar colours,
the focus outline, the option-list bar and the pill buttons. Those controls sit too small in
the frame to sample cleanly, and claiming otherwise would be exactly the blurring this file
exists to prevent.

**Layout** is the capture's own proportions: the tab bar across the top with the active tab
lit and named beneath it, a section heading, rows of label-left and value-right with a
segmented bar under the value, a rounded outline on the focused row, a description panel
with an option list, and the pill buttons bottom-right.

Two things the capture has that this build deliberately does not: eleven tab icons, when the
mod has two screens and a tab that does nothing is a lie in the shape of a button; and the
Burbank typeface, which is not shipped — sizes and weights follow the frame, the letterforms
cannot. See [ASSETS.md](ASSETS.md).

**Text is boxed.** Every label, value and paragraph sits in an invisible box, clipped to it,
scrolling within it when the text is too long. That is how Fortnite behaves and it is not
cosmetic: without it a long description collides with the option list below it, which is
exactly what shipped in the first build of this screen and what the author reported.

## The single most valuable next input

One **full-resolution, uncropped screenshot with the HUD fully in frame at a recorded FOV
setting**, of a single waypoint marker — plus the **same marker captured while walking
from ~10 m to ~120 m** without changing the view.

That one set fixes absolute scale, resolves the vertical anchor rule, measures the beam's
true world height against the horizon, and settles the 50 m question directly.
