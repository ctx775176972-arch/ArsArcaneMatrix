# Ars Arcane Matrix

Ars Arcane Matrix is a NeoForge addon for Ars Nouveau that adds the **Arcane Matrix Core**, a configurable endgame Source generator, and the **Arcane Mine**, a Source-powered, data-driven ore producer.

The mod includes English and Simplified Chinese localization, Ars Nouveau documentation integration, exact Source display support for ArsNumericHUD, and standard Ars Nouveau Source capability output.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.205 or newer
- Ars Nouveau 5.12.1 or newer
- The dependencies required by Ars Nouveau

Optional:

- ArsNumericHUD 1.0.0 or newer for an exact numeric Source display

## Installation

1. Install Minecraft 1.21.1 with NeoForge 21.1.205 or newer.
2. Install Ars Nouveau 5.12.1 or newer and all dependencies required by that version.
3. Place the Ars Arcane Matrix JAR in the instance's `mods` directory.
4. Optionally install ArsNumericHUD for exact Source values.
5. For multiplayer, install the same Ars Arcane Matrix version and required dependencies on both the server and every connecting client.
6. Start the game and enter a world. The server configuration will be created in the world's `serverconfig` directory.

Do not install multiple versions of Ars Arcane Matrix at the same time.

## Arcane Matrix Core

The Arcane Matrix Core stores generated Source internally and exports it to nearby Ars Nouveau Source containers and other compatible Source targets.

Default values:

| Setting | Default |
| --- | ---: |
| Internal capacity | 10,000,000 Source |
| Output range | 5 blocks |
| Base generation | 1,000 Source/second |
| Generation per additional frame | 250 Source/second |
| Minimum frames | 16 |
| Maximum frames | 42 |
| Maximum output | 10,000 Source/second |

With all 42 frame positions filled, the default production rate is 7,500 Source per second.

### Structure

Place the Arcane Matrix Core at the center of three mutually perpendicular 5×5 rings. Source Gem Blocks placed in the 42 valid ring positions count as frames.

- At least 16 valid frames are required by default.
- Additional valid frames increase Source generation.
- The structure is checked once per second.
- An incomplete structure neither generates nor exports Source.
- Completing the structure plays an activation sound and particle effect.

The full structure can be viewed in the Ars Nouveau Worn Notebook and spell-book documentation.

Horizontal layers, viewed from above (`F` = valid frame position, `C` = core):

```text
Y = -2 and Y = +2       Y = -1 and Y = +1       Y = 0
  F                       F                     FFFFF
  F                                             F   F
FFFFF                   F   F                   F C F
  F                                             F   F
  F                       F                     FFFFF
```

Only Source Gem Blocks placed at these positions count toward the frame total. Blocks outside these positions are ignored.

### Controls

- Right-click the core with an empty hand to start or stop generation.
- Shift-right-click with an empty hand to display structure, frame, Source, generation, and output status.
- Stopping the core pauses generation but does not stop stored Source from being exported while the structure remains complete.

The block requires a diamond or netherite pickaxe to drop when mined.

## Crafting

The Arcane Matrix Core is crafted with an Ars Nouveau Enchanting Apparatus.

Reagent:

- 1 Arcane Core

Pedestal items:

- 1 Agronomic Sourcelink
- 1 Alchemical Sourcelink
- 1 Mycelial Sourcelink
- 1 Vitalic Sourcelink
- 1 Volcanic Sourcelink
- 1 Nether Star
- 1 Wilden Tribute
- 1 Conduit

Source cost: **10,000**

## Arcane Mine

The Arcane Mine converts Sourcestone-family materials and stored Source into ore
blocks. Ore outputs are loaded from data-pack JSON files, with conservative
automatic fallback rules for unconfigured `c:ores/*` item tags.

### Structure

Place the Arcane Mine Core at the bottom and build complete square layers above
it. The default configured sizes are 3×3, 5×5, 7×7, and 9×9. Every layer uses a
Source Gem Block at its center and four corners; all other positions use
Sourcestone or Smooth Sourcestone.

- Layers must be complete and continuous from the core upward.
- A complete first layer activates the mine automatically; breaking the
  continuous structure stops it automatically.
- One layer unlocks common ores, two layers unlock intermediate ores, and four
  layers unlock the precious pool: diamond, emerald, and Ancient Debris by
  default.
- The active mine continuously emits particles. Successful production produces
  a pulse at the core and at a loaded bound output container.

### Materials and automation

Default material values:

| Material | Points |
| --- | ---: |
| Sourcestone | 1 |
| Source Gem | 8 |
| Source Gem Block | 32 |

Horizontal item capability faces accept materials. The bottom item capability
face exposes completed ore. The core accepts standard Ars Nouveau Source
capabilities and, while active, pulls Source from Ars Nouveau special providers
within its configured range. This includes Beyond Dimensions Source Pathways;
the pathway may draw from a dimensional network located in another dimension,
but the pathway block and mine core must be loaded in the same dimension.

Dominion Wand links follow Starbuncle transport order:

- Container first, then the core: bind a material input ("take").
- Core first, then a container: bind the ore output ("store").
- Sneak-use the wand on the unselected core to clear all links.

