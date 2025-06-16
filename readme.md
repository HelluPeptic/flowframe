# Flowframe Mod

Flowframe is a Minecraft mod for Fabric that adds quality-of-life and server management features for the FlowSMP. Each player related feature is permission-based and can be enabled/disabled by server admins.

---

## Features

### 1. Player Head Drops

- **Description:** Players drop their own head when killed by their own arrow (self-inflicted bow death).
- **Commands:** None
- **Permissions:** None (automatic)

### 2. No Crop Trampling

- **Description:** Prevents farmland from being trampled by players or mobs.
- **Commands:** None
- **Permissions:** None (automatic)

### 3. Invisible Item Frames (disabled)

- **Description:** Shift + right-click an item frame to make it invisible. Right-click again to reveal.
- **Commands:** None
- **Permissions:** None (automatic)

### 4. `/tphere` Command

- **Description:** Teleport another player to your location.
- **Command:** `/tphere <player>`
- **Permissions:** `flowframe.command.tphere` (or operator level 2+)

### 5. `/keepinv` Command

- **Description:** Toggle keep inventory for yourself.
- **Command:** `/keepinv`
- **Permissions:** `flowframe.command.keepinv` (or operator level 2+)

### 6. `/orelog` Command & Ore Announce

- **Description:**
  - **Ore Announce:** Broadcasts ore discoveries (diamond, modded ores, etc.) to ops or players with permission.
  - **Ore Log:** View a log of ore mining events within a time window.
- **Command:** `/orelog <hours>` (e.g., `/orelog 2h`)
- **Permissions:**
  - Ore Announce: `flowframe.feature.oreannouncements` (or operator level 2+)
  - Ore Log: `flowframe.command.orelog` (or operator level 2+)

### 7. End Toggle

- **Description:** Toggle whether "The End" dimension is enabled or disabled.
- **Command:** `/endtoggle`
- **Permissions:** `flowframe.command.endtoggle` (or operator level 2+)

### 8. Creeper No Grief

- **Description:** Prevents creepers from damaging blocks (no terrain damage).
- **Commands:** None
- **Permissions:** None (automatic)

### 9. Phantom Deny

- **Description:** Disables phantom spawning in the overworld. However, they are allowed in the nether for obtainable phantom membrane.
- **Commands:** None
- **Permissions:** None (automatic)

### 10. Chat Formatting & Tablist

- **Description:**
  - Custom join/leave messages.
  - Tablist header/footer and display name formatting (LuckPerms prefix support).
- **Commands:** None
- **Permissions:**
  - Tablist and chat formatting: automatic, but LuckPerms prefixes and group color permissions are respected (e.g., `flowframe.groupcolor.&a` for green).

### 11. Plow Deny (not added)

- **Description:** Prevents the Nifty Carts plow entity from being placed due to a bug causing server crashes.
- **Commands:** None
- **Permissions:** None (automatic)

### 12. `/flowframe version` Command

- **Description:** Displays the current version of the Flowframe mod.
- **Command:** `/flowframe version`
- **Permissions:** `flowframe.command.version` (or operator level 2+)

### 13. `/levitate` Command

- **Description:** Grants the levitation effect to a player for a short duration.
- **Command:** `/levitate <player>`
- **Permissions:** `flowframe.command.levitate` (or operator level 2+)

### 14. `/setaircraftpilotlimit` Command

- **Description:** Sets the maximum number of pilots for an aircraft entity.
- **Command:** `/setaircraftpilotlimit <limit>`
- **Permissions:** `flowframe.command.setaircraftpilotlimit` (or operator level 2+)

### 15. Removal of Spelunker Potion

- **Description:** The spelunker potion has been removed from the mod.
- **Commands:** None
- **Permissions:** None

### 16. `/linked` Command

- **Description:** Shows or manages linked accounts or entities.
- **Command:** `/linked [player]`
- **Permissions:** `flowframe.command.linked` (or operator level 2+)

### 17. `/gl i` Command

