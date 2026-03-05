# Server Logger

A Fabric client-side mod for Minecraft 1.21.11 that automatically detects and records server plugins, connection metadata, and world history whenever you join a multiplayer server.

---

## Features

- **Automatic plugin detection** — scans the server's command tree on join and fires a `/version` tab-complete probe to build a deduplicated, normalised plugin list
- **Persistent server logs** — saves each server's data to a JSON file in `.minecraft/server-logs/` for later review
- **Clipboard copy** — automatically copies detected plugins to clipboard on join, with a toast notification showing the count
- **In-game GUI** — browse, search, filter, and manage all logged servers without leaving the game
- **Glossary / alias mapping** — a built-in dictionary resolves common command aliases to canonical plugin names (e.g. `lp` → `luckperms`, `we` → `worldedit`)
- **Undo & remove** — delete server entries from the list with single-key undo support
- **Resource pack & URL tracking** — detects URLs embedded in chat, tab-list, scoreboards, and resource pack pushes

---

## Keybind

| Key | Action |
|-----|--------|
| `Z` | Open the Server Logs GUI |

Rebindable in **Options → Controls → Miscellaneous**.

---

## GUI Overview

### Server List screen (`Z`)

The main screen lists every logged server, sorted by most recently visited.

- **Search box** — filter by name, plugin, or software (cycle the **Filter** button to change mode)
- **Double-click** an entry to open its detail view
- **Add** — manually create an entry for a server you haven't visited yet
- **Remove / Undo** — delete the selected entry; `Delete` key also works; undo restores it from disk
- **Glossary** — open the alias editor

### Server Detail screen

A two-panel layout showing full information for one server.

```
┌─────────────────────────────────────────────────┐
│  IP · Software · Version                        │  ← Header
│  Domain · Logged · Plugin count                 │
├──────────────────┬──────────────────────────────┤
│ Detected         │  Plugins (3–4 column grid,   │
│ Addresses        │  scrollable)                 │
│                  │                              │
│ Worlds Visited   │                              │
│ (scrollable)     │                              │
├──────────────────┴──────────────────────────────┤
│  [Import]   [Copy Plugins]   [Back]             │  ← Footer
└─────────────────────────────────────────────────┘
```
Demonstration:
<img width="1917" height="1079" alt="image" src="https://github.com/user-attachments/assets/5b08e76e-45f9-4f56-97ab-ce721ad12a90" />


- **Scroll** over each panel independently with the mouse wheel
- **Import** — opens `.minecraft/server-logs/` in the OS file manager
- **Copy Plugins** — copies the full plugin list to clipboard as a comma-separated string

### Glossary Editor screen

Map custom command names to plugin names. Changes take effect immediately for future scans.

- **Add** — enter a command and plugin name, press Add
- **Import** — opens `.minecraft/config/` in the file manager so you can drop in a `server-logger-glossary.json` file
- **Copy All** — copies every mapping to clipboard (`command=plugin` format)
- **Remove / Undo** — remove the selected mapping with undo support
- **Save** — persists changes to disk; **Back** discards unsaved changes

---

## Data

### Log files

Each server gets one JSON file at:

```
.minecraft/server-logs/<hostname or ip_port>.json
```

```json
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
<img width="1916" height="1079" alt="image" src="https://github.com/user-attachments/assets/768d82d7-3ddf-4f78-aad7-8c081a6bc6d5" />

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

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.16.10 or later |
| Fabric API | 0.141.3+1.21.11 or later |

Client-side only — does not need to be installed on the server.

---

## Building

```bash
./gradlew build
```

Output jar: `build/libs/server-logger-<version>.jar`

```bash
./gradlew runClient
```

Launches a development client with the mod loaded.
