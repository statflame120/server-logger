# Archivist

A Fabric client-side utility mod for Minecraft that automatically detects and records server plugins, connection metadata, and world info whenever you join a multiplayer server.

---

## Features

- **Automatic plugin detection** via command tree scanning, tab-complete probing, and GUI fingerprinting
- **Server logging** — saves each server's data to JSON in `.minecraft/archivist/server-logs/`
- **GUI fingerprinting** — identifies plugins by their inventory window signatures
- **Auto-scraping** — sends configurable commands on join to trigger and capture plugin GUIs
- **Exception server resolver** — detects proxy/hub networks (Minehut, Hypixel, etc.) and resolves the real sub-server domain from tab-list and scoreboard
- **URL & address extraction** — tracks IPs, domains, and URLs found in chat, MOTD, tab-list, scoreboards, and resource packs
- **Plugin glossary** — built-in dictionary maps command aliases to canonical plugin names (e.g. `lp` → `luckperms`)
- **Remote sync** — push, download, and reset logs via REST API with custom auth headers
- **Export** — JSON, CSV, and clipboard export of server data and event logs
- **7 color themes** — Amber, Violet, Midnight, Slate, Pear, Rose, Ocean (or add your own themes)

---

## Keybind

| Key | Action |
|-----|--------|
| `Z` | Open the Archivist GUI |

---

## GUI

The main screen is a windowed desktop with 8 draggable, minimizable windows and a taskbar:

| Window | Description |
|--------|-------------|
| **Server Info** | IP, port, domain, version, brand, player count, dimension, MOTD |
| **Plugin List** | All detected plugins (command tree + scraper + fingerprint), with search and copy |
| **World Info** | Dimension, difficulty, time, weather, spawn, world border, gamemode, resource pack |
| **Connection Log** | Color-coded event timeline (connect, disconnect, brand, plugin, world, etc.) |
| **Console** | Command input with autocomplete — run `!help` for a full list |
| **Settings** | General, Theme, Connections, Exceptions, and Export tabs |
| **Inspector** | Debug view for GUI fingerprint captures |
| **Server Logs** | Historical list of all visited servers with sort, search, and CSV export |

### Console commands

| Command | Description |
|---------|-------------|
| `!help` | List all commands |
| `!info` | Current server summary |
| `!plugins` | List detected plugins |
| `!scan` | Rescan server info |
| `!export json\|csv\|clipboard` | Export data |
| `!db status\|sync\|download\|test\|reset confirm` | API sync operations |
| `!db set baseurl <url>` | Set API base URL |
| `!db set header <name> <value>` | Set auth header |
| `!db remove header <name>` | Remove auth header |
| `!theme [name]` | Switch or list themes |
| `!probe` | Run GUI fingerprint probes |
| `!inspector` | Toggle inspector mode |
| `!clear` | Clear console |

### Settings tabs

| Tab | Contents |
|-----|----------|
| **General** | Auto-scrape, silent scraper, log toggles, HUD summary, inspector mode, reset window positions |
| **Theme** | Live preview theme switcher |
| **Connections** | Database adapter (None / REST API / Archivist), auth headers, endpoints, auto-push |
| **Exceptions** | Manage proxy/hub server list for domain resolution |
| **Export** | JSON, CSV, and clipboard export buttons |

---

## Remote sync

Archivist can push server logs to a REST API. Configure in Settings → Connections:

- **Archivist mode** — simplified setup with base URL, custom auth headers, and reset key
- **REST API mode** — full control over push/download/reset endpoints and auth headers

Auth headers are stored Base64-encoded. The API payload format:

```json
{
  "servers": [
    {
      "timestamp": "2025-03-05",
      "server_info": { "ip": "...", "port": 25565, "domain": "...", "brand": "...", "version": "..." },
      "plugins": [{ "name": "essentialsx" }],
      "detected_addresses": [],
      "detected_game_addresses": [],
      "worlds": [{ "dimension": "minecraft:overworld" }]
    }
  ]
}
```

---

## Data files

| File | Location | Description |
|------|----------|-------------|
| Server logs | `.minecraft/archivist/server-logs/<address>.json` | Per-server visit history |
| Config | `.minecraft/config/archivist.json` | Core settings |
| Extended config | `.minecraft/config/archivist_extended.json` | Scraper, database, logging settings |
| API config | `.minecraft/archivist/api_config.json` | REST API endpoints and auth |
| GUI config | `.minecraft/archivist/gui_config.json` | Window positions, active theme |
| Glossary | `.minecraft/config/archivist-glossary.json` | Command → plugin name mappings |
| Exceptions | `.minecraft/config/archivist-exceptions.json` | Proxy/hub server list |
| Fingerprints | bundled resource | GUI signature patterns |
| Custom themes | `.minecraft/archivist/themes/*.json` | User-created theme files |
| Exports | `.minecraft/archivist/exports/` | Exported JSON/CSV files |

---

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.1 – 1.21.11 |
| Fabric Loader | 0.16.10+ |
| Fabric API | Required |
| Java | 21+ |
