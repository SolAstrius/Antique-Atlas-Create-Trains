# Atlas Railways (Live Trains)

> **Experimental variant.** This is a separate copy of Atlas Railways that adds **live, real-time
> train markers** on top of everything the base mod does. The working base version lives in the
> sibling `AtlasRailways` folder; this one is for the train-tracking feature.

Draws **Create** train stations and their rail networks onto the **Antique Atlas** map, like a
hand-drawn subway diagram. Each connected track network gets its own ink colour, every station is a
roundel, and the lines trace the actual track geometry (curves included). It all pans and zooms with
the atlas.

**This also has live trains.** Trains are drawn as colored arrowheads that slide along the
track in real time, pointing the way they travel; hovering shows name and speed. Own green side-tab
toggle (moving-train icon), separate from the railways toggle.

**This variant is NOT client-only.** Create only ticks trains on the server, and only syncs the
positions of *nearby, loaded* trains to clients. To track trains anywhere on the map, this variant
adds a **server-side component**:

- Install the jar on **both the server and the client** (unlike the base mod).
- The server (`AtlasRailwaysCommon` + `CreateServerTrains`) reads Create's global `RAILWAYS.trains`
  a few times a second and streams every train's position to clients via a custom packet
  (`TrainSyncPayload`).
- The client stores them (`LiveTrainStore`) and the overlay draws them.
- **fallback:** if the server does not have this mod, the client falls back to reading
  only nearby (loaded) trains locally — so it still runs client-only, just without map-wide tracking.

Singleplayer works out of the box (the integrated server is the server). Heading is from
`TrackEdge.getDirectionAt` (fed the raw edge distance, which is what it expects), sampled the same
way stations are placed on the track.

![icon](src/main/resources/assets/atlasrailways/icon.png)

- **Client-side only.** Nothing to install on the server.
- **No custom networking.** Create already syncs its full track graph to every client (that is how
  trains render); this mod just reads it.
- **Cheap to render.** The world-space geometry is baked into a cached snapshot that rebuilds only
  when Create's graph actually changes. Each frame is a re-projection with a handful of batched draw
  calls, so scrolling and zooming stay light.

## Requirements

A cross-ecosystem setup. All of these belong in the same instance:

| Mod | Loader | Notes |
|-----|--------|-------|
| Minecraft | 1.21.1 | |
| [Create](https://modrinth.com/mod/create) 6.x | NeoForge |
| [Sinytra Connector](https://modrinth.com/mod/connector) |
| [Forgified Fabric API](https://modrinth.com/mod/forgified-fabric-api) | | Connector's Fabric API bridge |
| [Antique Atlas](https://modrinth.com/mod/antique-atlas) 3.x (`+1.21`) | Fabric | plus its dependency, Surveyor |
| **Atlas Railways** | Fabric | via Connector |

## Building

```bash
./gradlew build
```

The jar lands in `build/libs/`. Drop it into a Connector-enabled 1.21.1 instance alongside the mods
in the table above.

Antique Atlas and Surveyor come from the `repo.sleeping.town` maven (configured in `build.gradle`).
If a version fails to resolve, bump `antique_atlas_version` / `surveyor_version` in
`gradle.properties` to a published `+1.21` build.

## Notes & tuning

- Line colours come from `RailPalette`, a curated palette indexed by network UUID. Edit the `COLORS`
  array to recolor.
- Line thickness and station size scale with zoom; tweak the clamps at the top of
  `RailwayOverlay#onScreenRender`.
- Station names show on hover.
- Curve smoothness is `CURVE_SAMPLES` in `CreateRailways` (8 by default).

## License

MIT.
