# Ars Arcane Matrix

Ars Arcane Matrix is a NeoForge addon for Ars Nouveau that adds the **Arcane Matrix Core**, a configurable endgame Source generator, and the **Arcane Mine**, a Source-powered, data-driven ore producer.

The mod includes English and Simplified Chinese localization, Ars Nouveau
documentation integration, optional JEI and Jade integration, exact Source
display support for ArsNumericHUD, and standard Ars Nouveau Source capability
interoperability.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.205 or newer
- Ars Nouveau 5.12.1 or newer
- The dependencies required by Ars Nouveau

Optional:

- ArsNumericHUD 1.0.0 or newer for an exact numeric Source display
- JEI for Arcane Mining recipes, structure requirements, costs, amplifier use,
  and the amplifier recycling recipe
- Jade for live structure, resource, cooldown, tuning, link, byproduct-buffer,
  and stop-reason information

## Installation

1. Install Minecraft 1.21.1 with NeoForge 21.1.205 or newer.
2. Install Ars Nouveau 5.12.1 or newer and all dependencies required by that version.
3. Place the Ars Arcane Matrix JAR in the instance's `mods` directory.
4. Optionally install JEI, Jade, or ArsNumericHUD for their integrations.
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
| Maximum generation | 100,000 Source/second (configurable) |
| Minimum frames | 16 |
| Maximum frames | 42 |
| Maximum output | 100,000 Source/second |

With all 42 frame positions filled, the default production rate is 7,500 Source per second.
Replacing the six axial vertices with Arcane Amplifiers raises this to 18,750
Source per second. Each amplifier adds 25% of the unamplified rate.

### Structure

Place the Arcane Matrix Core at the center of three mutually perpendicular 5×5 rings. Source Gem Blocks placed in the 42 valid ring positions count as frames.

- At least 16 valid frames are required by default.
- At least one of the three 5×5 rings must be complete. Sixteen scattered valid
  blocks do not form the Matrix.
- Additional valid frames increase Source generation.
- Up to six Arcane Amplifiers are recognized at the six axial vertices. They do
  not require a facing direction and continue to count as valid frames.
- The structure is checked once per second.
- A complete structure starts automatically.
- An incomplete structure neither generates nor exports Source.
- Completing the structure plays an activation sound and particle effect.

The full structure can be viewed in the Ars Nouveau Worn Notebook and spell-book documentation.

### Optional integrations

- JEI adds an Arcane Mining category for data-driven ore outputs, including
  required structure layers, material-point equivalents, Source cost, and the
  Arcane Mine Core catalyst.
- Jade displays live Matrix and Mine structure state, Source storage,
  production, material buffer, cooldown, and container-link information.
- Both integrations are optional; Ars Arcane Matrix still loads without either mod.

Horizontal layers, viewed from above (`F` = valid frame position, `C` = core):

```text
Y = -2 and Y = +2       Y = -1 and Y = +1       Y = 0
  F                       F                     FFFFF
  F                                             F   F
FFFFF                   F   F                   F C F
  F                                             F   F
  F                       F                     FFFFF
```

Source Gem Blocks and valid vertex Arcane Amplifiers count toward the frame
total. Blocks outside these positions are ignored.

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
Source Gem Block at its center and four corners; an Arcane Amplifier may replace
the center block. All other positions use Sourcestone or Smooth Sourcestone.

- Layers must be complete and continuous from the core upward.
- A complete first layer activates the mine automatically; breaking the
  continuous structure stops it automatically.
- A redstone signal at the core pauses production. With no signal, a complete
  mine runs automatically, preserving the behavior of existing installations.
- While redstone-paused, the mine consumes no Source or materials and does not
  pull from linked material containers. Its existing cooldown continues to
  count down, and a faint dark-red particle marks the paused core.
- One layer unlocks common ores, two layers unlock intermediate ores, and four
  layers unlock the precious pool: diamond, emerald, and Ancient Debris by
  default.
- The active mine continuously emits particles. Successful production produces
  a pulse at the core and at a loaded bound output container.
- Each of the four center-column Arcane Amplifiers adds one ordinary ore to a
  production cycle and raises both Source and material costs by 50%.
- A full four-layer mine has a configurable 1% chance per successful cycle to
  produce exactly one Arcane Amplifier as a separate byproduct. This byproduct
  is never multiplied by installed amplifiers. Amplifiers that cannot enter the
  output container are buffered separately up to one stack and do not block
  ordinary ore production.
- A surplus Arcane Amplifier can be recycled in an Imbuement Chamber with 2,000
  Source to produce four Source Gem Blocks.

### In-world ore tuning

Ore blocks can be hung directly below completed structure layers without
replacing any structural block:

- Blocks below the four layer corners form the whitelist.
- Blocks below the north, south, east, and west edge centers form the blacklist.
- If at least one valid whitelist entry exists, only matching rules remain
  eligible. The blacklist is applied afterward and always takes priority.