The clicked core face does not determine the link role. Linked containers can
be in other dimensions. Both the core and target container chunks must already
be loaded; the mod never force-loads chunks.

The core is crafted with an Enchanting Apparatus using an Arcane Core reagent
and eight pedestal items: Coal, Copper, Iron, Gold, Redstone, Emerald, Lapis,
and Diamond storage blocks. The recipe costs **10,000 Source**.

### Ore data

Ore rules are loaded from:

```text
data/<namespace>/arcane_mine/*.json
```

Example:

```json
{
  "output": {
    "tag": "c:ores/diamond",
    "count": 1
  },
  "required_layers": 4,
  "material_points": 128,
  "source_cost": 12800,
  "weight": 2,
  "enabled": true
}
```

Explicit JSON rules override automatic tag discovery. A disabled rule suppresses
the matching automatic output. Ancient Debris is enabled in the four-layer
precious pool by default at weight 1, costing 512 material points and 51,200
Source per block.

## Configuration

The server configuration is generated at:

```text
<world>/serverconfig/ars_arcane_matrix-server.toml
```

Available options:

```toml
[matrix_core]
sourceCapacity = 10000000
outputRange = 5
baseGenerationPerSecond = 1000
generationPerAdditionalFrame = 250
minimumFrameBlocks = 16
maximumFrameBlocks = 42
maxOutputPerSecond = 10000

[arcane_mine.structure]
layerSizes = [3, 5, 7, 9]
structureCheckInterval = 20

[arcane_mine.operation]
sourceCapacity = 1000000
sourceInputRange = 5
maxSourceInputPerSecond = 10000
materialPointCapacity = 1024
maxMaterialContainers = 4
cooldownTicksByLayer = [400, 300, 200, 100]
allowCrossDimension = true
autoDiscoverOres = true

[arcane_mine.effects]
enableParticles = true
particleIntervalTicks = 10
particleDensity = 1.0
enableSounds = true
```

The physical structure contains at most 42 valid frame positions. If the configured minimum exceeds the configured maximum, the effective minimum is capped to the maximum.

## Compatibility

### Required

- **NeoForge:** mod loader and capability platform.
- **Ars Nouveau:** recipes, Source capability, Source targets, Source Gem Blocks, particles, sounds, and documentation systems.

### Optional

- **ArsNumericHUD:** displays the Matrix Core's exact stored Source, frame count, and current generation rate.

### Built-in interoperability

- Outputs through the standard Ars Nouveau Source capability.
- Supports Source Jars and other targets accepted by Ars Nouveau's Source network utilities.
- Provides Ars Nouveau tooltip information.
- Adds an entry and multiblock preview to the Worn Notebook.
- Adds an entry, recipe, structure page, search result, and Ctrl item-page link to the current spell-book documentation system.

No mixins are used. Mods that interact through the standard Ars Nouveau Source interfaces should remain compatible.

## Roadmap

Development is planned in the following order. Integrations remain optional
dependencies and must not prevent the mod from loading when they are absent.

### Phase 1: Ore tuning and JEI

- Add ore samples or lenses that focus the Arcane Mine on a selected ore rule.
- Keep structure-layer requirements and apply configurable focused-production
  Source and material multipliers.
- Extend ore-rule JSON with tuning controls.
- Add a JEI Arcane Mine category generated from the active JSON and tag rules,
  showing output, layer requirement, material points, Source cost, count range,
  weight, and whether the rule was automatically discovered.

### Phase 2: Redstone control and Jade

- Add configurable redstone modes for the Matrix Core and Arcane Mine.
- Add comparator output for Source storage, material points, cooldown, or output
  blockage.
- Stop safely when the output is blocked instead of consuming resources.
- Add optional Jade providers for structure state, Source, material points,
  target ore, cooldown, connection counts, and the current reason production is
  stopped. Detailed information will be shown while holding Shift.
- Do not expose exact cross-dimensional container coordinates through Jade.

### Phase 3: Upgrade components

- Add limited upgrade slots for capacity, transfer speed, efficiency, cooldown,
  and ore weighting.
- Keep upgrades mutually competitive so a single machine cannot maximize every
  statistic at once.
- Make upgrade limits and effects configurable.

### Phase 4: Byproducts

- Add an Arcane Slag or dissipated-crystal byproduct.
- Reuse byproducts for tuning lenses, upgrade components, structure materials,
  or limited Sourcestone recovery.
- Avoid a lossless loop that creates unlimited Source or materials.

### Phase 5: Structure specialization

- Add replaceable structure nodes that specialize the mine toward Overworld,
  Nether, End, deep-level, or modded ores.
- Define node effects and compatible ore groups through tags and data-pack JSON.
- Preserve the existing layer progression while allowing structures to develop
  distinct roles.

EMI support is a follow-up compatibility target. JEI and EMI displays should
share one internal presentation model so data-pack rules remain consistent
across both viewers.

## Building from Source

This project uses the Gradle wrapper and a Java 21 toolchain.

On Windows:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```bash
./gradlew build
```

The built JAR is written to `build/libs`.

## License

Ars Arcane Matrix is licensed under the [MIT License](LICENSE).

Ars Nouveau, Minecraft, NeoForge, and other referenced projects retain their respective licenses and trademarks.
