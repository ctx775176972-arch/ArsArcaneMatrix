# Ars Arcane Matrix

Ars Arcane Matrix is a NeoForge addon for Ars Nouveau that adds the **Arcane Matrix Core**, a configurable endgame Source generator built around a conduit-style multiblock structure.

The mod includes English and Simplified Chinese localization, Ars Nouveau documentation integration, exact Source display support for ArsNumericHUD, and standard Ars Nouveau Source capability output.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.0 or newer
- Ars Nouveau 5.12.1 or newer
- The dependencies required by Ars Nouveau

Optional:

- ArsNumericHUD 1.0.0 or newer for an exact numeric Source display

## Installation

1. Install Minecraft 1.21.1 with NeoForge 21.1.0 or newer.
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

- 4 Crying Obsidian
- 2 Nether Stars
- 1 Wilden Tribute
- 1 Heart of the Sea

Source cost: **10,000**

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
