# Server Logger

A Fabric client-side mod for Minecraft 1.21.11 that automatically detects and records server plugins, connection metadata, and much more whenever you join a multiplayer server.

---

## Features

- **Auto plugin detection:** scans the server's command tree on join and fires a tab-complete probe to build a plugin list
- **Server logs:** saves each server's data to a JSON file in `.minecraft/server-logs/` for later review
- **Auto Clipboard:** automatically copies detected plugins to clipboard on join
- **In-game GUI:** browse, search, filter, and manage all logged servers without leaving the game
- **Glossary:** a built-in dictionary resolves common command aliases to canonical plugin names (e.g. `lp` → `luckperms`, `we` → `worldedit`)
- **Undo & remove:** delete server entries from the list with single-key undo support
- **Tab and Scoreboard logging:** detects URLs embedded in chat, tab-list, scoreboards, and resource pack pushes

---

## Keybind

| `Z` | Open the Server Logs GUI

Rebindable in **Options → Controls → Miscellaneous**.

---

## GUI Overview

### Server List screen

The main screen lists every logged server, sorted by most recently visited.

- **Search box:** filter by name, plugin, or software (cycle the **Filter** button to change mode)
- **Double-click** an entry to open its detail view
- **Add:** manually create an entry for a server you haven't visited yet (not functional yet)
- **Remove / Undo:** delete the selected entry; `Delete` key also works; undo restores it from disk
- **Glossary:** open the plugins list editor

### Server Detail screen

A two-panel layout showing full information for one server.

<img width="1917" height="1079" alt="image" src="https://github.com/user-attachments/assets/5b08e76e-45f9-4f56-97ab-ce721ad12a90" />

- **Import:** opens `.minecraft/server-logs/` in the OS file manager
- **Copy Plugins:** copies the full plugin list to clipboard as a comma-separated string

### Glossary Editor screen
<img width="1916" height="1079" alt="image" src="https://github.com/user-attachments/assets/768d82d7-3ddf-4f78-aad7-8c081a6bc6d5" />

Map custom command names to plugin names. Changes take effect immediately for future scans.

- **Add:** enter a command and plugin name, press Add
- **Import:** opens `.minecraft/config/` in the file manager so you can drop in a `server-logger-glossary.json` file
- **Copy All:** — copies every mapping to clipboard (`command=plugin` format)
- **Remove / Undo:** remove the selected mapping with undo support
- **Save:** persists changes to disk;
- **Back:** discards unsaved changes

---

## Data

### Log files

Each server gets one JSON file at:

```
.minecraft/server-logs/<hostname or ip_port>.json
```

```json format: 
{
  "timestamp": "2025-03-05",
  "server_info": {
    "ip": "1.2.3.4",
    "port": 25565,
    "domain": "play.example.com",
    "software": "Paper",
    "version": "1.21.1"
  },
  "plugins": [
    { "name": "essentialsx" },
    { "name": "luckperms" }
  ],
  "detected_addresses": [],
  "worlds": [
    { "timestamp": "2025-03-05", "dimension": "minecraft:overworld" }
  ]
}
```

Subsequent joins merge data: plugin lists keep the larger of the two sets, and new world sessions are appended.

### Config file

```
.minecraft/config/server-logger.json
```

```json
{
  "enabled": true,
  "logFolder": "server-logs"
}
```

### Glossary file

```
.minecraft/config/server-logger-glossary.json
```

```json
{
  "entries": {
    "lp": "luckperms",
    "we": "worldedit"
  }
}
```

The glossary ships with ~90 common aliases pre-loaded. Entries added through the editor are merged on top.

---

## Requirements

Minecraft 1.21.11
Fabric Loader 0.16.10 or later
Fabric API 0.141.3+1.21.11 or later

---

## Building

```bash
./gradlew build
```

Output jar: `build/libs/server-logger-<version>.jar`

```bash
./gradlew runClient
```
Image credits to: sunriseking on https://unsplash.com  (https://unsplash.com/photos/city-skyline-during-night-time-ZM6RUTERdBg)
