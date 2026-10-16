# Agent notes

Working notes for anyone (human or agent) picking this repo up. Read this before
sourcing art, adding a UI element, or believing something about a Minecraft API.

## UI and icon art: use fortnite.gg/assets

**Any time a UI element or an icon is to be used or created, source it from
<https://fortnite.gg/assets> ("Beyond Assets").**

This is the default source for this project. Do **not** freehand a Fortnite glyph in code
and do **not** trace one out of a compressed screenshot until Beyond Assets has been
checked: it hosts the real art, with `Icons`, `UI`, `Device Icons`, `Graphics` and
`Mini-Maps` categories, so the accurate version is normally one download away. An icon
redrawn from a video frame is a guess wearing a costume.

Order of preference when something is not there:

1. **<https://fortnite.gg/assets>**: the default.
2. **The Fortnite Wiki**, where the ping tones and the white marker pin came from; it hosts
   those as public files.
3. **FModel** against an installed copy of the game, with the current AES key. Fiddliest,
   and the only route for audio and textures the first two do not carry.

Whatever you add, record where it came from in **[docs/ASSETS.md](docs/ASSETS.md)**. That
file is the provenance record for every asset in the mod, and an asset with no recorded
source is an asset nobody can replace.

## House rules

- **Check Minecraft APIs against the real jar, from bytecode, not from memory.** This
  project targets a Minecraft version whose API differs from every older one in ways that
  look plausible either way (`GuiGraphics` is `GuiGraphicsExtractor`; screens render through
  `extractRenderState`; `Minecraft.setScreen` is `setScreenAndShow`; the current screen
  lives on `Gui.screen()`). Mapped jar:
  `~/.gradle/caches/fabric-loom/<version>/minecraft-merged.jar`, read with `javap -c -p`.
  A remembered signature is a bug waiting to happen.
- **Say measured, or say guessed; never blur them.** `docs/FIDELITY.md` exists to keep
  that line visible: it records what came from a real measurement, what came from
  documentation, and what is a working value chosen to look right. Do not promote a tuned
  number to a measured one, and if you find a source, cite it.
- **Do not launch a second Minecraft client while one is running.** It locks the world
  directory, steals focus, and pauses the running player's singleplayer session. Check for
  a running client first (`Get-Process java`), and never kill one that is not yours.
- **Respect the "one marker per player" model.** Pings are keyed by author; a second in-world
  marker for the same player is a behaviour change, not a feature.
- The mod is **client-side only**, and pings reach other players through chat (see
  `PingLink`). Anything that needs a server-side change is out of scope by construction.

## Building

```
./gradlew build      # jar lands in build/libs/ping-<version>.jar
./gradlew runClient  # dev client
```

Incremental `compileJava` is only a few seconds once the daemon is warm, so build often
rather than reasoning about whether something compiles.
