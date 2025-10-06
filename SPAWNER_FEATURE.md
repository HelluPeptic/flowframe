# Flowframe Spawner Feature

## Overview

The Spawner feature allows server administrators to create custom mob spawners at any location with configurable parameters. This feature requires proper permissions to use.

## Permission Required

- `flowframe.command.spawner` - Required to use all spawner commands

## Commands

### `/flowframe spawner add <radius> <limit> <interval> <mob>`

Creates a new spawner at the player's current location.

**Parameters:**

- `radius` - Spawn radius in blocks (1-100)
- `limit` - Maximum number of mobs that can exist within the radius (1-50) - **Includes spawner-tagged mobs**
- `interval` - Spawn interval in ticks (1-6000, where 20 ticks = 1 second)
- `mob` - The entity type to spawn (e.g., "minecraft:zombie", "minecraft:cow", "minecraft:creeper") - **Tab completion with mod support**

**Example:**

```
/flowframe spawner add 10 5 100 minecraft:zombie
```

This creates a zombie spawner with a 10-block radius, maximum 5 zombies, spawning every 5 seconds.

### `/flowframe spawner remove`

Removes the spawner that contains the player's current location.

### `/flowframe spawner list`

Shows all active spawners with their details including:

- Spawner ID (first 8 characters)
- Mob type
- Center coordinates
- Radius
- Current mob count / limit
- Spawn interval

## How It Works

1. **Spawner Creation**: When you create a spawner, it's placed at your current location and becomes the center point.

2. **Mob Spawning**: Every `interval` ticks, the spawner attempts to spawn a mob:

   - Checks if the current mob count is below the limit
   - Finds a random valid spawn location within the radius
   - Spawns the mob if the location is suitable

3. **Mob Counting**: The spawner counts existing mobs using two methods:

   - **Tagged mobs**: Mobs spawned by the spawner are tagged and tracked even if they move far away
   - **Nearby mobs**: Natural spawns within the radius are also counted towards the limit

4. **Spawn Logic**: The spawner tries up to 10 times to find a valid spawn location, looking for solid ground with air above.

5. **Tab Completion**: Enhanced tab completion with support for:

   - **All registered entities**: Shows ALL entities from vanilla and mods, not just common ones
   - **Smart sorting**: Exact prefix matches first, then partial matches
   - **Mod support**: Includes all modded entities (e.g., `crittersandcompanions:dragonfly`)
   - **Limited suggestions**: Prevents spam by limiting to 50 total suggestions

6. **Entity Tracking**: Advanced tracking system using UUID mapping:

   - **Persistent tracking**: Entities spawned by spawners are tracked even when they move
   - **Memory-based**: Uses in-memory mapping instead of NBT for better performance
   - **Dead entity cleanup**: Automatically removes tracking for dead/despawned entities
   - **Silent operation**: No console spam - operates quietly in the background

## Examples

**Fast Chicken Farm:**

```
/flowframe spawner add 15 20 60 minecraft:chicken
```

Spawns chickens every 3 seconds in a 15-block radius, max 20 chickens.

**Slow Zombie Siege:**

```
/flowframe spawner add 25 8 1200 minecraft:zombie
```

Spawns zombies every minute in a 25-block radius, max 8 zombies.

**Cow Pasture:**

```
/flowframe spawner add 20 10 600 minecraft:cow
```

Spawns cows every 30 seconds in a 20-block radius, max 10 cows.

## Notes

- Spawners persist across server restarts
- Each spawner has a unique ID for tracking
- Mobs must be valid entity types from the Minecraft registry
- Spawners respect world spawn rules (won't spawn in inappropriate locations)
- The feature is permission-gated for server security
