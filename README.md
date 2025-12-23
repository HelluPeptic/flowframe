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

### 5. **Server Restart Defaults**
- Daylight cycle automatically enabled on every server restart
- Weather cycle automatically enabled on every server restart
- No need to manually re-enable these game rules

## Commands

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
