# Farlands Pearl Cannon

A standalone Fabric 26.2 companion mod for **Farlands Reforged**.

Build a redstone-powered ender pearl cannon, choose North/South/East/West or one of the four corners, and launch to `±12,550,650` — about 175 blocks before the classic Far Lands distance so players can approach and see the terrain from a distance.

Farlands Reforged is optional: without it, the cannon still works and sends players to the same far-out coordinates; the terrain will simply be vanilla/whatever the current worldgen provides.

## V1 gameplay

- Symmetric multiblock cannon: a solid iron deck, an obsidian/crying-obsidian barrel with a flared muzzle, mirrored redstone control circuits wired out from the core, a feed lane of dispensers and ram pistons, plus three custom blocks and an ignition lever.
- `Cannon Blueprint Projector` auto-detects a nearby Cannon Core and guides the build one step at a time, with a HUD step card and an in-world beam on the next block. Works in either hand.
- A `Cannon Field Manual` written book is handed to you the first time you use the projector on a core (sneak-use the projector in the air to get another copy).
- Right-click the `Direction Dial` (or sneak-click the Core) to open a compass menu and pick one of 8 launch headings.
- Load an ender pearl, add 3 fuel items (`Blaze Powder` or `Eye of Ender`), then flip the ignition lever (or feed the core any redstone) to fire. The cannon animates — redstone lights up, pistons cycle, a charge races down the barrel — and launches the nearest player.
- 5 minute cooldown.
- No lightning/thunder and no dragon-breath style particles.
- Successful launch grants `How did I get here...`.
- Player receives an `Anchor Pearl` to return to their last sleep spawn/world spawn (dropped at their feet if their inventory is full, so it's never lost).
- `/farlandscannon return` is a safety net that **only works while you're out in the Far Lands** (configurable `returnCommandMinDistance`), so it can't be abused as a free teleport home. It sends you to your bed or world spawn.

## Quick start

The Cannon Core is intentionally a placeable block, not a handheld tool. In Creative, open the `Farlands Pearl Cannon` tab or search `blueprint`/`core`.

Place the core on the ground first, aimed the way it should fire. Then just **hold the `Cannon Blueprint Projector`** (either hand) near the core — the guide turns on automatically, no linking needed. It walks you through the build **one stage at a time** (Foundation deck → Breech & Chamber → Barrel → Loaders & Feed → Left Circuit → Right Circuit → Sights → Ignition Lever) so you are never looking at the whole thing at once:

- A glowing wireframe marks each block in the current stage. White = place here, green = done, red = wrong block.
- A bright **beam** marks the single next block to place, and an arrow shows which way it must face.
- Finished blocks fade to calm green pips, and a **faint outline of the whole cannon** is always sketched so you can see the full footprint and where everything is heading.

A **step card** on the right of the screen shows which step you are on, the exact next block, and just that stage's materials with have/needed counts (green when you have enough). Hold the projector in your off-hand so your main hand stays free to place blocks.

Completing the structure triggers a celebration burst plus the `Far Traveler` advancement.

Sneak-use the projector on the core to print the full material list in chat, or read the `Cannon Field Manual` book for the full walkthrough (both are generated from the actual structure, so they never drift).

Once the structure is complete (the final step is an **ignition lever** placed on top of the core):

1. **Right-click the `Direction Dial`** to open the aim menu — a compass of 8 headings — and click one (your current heading is highlighted, and a line at the bottom confirms the new pick). Cardinals (N/S/E/W) fire straight down one axis and keep your other coordinate; corners (NE/NW/SE/SW) launch you to a far corner where two Far Lands walls meet — that's how you reach each quadrant/edge. (Sneak-clicking the Core opens the same menu.)
2. Use an ender pearl on the core to load it.
3. Use `Blaze Powder` or an `Eye of Ender` on the core three times to fuel it.
4. Stand by the cannon and **flip the ignition lever** — any redstone signal reaching the core triggers it (lever, redstone block, button, etc.).
5. The machine comes alive: the redstone lights up, the pistons cycle, and a charge races down the barrel. You launch the moment it reaches the muzzle. The **nearest player** within 8 blocks rides the shot.

A 5-minute cooldown applies between shots. Completing the structure grants the `Far Traveler` advancement.

## Recipes

`Cannon Blueprint Projector`

```text
Amethyst Shard
Redstone Torch + Map + Redstone Torch
Paper
```

`Farlands Cannon Core`

```text
Obsidian + Eye of Ender + Obsidian
Redstone + Lodestone + Redstone
Obsidian + Copper Block + Obsidian
```

`Pearl Chamber`

```text
Glass + Copper Ingot + Glass
Copper Block + Ender Pearl + Copper Block
Glass + Redstone + Glass
```

`Direction Dial`

```text
Amethyst Shard
Redstone + Compass + Redstone
Quartz
```

## Config

Generated at `config/farlands-pearl-cannon.json`.
