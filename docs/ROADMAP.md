# Roadmap

Features that are wanted and not built, with what is actually known about each. The
"Not built yet" section of the [README](../README.md) is the short version; this is the
long one, and it is where a feature goes so that the question "can this even be done"
is not re-opened from scratch.

Read [FIDELITY.md](FIDELITY.md) first if a number below looks arbitrary — most of them
are, and that file is where the distinction between measured and guessed lives.

## How to read the status

| Status | Means |
|---|---|
| **ready** | Nothing technical is in the way. The work is the work. |
| **decision** | Something has to be chosen before it can be built, and the choice is not technical. |
| **blocked** | The platform does not expose what the feature needs. Recorded so it is not attempted twice. |

## The one structural fact worth reading first

**A Minecraft client cannot see structures.** Verified against the 26.2 jar
(`~/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar`), so this is measured rather
than remembered:

- `ClientboundLevelChunkPacketData` carries heightmaps, block data and block entities.
  It does not carry structure starts.
- `ClientLevel`, `LevelChunk` and the F3 debug overlay expose no structure data.
- `ChunkAccess.getAllStarts()` / `getAllReferences()` are real methods
  (`ChunkAccess implements StructureAccess`), but on a client chunk they are always
  empty, because nothing on the wire ever fills them.

So "the structure you are standing in" is not a lookup. Every feature downstream of POIs
starts there, and it is why two items below are marked **decision** rather than **ready**.

The second structural fact is a pleasant one: **this mod has no mixin config**, and the
features below that look like they would need one do not. `fabric-rendering-v1` exposes

```
HudElementRegistry.replaceElement(Identifier, Function<HudElement, HudElement>)
HudElementRegistry.removeElement(Identifier)
VanillaHudElements.HEALTH_BAR   VanillaHudElements.CHAT   VanillaHudElements.HOTBAR ...
```

so a vanilla HUD element can be wrapped or replaced through the registry instead of
through bytecode. That keeps the mod's "no mixins" property, which is worth keeping.

---

## Ready

### The Fortnite settings menu

**The container for most of the rest of this list.** Right now the mod's settings live in
`config/ping.json` and are edited in a text editor, which `PingConfig`'s own javadoc
defends as a deliberate trade for a small mod. That trade stops holding the moment the
toggle count grows, which it does below.

No new infrastructure is needed: `MapChrome` is already a measured Fortnite panel kit —
flat fills, hairline-edged buttons, the tab slab with its yellow stripe, the blue
gradient backdrop — all stored as fractions of the window. A settings screen is that kit
plus a `Screen` subclass and `setScreenAndShow`.

