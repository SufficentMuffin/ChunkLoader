# Minecraft modding ecosystem — where things actually live

Notes from dissecting `#minecraft:enderman_holdable` → endermen picking mushrooms out of a farm.
Everything here is read-only archaeology: no build needed for any of it except `genSources`.

## The layers, bottom up

| layer | what it is | where it lives on this machine |
|---|---|---|
| obfuscated jars | the real game. `a.class`, `b.class`, `abc.class` | — |
| Mojang client jar | official client | `~/.gradle/caches/fabric-loom/<ver>/minecraft-client.jar` |
| Mojang server jar | a **bundler**: contains `META-INF/versions/<ver>/server-<ver>.jar` plus all libraries | `.../<ver>/minecraft-server.jar` |
| extracted server | the inner server jar from the bundler | `.../<ver>/minecraft-extracted_server.jar` |
| merged | client + extracted server, still obfuscated | `.../<ver>/minecraft-merged.jar` |
| mappings | name dictionaries (see below) | `.../<ver>/net.fabricmc.yarn.<ver>/mappings.jar` |
| **merged + yarn** | merge, remapped to readable names. What mods compile against, and what to `javap` | `.../minecraftMaven/net/minecraft/minecraft-merged/<ver>/minecraft-merged-<ver>-yarn....jar` |
| merged + intermediary | same, Fabric's stable names | `.../minecraft-merged-intermediary/<ver>/....jar` |
| Loader + Mixins | patches the vanilla jar at launch; no API required for many jobs | `fabric-server-launch.jar` (server) |
| datapacks / resource packs | pure data, valid in **vanilla** too. Tags, loot tables, recipes, models | `world/datapacks/`, jar's built-in `vanilla` pack |

## Three (four) naming spaces

1. **obfuscated** — `a`, `b`, `abc` (Mojang ships this)
2. **intermediary** — `net.minecraft.class_3481`, `field_22801`, `method_1234`. Fabric's *version-independent* ids.
   Mixins and refmaps resolve to these at runtime, so an intermediary-targeted injection works in production.
3. **yarn** — `BlockTags.ENDERMAN_HOLDABLE`. Human-readable, **changes between Minecraft versions** →
   never copy-paste source between versions without checking names.
4. **official (Mojang mappings)** — `net.minecraft.tags.BlockTags`. Mojang's own deobf, close to yarn.

Practical consequence: the jar you read may look different from the source you remember purely because
of the mapping layer. Constantly-pool strings are longer in yarn (`BlockTags.ENDERMAN_HOLDABLE` vs
`class_3481`), which is why the yarn-remapped jar is *bigger* than the obfuscated merged one.

## Which tool answers which question

| question | tool | example |
|---|---|---|
| what does this **data** say? | unzip + read the JSON | `zipfile.ZipFile(JAR).read("data/minecraft/tags/block/enderman_holdable.json")` |
| what does this **code** do? | `javap -p -c` = bytecode, branch offsets, called fields/methods | `javap -p -c -classpath <dir> net.minecraft.entity.mob.EndermanEntity$PickUpBlockGoal` |
| I want real Java | `./gradlew genSources` → `-sources.jar` (a build — run it yourself) | Loom generates it per project |
| which class contains X? | scan class bytes for a constant-pool string | `[n for n in z.namelist() if n.endswith(".class") and b"ENDERMAN_HOLDABLE" in z.read(n)]` |
| which build is live on the server? | match host file listing against local `build/libs` **by byte size** | `25032 B / 1024 = 24.45 KiB` |
| what happened in the world? | server logs, `world/level.dat` (gamerules), Ledger DB | greps for `[ChunkLoader]`, `/gamerule` lines |

`$` in a class name is the classfile notation for a nested class: `Outer$Inner.class` is a separate
physical file, and `javap` wants exactly that string. `Outer$Inner.class` in source is `Outer.Inner`.

## Worked example: endermen pick up blocks

```
net/minecraft/entity/mob/EndermanEntity.class                    22,824 B
  EndermanEntity$ChasePlayerGoal.class                            2,238
  EndermanEntity$TeleportTowardsPlayerGoal.class                  5,151
  EndermanEntity$PlaceBlockGoal.class                             4,889
  EndermanEntity$PickUpBlockGoal.class                            4,677
```

`PickUpBlockGoal.canStart()` gates on `GameRules.DO_MOB_GRIEFING`; `tick()` does

```
getstatic  BlockTags.ENDERMAN_HOLDABLE        // TagKey<Block>
invokevirtual BlockState.isIn(TagKey)          // is this block pickable?
ifeq       <abort>
invokevirtual World.removeBlock(pos, false)    // take it
```

`PlaceBlockGoal` also gates on `DO_MOB_GRIEFING` but reads **no tag** — that's why carrying and
putting back are separate code paths. Consequences:

- one datapack JSON changes pickup entirely; no mod, no Java
- `/gamerule mobGriefing false` disables **both** goals at the source
- `brown_mushroom` / `red_mushroom` are in that tag → endermen harvest mushroom farms by accident

## Datapack essentials (1.21+ folder names)

- paths: `data/<namespace>/tags/block/*.json`, `tags/item/`, `loot_table/`, `recipe/`, `advancement/`,
  `enchantment/`, `damage_type/`. **Singular since 1.21** — `tags/blocks` and `loot_tables` are dead.
- a pack file at the same path as the built-in `vanilla` pack wins by priority.
- **tags merge by default**: `{"values": ["x"]}` *unions* with the built-in list. To remove entries you
  must write `{"replace": true, "values": [...]}`. A pack that "does nothing" is almost always this.
- optional entries: `{"id": "mod:thing", "required": false}` → no error on a missing mod block.
- world packs live in `world/datapacks/` and must be enabled (`/datapack list`); same file works
  unchanged in vanilla single-player.

## Picking a mechanism

- change a **tag**, loot table, recipe, or add content that's data → **datapack** (vanilla-safe, hot-reloadable with `/reload`)
- change **vanilla logic** (a tick, a placement rule, a drop) → **mixin** into the class, or a Fabric API
  event when one exists
- **new** blocks/items/entities → registries (Fabric API or a mod loader)
- keep chunks ticking → forceload/loader; note forceloaded chunks are `ENTITY_TICKING` (level 31) and are
  already random-ticked by vanilla with nobody online (see `references/random-tick-coverage.md` in the
  `fabric-mod-dev` skill).
