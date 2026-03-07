# Server Logger

A Fabric client-side mod for Minecraft that automatically detects and records server plugins, connection metadata, and world info whenever you join a multiplayer server.

---

## Features

- **Automatic plugin detection:** scans the server's command tree on join
- **Server logs:** saves each server's data to a JSON file in `.minecraft/server-logs/` for later review
- **Glossary:** a built-in dictionary resolves common command aliases to canonical plugin names (e.g. `lp` → `luckperms`, `we` → `worldedit`)
- **Glossary plugin highlighting:** plugins identified via the glossary are colored blue in the detailed view
- **Resource pack & URL tracking:** detects URLs embedded in chat, tab-list, scoreboards, and resource pack pushes
- **In-game GUI:** browse, search, filter, and manage all logged servers without leaving the game
- **Options screen:** toggle auto-clipboard, toast notifications, and in-game status messages

---

## Keybind

| Key | Action |
|-----|--------|
| `Z` | Open the Server Logs GUI |

Rebindable in **Options → Controls → Miscellaneous**.

---

## GUI Overview
<img width="1919" height="1079" alt="image" src="https://github.com/user-attachments/assets/f4401f9e-48be-4fe3-9fd3-81549c5faed0" />


### Server List screen (`Z`)

The main screen lists every logged server, sorted by most recently visited.

- **Search box:** filter by name, plugin, or software
- **Counter:** live `x / total` count updates as you type in the search box
- **Double-click** an entry to open its detail view
- **Add:** manually create an entry for a server you haven't visited yet
- **Remove / Undo:** delete the selected entry; `Delete` key also works; undo restores it from disk
- **Glossary:** open the alias editor
- **Options:** open the options screen (top-right corner)

### Server Detail screen
<img width="1919" height="1079" alt="image" src="https://github.com/user-attachments/assets/ad4bf1e2-6bb1-49d7-bc7f-6e04d16aab6f" />

A two-panel layout showing full information for one server.

- Left sidebar: detected addresses and worlds visited
- Right grid: full plugin list — glossary-identified plugins shown in **blue**
- **Scroll** over each panel independently with the mouse wheel
- **Import:** opens `.minecraft/server-logs/` in the OS file manager
- **Copy Plugins:** copies the full plugin list to clipboard as a comma-separated string

### Glossary Editor screen
<img width="1896" height="1076" alt="image" src="https://github.com/user-attachments/assets/42fddebf-e448-418c-9eac-c22417439cc8" />

Map custom command names to plugin names. Changes are saved immediately and persist across restarts — the hardcoded defaults are only written on the very first launch and are never re-applied.

- **Add:** enter a command and plugin name, press Add
- **Import:** opens `.minecraft/config/` in the file manager so you can edit `server-logger-glossary.json` directly
- **Copy All:** copies every mapping to clipboard (`command=plugin` format)
- **Remove / Undo:** remove the selected mapping with undo support
- **Save:** persists changes to disk; **Back** discards unsaved changes

### Options screen
<img width="1898" height="1079" alt="image" src="https://github.com/user-attachments/assets/2c68fb57-2996-4de7-9eeb-d27c1423de1e" />

Accessible via the **Options** button in the top-right of the Server List screen.

| Toggle | Default | Description |
|--------|---------|-------------|
| Auto Clipboard | OFF | Automatically copies detected plugins to clipboard on join |
| Show Toasts | OFF | Shows a toast notification when plugins are detected |
| Show Messages | OFF | Sends mod status and error messages to in-game chat |

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

note: the plugins are merged. You keep the larger of the two sets.

### Config file

```
.minecraft/config/server-logger.json
```

```json
{
  "enabled": true,
  "logFolder": "server-logs",
  "autoClipboard": false,
  "showToasts": false,
  "showMessages": false
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

The glossary ships with ~90 common aliases pre-loaded.

---

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.16.10 or later |
| Fabric API | 0.141.3+1.21.11 or later |

---
Image credits to: sunriseking on https://unsplash.com (https://unsplash.com/photos/city-skyline-during-night-time-ZM6RUTERdBg)
