# Arms Dealers Workbench Placement Blocker

This feature prevents players from placing the Arms Dealers Workbench from Vic's Point Blank mod unless they have the specific permission. Simple and lightweight!

## Features

- **Block Placement Prevention**: Prevents players from placing Arms Dealers Workbench blocks
- **Permission-Based**: Uses LuckPerms or OP level 2+ to control access
- **Lightweight**: Only hooks into item usage, no world scanning or inventory manipulation

## Permissions

- `flowframe.feature.armsworkbench` - Allows players to place Arms Dealers Workbench blocks

Players with OP level 2 or higher automatically have this permission.

## Behavior

### For Players Without Permission:
- Cannot place workbench blocks (placement is blocked with error message)
- Can still interact with existing workbenches (if any exist)
- Can keep workbench items in inventory

### For Players With Permission:
- Can place workbenches normally
- Full access to all workbench functionality

## Supported Item Names

The blocker automatically detects Arms Dealers Workbench items regardless of the exact mod namespace:
- `pointblank:arms_dealer_workbench`
- `vics_point_blank:arms_dealer_workbench` 
- `vicspointblank:arms_dealer_workbench`
- `pointblank:arms_dealers_workbench`
- `vics_point_blank:arms_dealers_workbench`
- `vicspointblank:arms_dealers_workbench`
- Any item containing `arms_dealer_workbench` or `arms_dealers_workbench`

## Messages

- **Placement Blocked**: "The Arms Dealers Workbench is disabled on this server!" (red text)

## Why This Approach?

- **Simple**: Only prevents new placements, doesn't interfere with existing gameplay
- **Performance**: No world scanning or inventory checking
- **Compatibility**: Uses standard Fabric events, no complex chunk iteration
- **User-Friendly**: Players can still use existing workbenches if they find them
