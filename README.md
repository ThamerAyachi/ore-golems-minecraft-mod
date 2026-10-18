# Ore Golems Expansion (`oregolems`)

A Minecraft **1.21.11** (Forge `61.2.x`, Java 21) mod that adds 8 miniature **Copper-Golem-style worker golems** crafted from vanilla ore blocks. Every golem uses the official 1.21.11 Copper Golem 3D geometry, custom 10-shade pixel-art textures, and automatically spawns with its own **Copper Chest**.

## Summoning Golems

Place a **Carved Pumpkin** or **Jack o'Lantern** directly on top of any supported Ore Block (or use its Spawn Egg from the **Ore Golems Expansion** Creative Tab):

| Base Ore Block | Golem Variant | Role & Behavior |
| :--- | :--- | :--- |
| **Block of Coal** | **Coal Golem** (`coal_golem`) | **Smelter & Torch Placer** — Refuels active Furnaces, Blast Furnaces, and Smokers within 10 blocks (+1600 burn ticks) and places torches when block light drops below `4`. |
| **Block of Iron** | **Mini Iron Golem** (`mini_iron_golem`) | **Base Guard & Item Magnet** — Hunts nearby hostile mobs and vacuums dropped items within 8 blocks into its Copper Chest or nearby Chests/Barrels. |
| **Block of Gold** | **Gold Golem** (`gold_golem`) | **Redstone & Barter Assistant** — Periodically presses nearby buttons and flips levers. Right-click with a **Gold Ingot** for a 5-second Piglin barter roll. |
| **Block of Emerald** | **Emerald Golem** (`emerald_golem`) | **Automated Farmer** — Harvests mature crops (Wheat, Carrots, Potatoes, Beetroots, Nether Wart, Sweet Berries) in a 13×13 area, replants seeds automatically, and deposits harvests into its Copper Chest. |
| **Block of Redstone** | **Redstone Golem** (`redstone_golem`) | **Mobile Redstone Power Source** — Right-click (empty hand) to toggle between **Follow/Wander** and **Stay/Power** mode (emits level-15 redstone power). |
| **Block of Lapis Lazuli** | **Lapis Golem** (`lapis_golem`) | **XP Collector & Enchanter Helper** — Absorbs XP orbs within 12 blocks; right-click with a **Glass Bottle** to bottle stored XP into a **Bottle o' Enchanting**. |
| **Block of Diamond** | **Diamond Golem** (`diamond_golem`) | **Automated Tunnel Miner** — Automatically mines a 1×2 tunnel forward (plus exposed ores on tunnel walls) and deposits mined blocks into its home chest. Right-click with a Pickaxe or empty hand to redirect/pause. |
| **Block of Netherite** | **Netherite Golem** (`netherite_golem`) | **Heavy Automated Miner** — Fire & lava immune; mines 2× faster (`10 ticks`/block) with knockback resistance and higher armor. |

## What's New in v1.2.0

- **2-Second (`40`-Tick) Anti-Stuck AI Timeout & Target Cooldown**:
  - If any golem cannot reach or complete its current task within **2 seconds**, it cancels that order, temporarily blacklists the unreachable block (`ignoreBlockTemporarily`), and immediately moves on to another task or wanders.
- **Smart Miner Golem Navigation & Auto-Start**:
  - **Diamond** and **Netherite Golems** now start mining automatically upon spawning and can be toggled/redirected by right-clicking with a Pickaxe or empty hand.
  - Fixed the 1-block advance stall (`PathNavigation` `accuracy=0` + `MoveControl`), added automatic Cobblestone floor bridging across gaps, broadened mineable tunnel blocks (Dirt, Gravel, Tuff, Dripstone, etc. + adjacent exposed ores), and added automatic 90°/180° rotation when hitting unbreakable obstacles.
- **Partial-Block Height Fix (`getLogicalBlockPos`) & Adjacent Chest Spawning**:
  - Fixed `Y`-coordinate truncation when standing on Copper Chests (`0.875` height) or Farmland (`0.9375` height), and updated golem spawning so golems spawn on solid ground right next to their paired Copper Chest.

## Building from Source

Requires **Java 21**.

```powershell
.\gradlew.bat build
```

The compiled mod JAR is generated in `build/libs/oregolems-1.2.0.jar`.