- Duplicate samples do not change weights, incomplete layers are ignored, and
  tuning never bypasses a rule's required layer count.
- Samples match exact output items or shared item tags such as `c:ores/iron`,
  allowing compatible modded ores to tune the same rule.

The active mine cycles through recognized samples instead of rendering every
beam simultaneously. Whitelist samples use pale particles and blacklist
samples use dark-red particles.

### Materials and automation

Default material values:

| Material | Points |
| --- | ---: |
| Sourcestone | 1 |
| Source Gem | 32 |
| Source Gem Block | 128 |

The configured material buffer is a normal prefill limit. If a selected recipe
costs more than that limit after amplification, the runtime capacity expands to
that recipe's cost plus enough room for one material unit, so old configurations
and mixed material values cannot permanently deadlock the Mine. Linked material
containers are drained in batches up to the current cycle's missing points.

#### Recommended Lapis cycle

The intended mid-to-late-game material loop is:

```text
Matrix Source
  -> Arcane Mine produces Lapis Ore
  -> break the ore with Fortune
  -> imbue each Lapis for 500 Source into a Source Gem
  -> return enough Source Gems to replenish the Mine
```

Only the portion needed to replace spent material points has to be imbued; the
remaining Lapis can be stored or used elsewhere. This loop is material-positive
even before Fortune, but it is not free: the Matrix must continuously supply
Source for both ore production and imbuement.

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
maxGenerationPerSecond = 100000
minimumFrameBlocks = 16
maximumFrameBlocks = 42
maxOutputPerSecond = 100000
amplifierBonusPerBlock = 0.25

[arcane_mine.structure]
layerSizes = [3, 5, 7, 9]
structureCheckInterval = 20

[arcane_mine.operation]
sourceCapacity = 1000000
sourceInputRange = 5
maxSourceInputPerSecond = 100000
outputBonusPerAmplifier = 1
costIncreasePerAmplifier = 0.5
amplifierByproductChance = 0.01
sourcestonePoints = 1
sourceGemPoints = 32
sourceGemBlockPoints = 128
materialPointSourceEquivalent = 15.625
materialPointCapacity = 4096
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

The physical structure contains at most 42 valid frame positions. At least one
complete 5×5 ring is always required before the Matrix can form; after that,
valid blocks in the other two rings increase generation individually. If the
configured minimum exceeds the configured maximum, the effective minimum is
capped to the maximum.

## Compatibility

### Required

- **NeoForge:** mod loader and capability platform.
- **Ars Nouveau:** recipes, Source capability, Source targets, Source Gem Blocks, particles, sounds, and documentation systems.

### Optional

- **ArsNumericHUD:** displays the Matrix Core's exact stored Source, frame count, and current generation rate.
- **JEI:** displays Arcane Mining rules, base costs, structure requirements,
  amplifier behavior, and the amplifier recycling recipe.
- **Jade:** displays live Matrix and Mine state, current requirements, stop
  reasons, links, tuning counts, and buffered amplifier byproducts.

### Built-in interoperability

- Outputs through the standard Ars Nouveau Source capability.
- Supports Source Jars and other targets accepted by Ars Nouveau's Source network utilities.
- Pulls through Ars Nouveau special Source providers, including loaded Beyond
  Dimensions Source Pathways.
- Provides Ars Nouveau tooltip information.
- Adds an entry and multiblock preview to the Worn Notebook.
- Adds an entry, recipe, structure page, search result, and Ctrl item-page link to the current spell-book documentation system.

No mixins are used. Mods that interact through the standard Ars Nouveau Source interfaces should remain compatible.

## Current 0.2.0 status

The planned 0.2.0 gameplay set is complete:

- Conduit-like scalable Arcane Matrix formation, generation, Source output, and
  six visible amplifier positions
- Inverted-beacon Arcane Mine progression with data-driven and automatically
  discovered tagged ores
- GUI-free whitelist and blacklist tuning through placed ore samples
- Mine-only redstone pause control; the Matrix remains automatic
- Four visible Mine amplifier positions, dynamic full-amplifier pacing, and
  high-cost material-buffer protection
- Rare amplifier byproducts, a separate non-blocking output buffer, and
  Imbuement Chamber recycling
- Loaded cross-dimensional material and output links using Dominion Wand
  Starbuncle-style connection order
- Optional JEI and Jade integration with no hard dependency

The next release work is stabilization rather than additional machine blocks:

- Complete fresh-world, dedicated-server, chunk-reload, and long-duration tests
- Verify optional-mod combinations: JEI only, Jade only, both, and neither
- Keep English and Simplified Chinese keys synchronized and accept reliable
  community translations
- Consider EMI support after the JEI presentation model is stable
- Consider structure specialization only if in-world ore tuning proves
  insufficient in real modpacks

Matrix redstone control, comparator output, GUI upgrade slots, and additional
input/output controller blocks are not currently planned. Existing vanilla
redstone, containers, Dominion Wand links, placed tuning samples, and visible
Arcane Amplifiers cover those roles without adding GUIs.

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
