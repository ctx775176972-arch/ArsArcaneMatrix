# Changelog

## Unreleased

- No unreleased changes.

## 0.5.0 - 2026-08-31

- Expanded Ars Arcane Matrix into a staged Source-powered production system,
  with an updated Ars Nouveau spell-book category and survival progression.
- Added the Processor, Smelter, and Crusher multiblocks with mixed-input batch
  processing, catalysts, guaranteed byproducts, metal dusts, redstone control,
  Dominion Wand logistics, structure previews, and JEI/Jade integration.
- Added the Source Stone Generator, Source Stone Furnace, Arcane Reaction
  Vessel, Advanced Imbuement Chamber, Formless Essence, and bulk Source Gem
  production through the Arcane Imbuement Core.
- Added one-million-capacity Arcane Source Jars, the 100-million-capacity Matrix
  Source Jar multiblock, Integrated Source Relays, and Source-aware advanced
  storage infrastructure.
- Added Wixie order automation: Order Pedestals, Crafting Guides, Pattern
  Providers, Order Terminals, automatic stock requests, parallel crafting,
  recipe matching modes, and advanced storage access.
- Added Storage Grid Directories and expansion modules for high-count item
  storage, plus tiered Starbuncle logistics management with per-Starbuncle
  filters, route inspection, recovery, protection, and high-throughput transfer.
- Added the Arcane Fluid Controller and installable Arcane Fluid Tanks, fluid
  upgrades, world-fluid collection, wireless transfer, fluid-aware crafting,
  and the Arcane Vacuum Hopper for items and experience.
- Added the data-driven Arcane Hunting Grounds and Alakarkinos expedition
  simulation for difficult-to-renew rewards without spawning or killing mobs.
- Added Capture as a tier-three glyph, including mounted-entity handling and a
  strengthened boss-only mode.
- Enhanced Whirlisprigs, Amethyst Golems, Drygmy Henges, and Starbuncles for
  automation while retaining Ars Nouveau-style interaction and progression.
- Added the Matrix Construction Wand and consistent empty-hand multiblock
  previews, including creative-mode construction support.
- Reorganized documentation, recipes, models, handheld transforms, textures,
  translations, CI checks, and compatibility behavior for the 0.5.0 release.

## 0.4.2 - 2026-08-04

- Added empty-hand translucent structure previews for the Arcane Matrix and
  Arcane Mine controllers.
- Changed previews to render only missing or invalid structure positions while
  recognizing configured block tags and valid Arcane Amplifier substitutions.
- Disabled previews for maximally complete structures and automatically closed
  an active preview when its final required block was placed.
- Added a dedicated Ars Arcane Matrix category to the Ars Nouveau spell book
  and simplified the project overview.

## 0.4.1 - 2026-08-02

- Removed an unreachable Imbuement Core batch-size guard identified by Qodana.
- Tightened non-null return contracts for the Imbuement Core block entity,
  update packet, and sided item handler without changing runtime behavior.

## 0.4.0 - 2026-08-02

- Added the Arcane Imbuement Core below existing Imbuement Chambers.
- Added configurable two-to-six-block vertical linking with pipe clearance.
- Added high-rate Source pulling and automatic Chamber supply.
- Added five-second bulk processing for multiple Lapis or Amethyst Blocks.
- Added the Lapis Block Imbuement recipe for nine Source Gems.
- Added sided automation, automatic downward output, redstone pause, persistent
  paid batches, particles, Jade status, JEI guidance, and spell-book
  documentation.
- Prevented compressed inputs from being processed by the connected Chamber and
  added recovery for stranded Source and bulk results.
- Changed Imbuement Core progress display from ticks to seconds.
- Replaced personal package and author identifiers with `dev.arsmatrix` and
  `xuemo3rd`, with an automated privacy check in every build.
- Updated the development dependency to Ars Nouveau 5.13.0 while retaining a
  5.12.1 runtime minimum.
- Completed the Ars Nouveau-inspired visual overhaul originally planned for
  0.5.0: the Matrix Core now uses a Conduit-like animated presentation, the
  Arcane Mine Core has dedicated high-resolution faces, and the Arcane
  Amplifier shares the Source Gem color language.
- Rebuilt the Arcane Imbuement Core as a non-full-cube pedestal, ring, and
  floating-crystal model, with its inventory appearance matching the placed
  block.
