# Flowframe

A server-side Minecraft mod for version 1.21.1 that adds various Quality of Life features for Minecraft servers.

**Author:** HelluPeptic

## Features

### 1. **Creeper Block Protection**
- Creepers can no longer destroy blocks when they explode
- Players can still take damage from creeper explosions
- Keeps your builds safe from creeper damage

### 2. **Phantom Spawn Mechanics**
- Phantoms no longer spawn in the Overworld
- Phantoms now spawn in the Nether with the same mechanics as they had in the Overworld
- Still requires players to avoid sleeping to trigger spawns

### 3. **End Portal Toggle Command**
- Use `/endtoggle` to enable or disable your ability to enter End portals
- Useful for preventing accidental portal entries
- Setting is per-player and persists during the session

### 4. **Path Block Speed Boost**
- Players move 30% faster (by default) when walking on path blocks (Dirt Path)
- Speed boost continues for 1 second after leaving the path block (grace period)
- Speed multiplier is configurable (see commands below)

### 5. **Crop Trampling Protection**
- Farmland blocks can no longer be trampled by entities falling or jumping on them
- Protects your crops from accidental damage by players, mobs, and items
- Entities still take normal fall damage when landing on farmland

### 6. **Server Restart Defaults**
- Daylight cycle automatically enabled on every server restart
- Weather cycle automatically enabled on every server restart
- No need to manually re-enable these game rules

### 7. **Spawner System**
- Create custom mob spawners in defined areas with entity limits and custom names
- Spawners persist across server restarts and track spawned entities properly  
- Mobs spawn at the same Y-level as the spawner (not at the bottom like a cylinder)
- Supports all living entity types with full tab completion for mob IDs

### 8. **Potion Extension**
- Drinking potions automatically last 2 hours instead of their normal duration
- Only affects potions consumed by drinking (not tipped arrows, guardian effects, beacon effects, etc.)
- This makes potions much more useful for long-term activities
- Exceptions: Poison, Instant Damage, and Weakness retain their normal durations

### 9. **Player Riding System** 
- Right-click another player to ride on their head

### 11. **Town System**
- Create and join towns to identify community membership
- Town prefixes appear in chat and above nametags
- Main command: `/town <create|disband|join|leave|edit|info>

## Commands

### `/sit`
- **Permission:** None (any player)
- **Description:** Sit down on the ground with the Minecraft sitting animation
- **Usage:** Simply type `/sit` to sit down, shift to stand up
- **Requirements:** Must be standing on a solid block
- **Notes:** Automatically stand up when disconnecting from server

## Player Interactions

### **Player Riding**
- **Action:** Right-click another player
- **Effect:** Ride the clicked player (piggyback style) or join their riding tower
- **Towers:** Can create multiple-player towers by right-clicking players who are already being ridden
- **Dismount:** Bottom player sneaks to dismount the entire tower
- **Restrictions:** 
  - Cannot ride a player who is sneaking
  - Cannot create riding loops (ride someone who is already riding another player)

### `/endtoggle`
- **Permission:** None (any player)
- **Description:** Toggles your ability to enter End portals
- **Usage:** Simply type `/endtoggle` to toggle on/off

### `/flowframe pathblocksspeed <percentage>`
- **Permission:** Operator level 2
- **Description:** Configure the speed boost percentage for path blocks
- **Usage:** `/flowframe pathblocksspeed 30` for 30% speed boost (default)
- **Range:** 0-1000 (percentage values)
- **Examples:**
  - `/flowframe pathblocksspeed 50` - 50% faster (1.5x speed)
  - `/flowframe pathblocksspeed 100` - 100% faster (2x speed)
  - `/flowframe pathblocksspeed 0` - No speed boost

### `/flowframe spawner` Commands
- **Permission:** Operator level 2
- **Description:** Manage custom mob spawners

#### `/flowframe spawner add <name> <radius> <limit> <interval> <entityName> <mobID>`
- **name:** Unique identifier for the spawner
- **radius:** Spawn radius around the spawner location (1-100 blocks)
- **limit:** Maximum number of mobs that can exist in the area (1-50)
- **interval:** Spawn interval in ticks (1-6000, where 20 ticks = 1 second)
- **entityName:** Custom name for spawned entities (use `\"\"` for no name)
- **mobID:** Minecraft entity ID (supports tab completion)
- **Examples:**
  - `/flowframe spawner add GuardPost 10 5 100 "Guard Zombie" minecraft:zombie`
  - `/flowframe spawner add CowFarm 15 8 200 "" minecraft:cow`
  - `/flowframe spawner add SkeletonTower 12 3 60 "Archer" minecraft:skeleton`

#### `/flowframe spawner remove [name]`
- Remove a spawner by name or remove the spawner at your current location
- **Examples:**
  - `/flowframe spawner remove GuardPost` - Remove specific spawner
  - `/flowframe spawner remove` - Remove spawner at your location

#### `/flowframe spawner list`
- View all active spawners with their current mob counts and settings

### `/town` Commands

#### `/town create <name> <color> <prefix> <coords>`
- **Permission:** All players
- **Description:** Create a new town with specified settings
- **Usage:** `/town create Corvia blue "Corvia" ~ ~ ~`
- **Parameters:**
  - `name` - Town name (single word)
  - `color` - Color from Minecraft's default colors (black, dark_blue, dark_green, etc.)
  - `prefix` - Text that appears before player names 
  - `coords` - Town location (use ~ ~ ~ for current position)

#### `/town join <name>`
- **Permission:** All players
- **Description:** Join an existing town
- **Usage:** `/town join Corvia`

#### `/town leave`
- **Permission:** All players  
- **Description:** Leave your current town
- **Usage:** `/town leave`

#### `/town disband <name>`
- **Permission:** All players (town owners only)
- **Description:** Disband a town you own
- **Usage:** `/town disband Corvia`

#### `/town edit <name> <color>`
- **Permission:** All players (town owners only)  
- **Description:** Change your town's color
- **Usage:** `/town edit Corvia red`

#### `/town list`
- **Permission:** All players
- **Description:** Show all existing towns
- **Usage:** `/town list`

#### `/town info <name>`
- **Permission:** All players
- **Description:** Show town information
- **Usage:** `/town info Corvia`

## Installation

1. Make sure you have Fabric Loader 0.16.5 or newer installed on your server
2. Download the mod JAR file: `flowframe-1.21.1-1.40.8.jar`
3. Place it in your server's `mods` folder
4. Restart your server

## Requirements

- **Minecraft Version:** 1.21.1
- **Mod Loader:** Fabric
- **Fabric Loader:** 0.16.5+
- **Fabric API:** 0.105.0+1.21.1
- **Java:** 21 or newer

## Technical Details

This is a **server-side only** mod. Players do not need to install anything on their clients to join and benefit from the features.

The mod uses Mixins to modify game behavior:
- `CreeperMixin` - Prevents block damage from explosions
- `PhantomSpawnerMixin` - Redirects phantom spawns to Nether
- `EndPortalBlockMixin` - Controls portal entry per player
- `FarmBlockMixin` - Prevents farmland from being trampled
- `LivingEntityMixin` - Applies path block speed boost
- `ServerLevelMixin` - Cleanup on player disconnect

## Building from Source

```bash
./gradlew build
```

The compiled mod will be in `build/libs/flowframe-1.21.1-1.40.8.jar`

## License

See license.md

## Support

For issues, questions, or suggestions, please contact HelluPeptic.
