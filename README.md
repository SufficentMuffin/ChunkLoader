# Simple Chunk Loader (Fabric, MC 1.21.11)

Same mod as before, rebuilt and re-pinned for **Minecraft 1.21.11** specifically,
using version numbers I verified directly against Fabric's Maven and official
blog post rather than guessing.

## What it does

- Adds a **Chunk Loader** block/item with **no crafting recipe** (only
  `/give` or creative).
- **Sneak + right-click** cycles its radius through `1, 2, 3, 4, 6, 8` chunks.
- Force-loads every chunk in that radius via `ServerWorld#setChunkForced`
  (same thing `/forceload` does).
- Its block entity ticks once a second and **manually simulates vanilla's
  random-tick behaviour** across those force-loaded chunks - this is the part
  forceload doesn't do by itself, and it's what actually makes cactus, crops,
  sugar cane, etc. keep growing with nobody nearby.
- Uses **Fabric API's `FabricBlockEntityTypeBuilder`** for registration, so
  no custom access widener file is needed anywhere in this project.

## Verified version numbers (as of Sept 2026)

| Component | Version | Source |
|---|---|---|
| Minecraft | 1.21.11 | - |
| Yarn mappings | `1.21.11+build.4` | maven.fabricmc.net |
| Fabric Loader | `0.18.1` | fabricmc.net's official 1.21.11 blog post |
| Fabric Loom (Gradle plugin) | `1.14.3` | maven.fabricmc.net, matches "Loom 1.14" recommendation in the blog post |
| Fabric API | `0.141.3+1.21.11` | modrinth.com/mod/fabric-api |

**Important context specific to this version:** Fabric has stated that
**1.21.11 is the last Minecraft version that will get Yarn mapping updates.**
Going forward (starting with Minecraft 26.1), Minecraft itself ships
unobfuscated, and Fabric mods are expected to migrate to Mojang's own
mappings instead of Yarn. That means this exact setup (Yarn + Loom remapping)
is specific to 1.21.11 and won't carry forward as-is to future Minecraft
versions - if you ever update past 1.21.11, expect a real migration, not just
a version bump. See: https://docs.fabricmc.net/develop/migrating-mappings/

If a lot of time has passed since this was written, double check these
numbers still resolve at https://fabricmc.net/develop/ before trusting them -
Fabric API in particular publishes new builds frequently.

## This version is standalone - no Fabric API required

Only **Fabric Loader** is needed on the server now, nothing else. This works
via two things instead of Fabric API's `FabricBlockEntityTypeBuilder`:

1. **`src/main/resources/chunkloader.accesswidener`** widens open
   `BlockEntityType$BlockEntityFactory`, a private nested interface that
   `BlockEntityType.Builder.create(...)` needs a lambda/method-reference to
   implement. This is wired into `build.gradle` via `loom.accessWidenerPath`
   and declared in `fabric.mod.json` so it also applies at runtime, not just
   compile time.
2. **`ModBlockEntities.java`** now calls vanilla's
   `BlockEntityType.Builder.create(...).build(null)` directly - the `null`
   is the DataFixerUpper `Type<?>` parameter vanilla's `build()` expects,
   which mods almost universally pass `null` for.

**One open caveat, stated plainly:** I verified `build(Type<?>)` (never a
no-arg version) is what vanilla's builder has required in every yarn build
I could directly confirm, back through 1.21.1 - but I could not get a
clean, direct confirmation of the exact signature specifically for
`1.21.11+build.4`. If the compiler tells you `build(null)` doesn't resolve,
that's the one line to revisit - try `build()` with no argument, or check
`BlockEntityType.Builder` in your local mapped sources for whatever the
current signature actually is. Nothing else in this standalone setup should
be affected either way.

## API changes since the 1.21.1 version of this mod

1.21.11's actual mapped API (verified via maven.fabricmc.net/docs/yarn-1.21.11+build.4/)
differs from 1.21.1 in a few real ways, not just cosmetic renames:

- **`World.isClient` is now a method, not a field**: call `world.isClient()`,
  not `world.isClient`.
- **`AbstractBlock#onStateReplaced` changed meaning**: since 1.21.5, it now
  runs *after* the block entity has already been removed, and receives the
  *old* state rather than the new one. It's back to the simpler
  `(BlockState state, ServerWorld world, BlockPos pos, boolean moved)`
  signature, but it's no longer useful for per-block-entity cleanup logic.
- **Block-entity-specific cleanup now belongs in `BlockEntity#onBlockReplaced(BlockPos, BlockState)`**,
  called while the block entity instance is still valid - that's where this
  mod now releases its forceload tickets.
- **NBT read/write was overhauled to a View-based system**: `writeNbt(NbtCompound, RegistryWrapper.WrapperLookup)` /
  `readNbt(...)` are gone; block entities now override
  `writeData(WriteView view)` / `readData(ReadView view)`
  (package `net.minecraft.storage`), using `view.putInt(key, value)` and
  `view.getInt(key, fallback)`.
- Separately, plain `NbtCompound` getters (e.g. `NbtCompound#getInt`) now
  return `Optional<Integer>` instead of a raw `int` - this mod doesn't touch
  `NbtCompound` directly anymore since it uses `ReadView`/`WriteView`
  instead, which still return plain primitives with a fallback parameter.

If you're comparing against the earlier 1.21.1 zip I gave you: these are the
concrete diffs in `ChunkLoaderBlock.java` and `ChunkLoaderBlockEntity.java`.

## Building it

I could not compile-test this myself - my environment has no network access
to Fabric's/Mojang's Maven repositories. You'll build it locally:

1. **JDK 21** installed (1.21.11 is confirmed as the last version requiring
   Java 21 specifically).
2. Get Gradle wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/`)
   from a fresh template generated at https://fabricmc.net/develop/template/
   for MC 1.21.11 - copy just those wrapper files into this folder, don't
   copy the template's example source files (its default `ExampleMod.java` /
   `ExampleMixin.java` will conflict with nothing here since we don't reuse
   its package, but no need to keep them - delete them if the generator adds
   any src files alongside the wrapper).
3. Make sure the Gradle wrapper itself is on **Gradle 8.14 or newer** in
   `gradle/wrapper/gradle-wrapper.properties` - Loom 1.14.x needs at least
   that (older Gradle versions will fail with a `Problems.forNamespace`-style
   error; too-new Gradle 9.x+ combined with an old Loom will also fail -
   8.14 is the safe middle ground for Loom 1.14.3 specifically).
4. Run:
   ```
   ./gradlew build
   ```
5. Built jar: `build/libs/chunkloader-1.0.0.jar`
6. Copy it, plus a matching **Fabric API** jar for 1.21.11, into your
   server's `mods/` folder.

## Usage in-game

1. `/give @p chunkloader:chunk_loader`
2. Place it near/inside your farm.
3. Right-click to check the current radius; sneak + right-click to cycle it.
4. Walk away - the farm should keep growing.

## Tuning

In `ChunkLoaderBlockEntity.java`:
- `RADIUS_OPTIONS` - preset radii (chunks) cycled via sneak-click.
- `RANDOM_TICKS_PER_CHUNK` - random-tick rolls per chunk per cycle (vanilla
  default `random_tick_speed` is 3).
- `SIMULATION_INTERVAL_TICKS` - how often (game ticks) the simulation runs.
