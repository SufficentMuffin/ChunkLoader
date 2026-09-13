# Chunk Loader (Fabric, MC 1.21.11)

A Fabric mod for Minecraft 1.21.11 that adds a block-based chunk loader item
which force-loads a configurable radius of chunks and manually simulates
random ticks inside those chunks so crops, cactus, sugar cane, and similar
block behaviour continue to run while nobody is nearby.

## What it does

- Adds a chunk loader block/item with no crafting recipe. It is intended to be
  spawned with `/give` or added in creative mode.
- Uses the block entity's stored radius index to cycle through the preset
  chunk radii `1, 2, 3, 4, 6, 8`.
- Force-loads the chunk area with `ServerWorld#setChunkForced(...)`, matching
  the same general idea as `/forceload`.
- Runs a one-second server tick loop that performs random-tick simulation
  inside each loaded chunk, so crops and other random-tick blocks continue to
  evolve through the force-loaded area.

## Project setup

This repository is intentionally implemented as a standalone Fabric Loader
project. The Gradle file declares only:

- `minecraft` 1.21.11
- `yarn` mappings `1.21.11+build.4`
- `fabric-loader` `0.18.1`
- Fabric Loom `1.14.3`

The build does not include Fabric API. The mod instead uses an access widener
file, `src/main/resources/chunkloader.accesswidener`, to widen the private
`BlockEntityType$BlockEntityFactory` nested type captured by vanilla's
`BlockEntityType` registration flow. That lets the block entity use the
vanilla builder directly without a Fabric API registration wrapper.

## Version notes

The workspace metadata is pinned to the confirmed 1.21.11 Fabric stack:

| Component | Version | Notes |
|---|---|---|
| Minecraft | `1.21.11` | Target game version |
| Yarn mappings | `1.21.11+build.4` | Fabric mapping source |
| Fabric Loader | `0.18.1` | Required runtime loader |
| Fabric Loom | `1.14.3` | Gradle plugin |

Fabric has confirmed that 1.21.11 is the final 1.x version receiving Yarn
mapping updates. If you update beyond that line, you should expect an actual
mapping migration rather than a simple dependency bump.

## Build and run

Requirements:

1. JDK 21
2. Gradle wrapper files matching the Fabric 1.21.11 template for Loom
3. A local Gradle wrapper using Gradle 8.14 or newer

Build command:

```sh
./gradlew build
```

The jar output is produced by the Gradle build in the standard `build/libs`
folder. The current project metadata sets the output archive name to
`chunkloader-1.0.0.jar`.

## In-game usage

1. Give yourself the block with `/give @p chunkloader:chunk_loader`
2. Place the block near a farm or crop area.
3. Right-click the block to see the current radius.
4. Use creative OP access to cycle the radius through the preset list, or
   adapt the server-side logic in the block entity if you want different
   tuning values.

## Tuning

The live tuning knobs live in the block entity implementation:

- `RADIUS_OPTIONS` controls the preset chunk radii the server cycles.
- `RANDOM_TICKS_PER_CHUNK` controls how many random-tick attempts occur per
  chunk per simulation cycle.
- `SIMULATION_INTERVAL_TICKS` controls the tick cadence of the simulation
  loop.

## Important implementation detail

The project intentionally avoids the Fabric API block entity wrapper. It
registers the block entity using the vanilla `BlockEntityType` constructor and
relies on the access widener for the private nested factory interface, which
matches the current workspace source in the registry and build files.
