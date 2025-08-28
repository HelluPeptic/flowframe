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
  - `/flowframe inventoryrestore save <player>` – Manually create a backup.
  - `/flowframe inventoryrestore cleanup` – Clean up old backups. Saves 3 for each player
- **Permissions:**
  - View: `flowframe.feature.inventoryrestore.view`
  - Restore: `flowframe.feature.inventoryrestore.restore`
  - Manual backup: `flowframe.feature.inventoryrestore.manualbackup`
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
  - Ensures all drinkable potions (vanilla and modded) last at least 2 hours (144,000 ticks) when applied.
  - **HerbalBrews teas** (including all effects like "feral", "balanced", "tough", "lifeleech", "fortune", "bonding", "deeprush", and regeneration from Rooibos Tea) are always extended to 2 hours, even if the mod applies them with a different method.
- **Commands:** None
- **Permissions:** None (automatic)

### 23. Arms Workbench Blocker

- **Description:** Prevents players from using or breaking the Arms Dealers Workbench from Vic's Point Blank mod unless they have permission. This helps server admins control access to weapon crafting.
- **Commands:** None
- **Permissions:** `flowframe.feature.armsworkbench` (or operator level 2+)

### 24. Battle System

- **Description:** A comprehensive PvP battle system that allows players to create teams, participate in multi-round battles, and spectate after elimination. Features include random team color assignment, battle leader controls, notification preferences, and automatic spectator teleportation.
- **Commands:**
  - `/flowframe battle boot [x] [y] [z]` – Boot a new battle at specified coordinates or current location.
  - `/flowframe battle join <team>` – Join a team with any custom name (colors assigned automatically).
  - `/flowframe battle start [rounds]` – Start the battle with optional number of rounds (default: 1).
  - `/flowframe battle leave` – Leave the battle (only during waiting periods).
  - `/flowframe battle kick <player>` – Kick a player from the battle (battle leader only).
  - `/flowframe battle shutdown` – End the battle and reset all players (battle leader only).
  - `/flowframe battle status` – View current battle status and team information.
  - `/flowframe battle togglenotifications` – Toggle action bar notifications for battle events.
  - `/flowframe battle giveup` – Voluntarily eliminate yourself from the battle (only during active PvP).
- **Permissions:**
  - Boot battle: `flowframe.command.battle.boot` (or operator level 2+)
  - Other commands: Available to all players
- **Features:**
  - **Team System:** Players can join teams with any name, random colors assigned automatically (no duplicates)
  - **Multi-Round Support:** Play multiple rounds in sequence with automatic progression
  - **Battle Leader System:** Only the player who boots the battle can control game state
  - **Spectator Mode:** Eliminated players automatically become spectators and are teleported to battle location
  - **Notification System:** Optional action bar notifications for battle events with per-player toggle
  - **Color Management:** Ensures unique team colors, automatically freed when teams are removed
  - **Grace Period:** 30-second preparation time before PvP becomes active
  - **Smart Respawn:** 3-second delay after respawn before spectator teleportation to ensure proper loading

### 25. Auto GameRules

- **Description:** Automatically sets `doDaylightCycle` and `doWeatherCycle` to `true` when the server starts/restarts. This ensures that time progression and weather changes are always enabled after a server restart.
- **Commands:** None
- **Permissions:** None (automatic)

### 26. Capture the Flag (CTF) Game Mode

- **Description:** A team-based objective game mode where Red and Blue teams compete to capture each other's flags and return them to their own base. Features persistent base locations, automatic flag mechanics, and multiple victory conditions.
- **Commands:**
  - `/flowframe battle boot capture_the_flag time` – Boot a time-based CTF battle (10-minute rounds).
  - `/flowframe battle boot capture_the_flag score <target>` – Boot a score-based CTF battle with target score.
  - `/flowframe ctf setbase <team> [x] [y] [z]` – Set team flag base at coordinates or current location (team leader only).
  - `/flowframe ctf status` – View current CTF game status, scores, and flag locations.
  - `/flowframe ctf bases` – List all team flag base locations.
- **Permissions:**
  - Boot CTF: `flowframe.command.battle.boot` (or operator level 2+)
  - Set bases: Team leader status required
  - Other commands: Available to all players
- **Features:**
  - **Two-Team System:** Red vs Blue team competition with automatic team color integration
  - **Persistent Bases:** Flag base locations and particles persist between battles and server restarts
  - **Team Leader Control:** Only team leaders can set or move flag bases (prevents griefing)
  - **Automatic Flag Mechanics:** Walk near enemy base to pick up flag, return to your base to capture
  - **Visual Effects:** Continuous colored particle effects mark base locations, flag carriers glow with flag team colors
  - **Respawn System:** 10-second respawn delay with spectator mode, respawn at team base
  - **Multiple Game Modes:** Time-based (10 minutes) or score-based (first to target) victory conditions
  - **Flag Requirements:** Your own flag must be at base to capture enemy flags (strategic defense element)
  - **Real-time Feedback:** Action bar messages, sound effects, and score updates for all major events

### 27. Daily Restart Warning

- **Description:** Automatically sends a red warning message to all players every day at 6:55 AM Central European Time, notifying them that the server will restart in 5 minutes. This helps players prepare for scheduled daily restarts.
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
  - `flowframe.command.minetracer.lookup`
  - `flowframe.command.minetracer.rollback`
  - `flowframe.command.minetracer.inspect`
  - `flowframe.command.minetracer.page`
  - `flowframe.feature.armsworkbench`

### Color System (Chat & Tablist)

The mod supports colored player names in chat and tablist through LuckPerms integration:

**Method 1: Permission-Based Colors**
Grant specific color permissions to players:

- `flowframe.groupcolor.&0` (black)
- `flowframe.groupcolor.&1` (dark blue)
- `flowframe.groupcolor.&2` (dark green)
- `flowframe.groupcolor.&3` (dark aqua)
- `flowframe.groupcolor.&4` (dark red)
- `flowframe.groupcolor.&5` (dark purple)
- `flowframe.groupcolor.&6` (gold)
- `flowframe.groupcolor.&7` (gray)
- `flowframe.groupcolor.&8` (dark gray)
- `flowframe.groupcolor.&9` (blue)
- `flowframe.groupcolor.&a` (green)
- `flowframe.groupcolor.&b` (aqua)
- `flowframe.groupcolor.&c` (red)
- `flowframe.groupcolor.&d` (light purple)
- `flowframe.groupcolor.&e` (yellow)
- `flowframe.groupcolor.&f` (white)

**Method 2: LuckPerms Prefix Colors**
The mod automatically extracts colors from LuckPerms prefixes (e.g., `§4[Admin] ` will make the player's name red)

**Priority:**

1. Permission-based colors (highest priority)
2. LuckPerms prefix colors
3. Gold for operators
4. White for regular players

---

## Removing Features

- Each feature is modular and can be enabled/disabled by removing its registration in `FlowframeMod.java`.
