# Flowframe Mod

Flowframe is a Minecraft mod for Fabric that adds quality-of-life and server management features for the FlowSMP. Each player related feature is permission-based and can be enabled/disabled by server admins.

---

# Flowframe Mod

A Minecraft Fabric mod for the FlowSMP that adds quality-of-life and server management features. All player-related features are permission-based.

---

## Core Features

### Player Mechanics

- **Player Head Drops** - Drop your head when killed by your own arrow
- **No Crop Trampling** - Farmland can't be trampled
- **Creeper No Grief** - Creepers don't damage blocks
- **Phantom Deny** - No phantoms in overworld (allowed in nether)

### Commands

- /orelog <hours> - View ore mining logs + ore announcements
- /endtoggle - Enable/disable The End dimension
- /levitate <player> - Give levitation effect
- /linked [player] - Shows the user ID of the Discord account that is linked to that Minecraft name
- /flowframe version - Show mod version
- /flowframe countentities - Count all entities

### Item & Block Features

- **Ore Announcements** - Broadcasts rare ore discoveries
- **Potion Extension** - All potions last 2+ hours
- **Map/Trash Bag/Banglum Nuke Core Removal** - Remove unwanted items
- **Path Speed Boost** - Move faster on dirt paths

### Spawner System

Create custom mob spawners:

- /flowframe spawner add <name> <radius> <limit> <interval> <entityName> <mobID>
- /flowframe spawner remove [name] - Remove spawner
- /flowframe spawner list - View all spawners

### Inventory Management

- **Auto Backup** - Saves inventories on join/leave/death
- /flowframe inventoryrestore view <player> - View backups
- /flowframe inventoryrestore restore <player> - Restore inventory

### Server Management

- **Auto GameRules** - Ensures day/weather cycles stay enabled
- **Daily Restart Warning** - 6:55 AM CET warning
- **GL Commands** - Gives non-operators the ability to access GrieferLogger's commands. This lets players see deposits/withdrawals from inventories and placement/breaking of blocks

---

## Permissions

Most features require permissions via LuckPerms or Fabric Permissions API. Operators (level 2+) have access to everything.

**Key Permissions:**

- flowframe.command.\* - For commands
- flowframe.feature.\* - For features
- flowframe.groupcolor.&[color] - For chat colors

**Chat Colors:**
Players get colored names from LuckPerms prefixes or color permissions in the tablist ( flowframe.groupcolor.&a for green, etc.)

---

## Configuration

Features can be enabled/disabled by modifying registrations in FlowframeMod.java .