Art that is genuinely new (gear cog, slider track, toggle switch) comes from
[fortnite.gg/assets](https://fortnite.gg/assets) per [ASSETS.md](ASSETS.md), not redrawn.

**This is the one item on the list with no reference gathered, and that is deliberate.**
The modern settings menu's chrome, tab set and control styling were not obtained, so any
layout built now would be an invention wearing Fortnite's colours — worse than an honest
Minecraft-styled screen. It stays ready *in principle* and blocked *in practice* until
reference exists. See *What reference is still missing* below.

Contents, tab by tab:

| Tab | Holds |
|---|---|
| **Game** | `sendPingsOverChat`, `showMinimap` — the two settings that today are JSON-only |
| **Marker** | `PingColors.THREAT_OVERRIDE` (Fortnite red vs author colour) and `PingConstants.BEAM_FIXED_COLOR` (Fortnite's always-cyan `#00B1EF` vs the author's colour); colourblind mode; marker size |
| **Fidelity** | The constants `FIDELITY.md` already labels unsourced: `MARKER_LIFT`, `BEAM_FADE_NEAR`/`BEAM_FADE_FAR`, `THREAT_LIFETIME_MS`, `REMARK_COOLDOWN_MS`, `MARKER_HEIGHT_FRACTION` |
| **Feed** | Ping feed on/off, feed position |
| **Controls** | A link out to vanilla's own Controls screen rather than a reimplementation of rebinding |

The **Fidelity** tab is the one with a principle behind it. Several numbers in
`PingConstants` are explicitly *tuned estimates*, not measurements. Exposing one as a
slider does not promote it to measured — it lets a player settle what no capture could,
and `FIDELITY.md` stays the record of which is which.

Status: **ready**

### The ping feed ("Zoey pinged Enemy Spotted")

Most of this is already paid for:

- `PingType.label()` already returns "Enemy Spotted", "Going Here", "Defend This" and so on.
- `PingConstants.FEED_LIFETIME_MS` is defined as 4000 ms and is **currently unused**.
- `PingLink` already cancels the `#pp#` line before it reaches the chat HUD, so a feed
  entry cannot double up with a chat message.
- The type id already travels in the codec, so the label needs no wire change.

The design fork is which of Fortnite's two things is being built. Fortnite's **chat** is
the message window; its **feed** is a separate transient notification list. Restyling chat
means replacing `VanillaHudElements.CHAT` and reimplementing scrollback; a feed means adding
a new `HudElement` beside it and leaving vanilla chat alone. `replaceElement(id, old -> ...)`
hands over the previous element, so a wrapper that restyles ping lines and delegates
everything else is possible — but the simpler read is that these are two different features
and only one of them was asked for.

**It is the Message Feed, and it is sourced.** The wiki confirms it by name and date:
v34.30, *"Markers now cause a message to be displayed in the Message Feed."* So Fortnite
does not give pings a panel of their own — it reuses the elimination feed, which sits in
the **bottom left** of the screen (wiki prose, so this is sourced rather than inferred from
a crop). The feed's ping strings are published under Squad Comms, and they are the
vocabulary to use rather than one invented here:

- "Player 1 marked an enemy location"
- "Player 1 marked a destination"
- "Player 1 requests health", "Player 1 requests a ride" — the wheel's other options

Feed styling, also sourced: the named player on the other side of an event is red, a Victory
Crown wearer is yellow, and long-range events append a distance in metres. Measured from the
feed illustration: cap height 14 px with a 32–33 px line pitch (≈ 2.3 ×), no backdrop plate,
left margin ≈ 14 % in. See [FIDELITY.md](FIDELITY.md).

**Built, and merged into chat.** The earlier version was a separate HUD element stacked
above vanilla chat, which made the feed a second widget doing the same job as the first.
It is not: Fortnite's Message Feed and Minecraft's chat occupy the same corner and do the
same thing, so a ping now becomes a chat message and there is only one thing.

`PingFeed` is a queue rather than an on-screen list — `PingManager.place` hands it every
marker, which is the one funnel both local and incoming pings pass through, and the client
drains it into chat each tick. The line wears the author's marker colour and the action in
white. It uses `addClientSystemMessage`, which is display-only, so a ping still costs
exactly one line on the wire and this adds no traffic. Duplicates are dropped, because the
server echoes your own chat line back to you. Off-switch: `showPingFeed`.

The feed's own fade is gone with it — chat owns how long a line stays. That also retires
`FEED_LIFETIME_MS`, which was the only thing keeping the constant alive.

Status: **built**, pending one visual check

### Fortnite health and shields

The cleanest item on the list. Regular hearts become green health; absorption hearts
become shield. Verified against the jar:

- `HudElementRegistry.replaceElement` + `VanillaHudElements.HEALTH_BAR` exist, so no mixin.
- `LivingEntity.getHealth()`, `getMaxHealth()` and `getAbsorptionAmount()` all exist.

There is a rare piece of luck here: **vanilla already applies absorption before health**
when damage lands, so absorption→shield is not an approximation of Fortnite's rule, it is
the same rule. Nothing has to be intercepted.

The cost is that replacing the element means reimplementing it: the heart row, the damage
blink, hardcore hearts, the "absorption and health in one row" packing, and
`VanillaHudElements.MOUNT_HEALTH`. Fortnite draws a continuous bar where this asks for
hearts, so the shape stays Minecraft's and only the colour and the split change.

**Reference is gathered and measured.** The Chapter 7 meter was measured off a reference
crop: shield bar above health, health bar ≈ 1.6 × the shield bar's height, shield fill a
blue gradient over a solid `#211C3D` track, health fill green→yellow-green, and a white
value right of each bar. See *The health and shield meter, Chapter 7* in
[FIDELITY.md](FIDELITY.md) for the numbers and their caveats — the headline caveat being
that every pixel figure is crop-relative, so the mod must take the ratio within one crop
and never a raw pixel count.

**Built.** `HealthShieldHud` replaces the vanilla `health` element — which covers the heart
row and absorption *only*, leaving armour, food, air and mount health as separate elements
— and draws Fortnite's two bars instead: shield above health, the wiki's shield and health
glyphs, a value beside each, a solid dark track, and the measured gradients. Sizes are
whole GUI pixels holding the measured *ratios*, since the capture's absolute resolution is
unknown. Off-switch: `fortniteHealth`.

**Superseded an earlier attempt, on purpose.** That first pass tinted Minecraft's hearts
green, which is a Minecraft HUD in green rather than Fortnite's UI. Hearts are the wrong
shape and the sprites cannot be retinted anyway; see the note in
[ASSETS.md](ASSETS.md). The lesson is recorded so it is not re-tried.

Headless-verified only: it compiles and the jar ships, but it has not been looked at on
screen yet.

Status: **built**, pending one visual check

### The Burbank font

`ASSETS.md` has the whole plan: drop the `.ttf` at
`src/main/resources/assets/ping/burbank.ttf`, add a font provider at
`assets/ping/font/ping.json`, pass the font to the label draw call. The asset is
commercially licensed, so this is a private-use build and should stay out of anything
distributed.

Scope note: this is not only ping labels. The settings menu and the feed both want it, so
it is cheaper to do before either than after.

Status: **ready**

### The compass strip

Already on the README list. It is also the only place a **POI name works while playing** —
the minimap label is a corner readout, the compass is what you read facing forward.

`FIDELITY.md` records the one rule that matters so it is not re-argued: markers and
danger markers display on the compass, **item marks do not**.

Now sourced too: the compass shows facing direction, cardinal or in degrees, and can be
turned off — relevant, because this mod's minimap is already a toggle, so the two are the
same kind of preference. An opponent firing a **non-suppressed** weapon puts a red blip on
the compass, larger the closer they are; suppressed weapons blip not at all, grenades do,
melee does not.

That blip is the one part with no Minecraft equivalent and it is **not free**: it needs a
client-side hook on incoming sound packets, and whether Fabric exposes one is not yet
checked. Treat it as a separate, gated sub-feature rather than part of the strip.

Status: **ready**

### Hostile mobs as enemy markers

`PingController.isHostile` currently returns true only for another `Player` who is not an
ally. A creeper behind you is therefore marked as a plain location pin. Fortnite has no
mobs, so this is a Minecraft-native addition rather than a fidelity item, and it belongs
as a toggle rather than as new default behaviour.

Status: **ready**

### Colourblind mode

Cheap, because marker colour has exactly one derivation point: `PingColors.forType`.
Fortnite ships colourblind modes, and the mod already routes every marker through those
two methods, so a mode is a branch in one place.

Status: **ready**

### Acknowledge / "copy that" ping

The feed's natural other half. Reuses the existing `#pp#` channel with one new `PingType`
and one new keybind; the feed then reads "You: Copy that". No new transport.

Status: **ready**

---

## Needs a decision

### ~~POI naming — structures instead of biomes~~ — resolved, differently

Was filed here as a decision because a client cannot read structures. It is resolved, and
not by picking one of the bad options below — those are kept because the reasoning still
applies to anything that tries to *infer* a structure.

**What was decided: named locations, made by the player.** Right-click a banner, type a
name, and that block becomes a named place. It shows on the minimap at its spot, and the
label at the map's foot shows it when you are standing in it. Biomes are the fallback.
See `PingPoiScreen` and `PoiStore`.

This is better than every option in the table, which is the point worth recording:

- **It cannot be wrong.** A structure detector guesses; a name the player typed is a fact.
  The whole reason the options below are unsatisfying is that they all produce a label the
  mod cannot actually vouch for, and this produces one it can.
- **It exists on a server.** Structure data does not survive the network boundary at all —
  see below — where a banner and a typed name work anywhere.
- **It needs no data.** No bundled list, no seed, no detector to maintain per structure.

**The options that were rejected, and why**, kept so nobody re-proposes them:

| Option | Why not |
|---|---|
| Infer the structure from its blocks | Fragile, and confidently wrong on a player's own base. It was built and then deleted — see the git history for `StructurePois` if the idea comes back |
| Promote biome regions to named locations | Relabelling. Calling a biome a location when Fortnite means a named place is the kind of thing this repo avoids |
| Ship a curated list of known locations | Only knows what it was told; meaningless on a random seed |

**And the exact route that does exist, for the record.** When the client is *hosting* —
singleplayer or a LAN world — it can reach the integrated server's `StructureManager` and
get real structure data with no guessing. That works, and it is what `StructurePois` used.
It is useless for the case that matters here, because **a client that merely joined a
server cannot get structure data by any means**: there is no clientbound structure packet,
the chunk packet carries no structure starts, and the seed needs op. Explorer's Compass, a
mod whose entire purpose is locating structures, ships a server-side component and only
works when the server has it — which is the same wall from a different direction.

Status: **built** — superseded the structure idea entirely

### Newly discovered POIs — the noise and the animation

This was waiting on the structure question above, and no longer has to be. Named locations
give discovery something to discover without any inference at all: the first time you come
within range of a place somebody named, that is a discovery, and the cue and animation
below can hang off it.

The rest is cheap. `PingSounds.play` already looks sounds up by id at play time, and the mod
ships `sounds/scan.ogg` ("Marking Scan v31.00"), which is currently **unreferenced by any
`PingType`** and is the obvious cue.

There is a better cue than that one, though. The wiki's marking sound table lists two
**midmatch-objective** tones as named assets — `S24_Rift_POI_Ping` and
`MMObj_RadioTower_Ping` — which is a dedicated POI ping that exists separately from the
marking tones. They are not in the mod and would have to be extracted, but they are named,
which is the difference between a known target and a hunt.

The animation wants a capture before it is built — Fortnite's discovery banner has a
specific sweep and a specific screen position, and guessing it would put an invented
animation in the middle of the screen.

**Persistence is the real design work.** `ping.json` is one global file, but discoveries
are per world and per server. That means a key derived from the save or the server
address, and a decision about whether discovery is per-player or shared.

Status: **decision**

### The radial wheel

Holding the ping key opens Fortnite's communication wheel. This is the largest UI job on
this list, and it is worth noting what is already waiting for it — **`PingType.DEFEND` and
`PingType.GOING` already exist** with labels and sounds, and no code path ever selects them.
They are dead enum constants until the wheel exists. Everything else in this mod is reached
by one smart ping and a double-tap.

The wheel's options are now sourced rather than recalled. v22.10 (Chapter 3: Season 4):
*"Added new marking options to the marker wheel."* The known set, from the published Squad
Comms strings, is **request health, request a ride, mark an enemy location, mark a
destination** — note that two of the four are requests rather than marks, which is a hint
that the wheel carries `PingType`s and plain chat lines, not only markers. Separately,
holding the aim button while marking produces the danger marker, and that can be turned off
in settings.

Status: **decision** (the payload and the option list are ready; the wheel's geometry still
wants a capture — no measured radius, segment angle or icon has been obtained)

### Chest and container ping

Fortnite marks a chest and shows what is in it. Today a chest is terrain and gets a plain
pin. The open question is not visual: it is whether a *closed* container's contents are
readable client-side, or whether this only works for a container the player has open. That
needs checking against the jar before it is specced.

Status: **decision**

### Pannable map, and marker depth against the world

Both remain from the README list. The map is centred on the player and zooms; Fortnite's
also drags. Marker depth is a known limitation with a known cause: the beam is real world
geometry and occludes correctly, but the pin and its label are HUD, so terrain in front of
a pin dims it rather than hiding it.

Status: **ready**, both — recorded here so they keep a home.

---

## Blocked

Nothing is fully blocked, but the **structures** item above is blocked *as literally
stated*: there is no API that returns the structure a client is standing in. Any version of
that feature is a proxy for it, and should be labelled as one.

---

## Known drift

Found while writing this and fixed in place:

- `ASSETS.md` claimed `PingColors.THREAT_OVERRIDE` "is currently `0`, so every ping wears
  its author's own colour." The constant is `0xFFE8383D` — someone set it to Fortnite's
  red and the doc did not follow. This is exactly the drift `FIDELITY.md` exists to catch,
  and it is why the check is worth running whenever a constant with a documented
  alternative moves.

## Suggested order

Reasoning, not priority by size:

1. **Health and shields** — first, because it is now the only item on this list that is
   fully referenced *and* fully unblocked. It is also the smallest fully-correct standalone
   change, and it proves the `replaceElement` path before anything bigger depends on it.
2. **Burbank font** — cheaper before the menu and feed exist than after.
3. **The settings menu** — the container for everything below, and it stays third only
   because its reference is missing (see the next section). Its content is not: the two
   JSON settings, the marker colours, the Fidelity tab and the feed toggle are all
   specified below and need no screenshot to decide.
4. **The ping feed** — needs the menu for its toggle; two thirds of it is already written.
5. **The radial wheel** — unblocks the two dead `PingType` constants.
6. **POIs** — last, because it is the only item that has to answer the structure question
   first, and a POI system with nowhere to draw the name is half a feature.

## What reference is still missing

Gathered this round, and recorded in [FIDELITY.md](FIDELITY.md) with
[docs/reference/](reference/):

| Obtained | Settles |
|---|---|
| Chapter 7 health/shield meter + the pre-Chapter-7 baseline | the health/shield HUD's layout, colours and bar proportions |
| Chapter 7 NPC/boss bar, Chapter 1 and Chapter 2 meters | history; not measured |
| the Message Feed's position (prose) and its text metrics | where the ping feed sits and how it is set |
| the wheel's option list, and the POI ping sound names | what the wheel carries, and the discovery cue |

Still outstanding, in the order it would unblock work:

1. **Settings-menu chrome.** The only item with nothing. Needs the modern tab set, panel
   and control styling — none of which exists here, which is why that item is not started.
2. **The radial wheel's geometry.** Radius, segment count, segment angle, icon layout.
   Sourceable the same way the health bar was, from a wheel screenshot or capture.
3. **The POI discovery banner.** A capture, for the sweep and the screen position.
4. **A partial-health Chapter 7 meter.** One capture would settle whether the yellow
   segment at the bar's end is a fixed cap or the terminus of the fill's gradient.

## The two questions blocking POIs

1. **Where do POIs come from, given the client cannot read structures?** Signature
   detection, biome regions renamed, or a bundled curated list.
2. **Do discoveries persist per world, per server, or not at all?** `ping.json` is global
   today, and that file is not the right home for this.
