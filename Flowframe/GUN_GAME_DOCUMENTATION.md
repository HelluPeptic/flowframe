# Gun Game Feature Documentation

## Overview
The Gun Game feature provides a comprehensive CS:GO-style team-based combat system for Minecraft servers. Players can join teams, participate in organized matches, and compete in a controlled environment.

## Commands

### Administrative Commands
- `/flowframe gungame boot <x> <y> <z>` - Start a new gun game at the specified coordinates
  - Permission: `flowframe.command.gungame.boot`
  - Only one game can be active at a time
  
- `/flowframe gungame start` - Begin the countdown for an active game
  - Permission: `flowframe.command.gungame.start` 
  - Requires at least 2 teams to be formed
  
- `/flowframe gungame kick <player>` - Remove a player from the current game
  - Permission: `flowframe.command.gungame.kick` OR operator level 2
  - Resets the player's gamemode and teleports them away

### Player Commands
- `/flowframe join team <color>` - Join a team with the specified color
  - Available to all players during the waiting phase
  - Colors cannot be "purple" or "aqua" (reserved)
  
- `/flowframe gungame status` - View current game status
  - Shows current state and active teams

## Game Flow

### 1. Boot Phase
When an administrator runs `/flowframe gungame boot <x> <y> <z>`:
- A new game session is created
- The spawn point is set to the specified coordinates
- All players receive a notification that a game has started
- The game enters WAITING state

### 2. Team Formation Phase
During the WAITING state:
- Players can join teams using `/flowframe join team <color>`
- Team names appear in chat with colored prefixes: `[TeamName] PlayerName`
- Players can switch teams freely during this phase
- Original gamemode and position are stored for later restoration

### 3. Start Countdown
When `/flowframe gungame start` is executed:
- A 10-second countdown begins using title display
- All team members see the countdown timer
- After countdown, all players are teleported to the spawn point

### 4. Grace Period
After teleportation:
- 1-minute grace period begins
- Players can prepare (remove armor, get weapons, position themselves)
- PvP is disabled during this time
- Title shows "Grace Period" with countdown

### 5. Active Combat
When grace period ends:
- PvP is enabled between different teams
- Friendly fire is prevented (same team cannot damage each other)
- Non-participants cannot damage game players and vice versa
- Title shows "FIGHT!" to indicate start of combat

### 6. Elimination & Spectator Mode
When a player dies:
- They are immediately put into spectator mode
- Their team is notified of the elimination
- If all team members are eliminated, the team is declared eliminated
- Eliminated players can spectate the remaining match

### 7. Victory & Reset
When only one team remains:
- Victory message is broadcast to all players
- Winning team is announced with colored formatting
- After 5 seconds, all players are teleported back to spawn point
- Gamemodes are restored to original values
- All game data is cleared

## Technical Features

### Team Management
- Thread-safe team management using ConcurrentHashMap
- Support for unlimited teams with different colors
- Automatic cleanup of empty teams
- Player elimination tracking per team

### PvP Control
- Granular PvP control through mixins
- Prevents friendly fire automatically
- Isolates game participants from non-participants
- Only enables PvP during active game phase

### Chat Integration
- Automatic team prefix addition to chat messages
- Colored team names in chat
- Team-based message formatting
- Integration with existing chat systems

### Player State Management
- Automatic backup and restoration of gamemode
- Position tracking for post-game teleportation
- Spectator mode management for eliminated players
- Cleanup on player disconnect

### Permissions
The system integrates with LuckPerms for fine-grained permission control:
- `flowframe.command.gungame.boot` - Boot new games
- `flowframe.command.gungame.start` - Start games
- `flowframe.command.gungame.kick` - Kick players from games

### Safety Features
- Only affects players who actively join teams
- Non-participants are completely unaffected
- Automatic cleanup on server shutdown
- Proper error handling and state validation
- Prevention of multiple concurrent games

## Team Colors
Supported team colors include: red, blue, green, yellow, orange, pink, white, black, gray/grey, brown, lime, cyan
Restricted colors: purple, aqua (reserved for system use)

## State Management
The game uses a state machine with the following states:
- INACTIVE: No game running
- WAITING: Game booted, accepting team joins
- COUNTDOWN: Start countdown in progress
- GRACE_PERIOD: 1-minute preparation time
- ACTIVE: Combat phase with PvP enabled
- ENDING: Game conclusion and cleanup

This ensures proper game flow and prevents invalid operations at inappropriate times.
