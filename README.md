# Chunk Loader (Fabric, Minecraft 1.21.10)

A **server-side-only** Fabric mod that turns the vanilla **dragon egg** into a chunk
loader. It registers **no custom blocks, items, block entities, or any other
client-visible content**, so a completely unmodified vanilla client can join with
zero issues.

Each loader can be individually toggled between **mineable** (players can break and
relocate the egg) and **permanent** (the egg is unbreakable). Player-placed eggs
default to mineable; op-claimed loaders default to permanent.

## Requirements
- Minecraft **Java Edition 1.21.10** (dedicated server)
- **Fabric Loader 0.19.5** (no Fabric API needed — the mod is pure Mixin)
- Java **21**

## Building
```bash
./gradlew build        # Windows: gradlew.bat build
```
The compiled jar lands in `build/libs/chunkloader-1.0.0.jar`. Drop it into the
server's `mods/` folder. No Fabric API and no client-side install are needed.

Versions are pinned to the official Fabric example-mod for 1.21.10:
`minecraft 1.21.10`, `yarn 1.21.10+build.3`, `loader 0.19.5`,
`loom 1.17-SNAPSHOT`, Gradle 9.5.1 (wrapper included).

## Behaviour
- **Claim (player places egg)**: placing a dragon egg → it becomes a **mineable** chunk
  loader with a default radius of **3 chunks** (Chebyshev). Its chunks are forceloaded
  and actively grown. The egg can be mined and relocated.
- **Claim (op command)**: `/chunkloader claim` → places a **permanent** (unmineable)
  loader. The egg cannot be destroyed by mining, explosions, or pistons.
- **Mineable toggle**: `/chunkloader mineable <true|false>` flips whether the loader
  you're standing in can be mined. Mineable eggs drop themselves when broken (no silk
  touch required); permanent eggs are unbreakable.
- **Teleport disabled**: right-clicking a dragon egg no longer teleports it
  (`interactBlock` returns `FAIL`). Left-click teleport is also suppressed for loader
  eggs via `DragonEggBlockMixin`.
- **Growth**: `BlockState.randomTick(...)` is called directly on sampled positions —
  the exact method vanilla's engine calls — so every crop follows its real,
  unmodified growth mechanic. Density matches vanilla (3 random ticks per sub-chunk
  per game tick), so growth is authentic and can **never exceed vanilla rates**.
  Chunks already being random-ticked by vanilla (within any online player's
  simulation distance) are skipped to avoid double-ticking.
- **Persistence**: `claimed_locations.txt` in the world save folder, one loader per
  line: `dimension,x,y,z,radius,mode,mineable`. Reloaded on server start, rewritten on
  any change. Old formats (4–6 fields) are migrated automatically, defaulting to
  mineable.
- **Command** (all op-only, permission level 2):

  | Command | Description |
  |---|---|
  | `/chunkloader claim [crop\|entity] [radius]` | Claim a permanent loader where you stand. Defaults: `crop` mode, radius 3. Passing a mode uses that mode's default radius (`crop`=3, `entity`=1); passing an explicit radius overrides it. |
  | `/chunkloader unclaim` | Release the loader you are currently standing inside. |
  | `/chunkloader mode crop\|entity` | Switch the loader you are standing in between crop mode (growth simulation, mobs despawn normally) and entity mode (holds mobs like a player standing there). |
  | `/chunkloader radius <n>` | Change the forceload radius (0–`MAX_RADIUS`) of the loader you are standing inside. |
  | `/chunkloader mineable <true\|false>` | Toggle whether the loader's egg can be mined. `true` = mineable (players can break and relocate); `false` = permanent (unbreakable). |

## Known limitations / deliberate flags (please read)
1. **The placed block cannot display the "Chunk Loader" name.** A vanilla dragon egg
   is a plain block with no block entity, and this mod deliberately adds none (that
   would violate the "no client-visible content" rule). Blocks cannot show a custom
   name without a block entity.
2. **Every placed dragon egg becomes a loader.** Because the egg isn't a distinct new
   item, the mod cannot tell "intentional chunk loader" from "decorative trophy". Every
   placed egg is claimed as mineable. (Accepted trade-off.)
3. **Vanilla idle particles on the egg cannot be suppressed.** Dragon eggs emit their
   own idle particles from client-side rendering code (`randomDisplayTick`); that is
   not controllable from the server.
4. **Mangrove propagules** require a real player within range even in loaded chunks,
   bypassing the random-tick system entirely — a hard vanilla limitation, not worked
   around.
5. **Placement detection is predictive.** Claiming is triggered from `interactBlock`
   when a player right-clicks a block face holding a dragon egg. The per-second
   verification self-heals any false positive (e.g. clicking a chest that opened
   instead of placing) within ~1 second. Eggs placed by dispensers/structures are not
   auto-claimed (rare edge case).
6. **CPU scales with radius.** Simulating growth at vanilla density over a large radius
   is proportionally heavier. Typical radius 3 is negligible; very large radii cost
   more (inherent to simulating more land).
7. **Works in both singleplayer and dedicated servers.** `fabric.mod.json` declares
   `"environment": "*"` and the mixin sits in the `common` section, so the mod loads in
   singleplayer/LAN (client-hosted integrated server) as well as on a dedicated server.

## Layout
```
src/main/java/dev/SufficientMuffin/chunkloader/
  ChunkLoaderMod.java        entry point, event wiring, command
  LoaderManager.java         claim/unclaim, forceload, growth, persistence
  LoaderEntry.java           one tracked loader (radius, mode, mineable)
  mixin/
    CommandManagerMixin.java                  /chunkloader command registration
    DragonEggBlockMixin.java                  cancels left-click teleport for loader eggs
    MinecraftServerMixin.java                 loader ticking on server tick
    MobEntityMixin.java                       despawn-budget awareness
    MobSpawnerLogicMixin.java                 spawner cooperation
    ServerPlayerInteractionManagerMixin.java  right-click cancel + conditional mining block
    SpawnHelperMixin.java                     spawn-helper coordination
src/main/resources/
  fabric.mod.json
  chunkloader.mixins.json
  data/minecraft/loot_table/blocks/dragon_egg.json   mineable egg drops itself
```
