# Flowframe Mod

Flowframe is a modular Minecraft mod for Fabric that adds quality-of-life and server management features. Each feature is permission-based and can be enabled/disabled by server admins.

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

### 3. Invisible Item Frames (feature turned off)

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

- **Description:** Disables phantom spawning entirely.
- **Commands:** None
- **Permissions:** None (automatic)

### 10. Chat Formatting & Tablist

- **Description:**
  - Custom join/leave messages.
  - Tablist header/footer and display name formatting (LuckPerms prefix support).
- **Commands:** None
- **Permissions:**
  - Tablist and chat formatting: automatic, but LuckPerms prefixes and group color permissions are respected (e.g., `flowframe.groupcolor.&a` for green).

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

---

## Adding/Removing Features

- Each feature is modular and can be enabled/disabled by removing its registration in `FlowframeMod.java`.
- To add a new feature, create a new Java class in `features/` and register it in `FlowframeMod.java`.

---

## License

See [license.md](license.md).