- **Description:** Displays information about the current glow list or glowing entities.
- **Command:** `/gl i`
- **Permissions:** `flowframe.command.gl.inspect` (or operator level 2+)

### 18. `/gl p` Command

- **Description:** Manages or displays glow list participants.
- **Command:** `/gl p`
- **Permissions:** `flowframe.command.gl.page` (or operator level 2+)

### 19. Inventory Restore

- **Description:** Automatically backs up player inventories (including trinket slots and compatible backpacks) on join, leave, and death. Allows viewing, restoring, and exporting previous inventory states.
- **Commands:**
  - `/flowframe inventoryrestore view <player>` – View available backups for a player.
  - `/flowframe inventoryrestore restore <player> [backup]` – Restore a specific backup (default: latest).
  - `/flowframe inventoryrestore save <player>` (not added) – Manually create a backup.
  - `/flowframe inventoryrestore export <player>` (not added) – Export a backup to file.
  - `/flowframe inventoryrestore cleanup` (not added) – Clean up old backups.
- **Permissions:**
  - View: `flowframe.feature.inventoryrestore.view`
  - Restore: `flowframe.feature.inventoryrestore.restore`
  - Manual backup: `flowframe.feature.inventoryrestore.manualbackup`
  - Export: `flowframe.feature.inventoryrestore.export`
  - Cleanup: `flowframe.feature.inventoryrestore.cleanup`
  - (Operators always have access)

### 20. `/obtain` Command

- **Description:** Allows players to exchange Netherite Ingots for special cards from Majrusz's Accessories mod.
- **Commands:**
  - `/obtain removal_card` – Costs 1 Netherite Ingot, gives a Removal Card.
  - `/obtain gambling_card` – Costs 3 Netherite Ingots, gives a Gambling Card.
  - `/obtain reverse_card` – Costs 10 Netherite Ingots, gives a Reverse Card.
- **Permissions:** `flowframe.command.obtain` (or operator level 2+)

### 21. `/flowframe countentities` Command

- **Description:** Counts all entities in all worlds and displays the top 15 most common entity types to the player.
- **Command:** `/flowframe countentities`
- **Permissions:** `flowframe.command.countentities` (or operator level 2+)

### 22. Potion Duration Extension (Vanilla & Modded)

- **Description:**
  - Ensures all potions (vanilla and modded) last at least 2 hours (144,000 ticks) when applied, except for effects from beacons, suspicious stew, and certain excluded effects.
  - Modded effects like "tipsy" from Brewin and Chewin are also extended to 2 hours, even if the mod applies them with a different method.
  - **The following effects are NOT extended and/or are disabled:**
    - Slowness (vanilla)
    - Health Boost (vanilla)
    - Mining Fatigue (vanilla, completely disabled)
    - Poison (vanilla, completely disabled)
    - All effects from the Simply Skills mod
    - All effects from the Simply Swords mod
- **Commands:** None
- **Permissions:** None (automatic)

---

## Permissions

- All commands and features that require permissions use LuckPerms or Fabric Permissions API.
- Operators (permission level 2+) always have access.
- Example LuckPerms permission nodes:
  - `flowframe.command.tphere`
  - `flowframe.command.keepinv`
  - `flowframe.command.orelog`
  - `flowframe.feature.oreannouncements`
  - `flowframe.command.endtoggle`
  - `flowframe.groupcolor.&a` (for green tablist name)
  - `flowframe.command.levitate`
  - `flowframe.command.setaircraftpilotlimit`
  - `flowframe.command.linked`
  - `flowframe.command.gl.inspect`
  - `flowframe.command.gl.page`
  - `flowframe.feature.inventoryrestore.view`
  - `flowframe.feature.inventoryrestore.restore`
  - `flowframe.feature.inventoryrestore.manualbackup`
  - `flowframe.feature.inventoryrestore.export`
  - `flowframe.feature.inventoryrestore.cleanup`
  - `flowframe.command.obtain`
  - `flowframe.command.countentities`

---

## Removing Features

- Each feature is modular and can be enabled/disabled by removing its registration in `FlowframeMod.java`.

---
