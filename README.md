# Project Keeper

A Fabric mod for Minecraft that automates spawner defense with threat detection, inventory management, and path rendering.

## Features

- **Threat Detection**: Monitors for non-whitelisted players and block breaking activity within render distance
- **Automated Mining**: Custom A* pathfinding to navigate and mine spawners  
- **Inventory Management**: Automatic packing into shulker boxes and ender chest storage
- **Ghost Block Detection**: Identifies and fixes ghost spawner blocks
- **Path Rendering**: Visual path display similar to Baritone
- **Background Operation**: Works when game is tabbed out

## Keybindings

| Key | Action |
|-----|--------|
| `P` | Toggle defense mode (threat watching) |
| `U` | Start mining/packing |
| `I` | Emergency stop |
| `O` | Show status |
| `Y` | Open config menu |

## Configuration

Press `Y` to open the config menu with tabs for:
- **Config**: Bot settings (search radius, mining distance, path rendering)
- **HUD**: Display settings (position, scale, elements)
- **Whitelist**: Manage trusted players (supports username/UUID input)
- **About**: License, credits, and session stats

## Requirements

- Minecraft 1.21.x
- Fabric Loader 0.16.0+
- Fabric API

## Building

```bash
./gradlew build
```

Output JAR will be in `build/libs/`.

## Installation

1. Install Fabric Loader
2. Install Fabric API
3. Place the mod JAR in your `mods` folder

## License

Apache License 2.0 - See LICENSE file for details.

Copyright 2026 Caelen Cater / Team Arctorian