- Refined functional particles and silhouettes so the Matrix, Mine,
  Amplifier, and Imbuement devices remain visually distinct.
- Added an expanded, configurable Source input range for complete four-layer
  Arcane Mines. Complete Mines now directly draw from loaded Matrix Cores in
  range without loading additional chunks.
- Added a data-driven default Arcane Mine output blacklist. Nether Gold Ore is
  excluded by default so tagged Gold Ore output remains compatible with later
  raw-ore processing lines.
- Fixed the Arcane Imbuement Core mistaking a Source Gem placed in its connected
  Chamber for a completed bulk result. Ordinary elemental-essence recipes now
  keep their Source Gem input and receive Source without the Core reclaiming it.
- Added an empty-hand loose/compact output toggle to the Arcane Imbuement Core.
  Compact mode converts every four buffered Source Gems into one Source Gem
  Block, preserves remainders in a separate output buffer, and exposes the
  current mode through Jade and Ars Nouveau documentation.
- Added a dedicated Ars Arcane Matrix category to Ars Nouveau's current
  spell-book documentation and moved all Matrix, Mine, Amplifier, and
  Imbuement Core entries into it while retaining search and item-page links.
- Replaced the deprecated mod-list artwork with a temporary pixel-art icon
  based on the in-game Matrix Core's thin, offset frames and violet crystal,
  finished with restrained rune fragments, Source-light accents, and a pixel-art
  rendering of the Ars Arcane Matrix name.

## Planned 0.6.0 - Arcane Stabilization Core

- Add the Arcane Stabilization Core, a Source-powered device that keeps its
  configured chunks loaded only while sufficient Source is available.
- Make chunk loading an ongoing magical operating cost rather than a free or
  permanently active utility.
- Provide clear active, starved, paused, and unloaded-state feedback, with
  server-safe limits and configuration planned alongside the implementation.
- Document ownership, restart, dimension, and chunk-ticket behavior before the
  feature is considered release-ready.
- Skyblock-specific resource acquisition is not planned for this release.

## Planned 0.7.0 - Arcane Smelting Matrix

- Add the Arcane Smelting Matrix, an intentionally very expensive endgame
  multiblock for Source-powered raw-ore processing.
- Add an Arcane Source Reservoir and Reinforced Source Relay as endgame Source
  infrastructure for players without Beyond Dimensions. The Reservoir provides
  large local buffering, while the Relay provides high-throughput directional
  transfer within the same dimension.
- Keep both support blocks chunk-load neutral and same-dimension only. They must
  not become a substitute for cross-dimensional Source networks, and unloaded
  endpoints will suspend transfer instead of force-loading them.
- Target a configurable default Reservoir capacity of 50,000,000 Source and a
  configurable Relay throughput of 100,000 Source per second, enough to buffer
  and feed the planned two-to-three-Matrix endgame production line.
- Use Dominion Wand source-to-destination links for the reinforced relay and
  prevent cyclic relay networks from repeatedly moving the same Source.
- Consume substantial Source to resolve valid furnace-style raw-ore recipes
  internally and double their normal material output.
- Restrict doubling to eligible raw ores and compatible recipe results rather
  than arbitrary furnace inputs, preventing general-purpose item duplication.
- Balance one continuously operating endgame production line -- Arcane Mine,
  Source Gem production, ore processing, and Arcane Smelting Matrix together --
  around the output of two to three fully amplified Arcane Matrix Cores. With
  current defaults, this is a total demand of roughly 37,500 to 56,250 Source
  per second across the complete line, not per individual machine.
- Balance construction materials, Source cost, processing time, automation,
  buffering, and amplifier interaction during implementation.

## 0.3.0 - 2026-07-27

- Added the Arcane Mine, a Source-powered inverted-beacon multiblock with
  data-driven ore outputs.
- Added GUI-free ore whitelist and blacklist tuning through placed samples.
- Added Arcane Amplifiers for Matrix generation and Mine output scaling.
- Added rare Amplifier byproducts, non-blocking buffering, and Imbuement Chamber
  recycling.
- Added JEI and Jade integrations for recipes, structures, live state, tuning,
  requirements, links, and stop reasons.
- Added dedicated Arcane Amplifier pages to the Ars Nouveau spell-book
  documentation and Worn Notebook.
- Improved redstone pausing, material extraction, high-cost recipe handling,
  structure validation, and cross-dimensional loaded-container links.
