# LocalGlobalChat (Hytale Plugin)
```md
Chat plugin for Hytale featuring **Global/Local chat**, **private messaging**, **admin debugging**, and a **configurable local chat radius**.

---

## Features

- Global and Local chat channels
- Local chat filtered by **distance radius** (same world)
- Private messages with `/msg` (message is **fully pink**)
- Admin-only chat debug toggle
- Admin-only local radius setting (**persists after restart**)
- Optional TinyMessage/TinyMsg support for better colors/formatting

---

## Installation

1. Build the plugin and get the generated `.jar`.
2. Move the plugin `.jar` to:
   - `mods/`
3. (Optional, recommended) Install TinyMessage/TinyMsg:
   - Move `tinymessage-*.jar` to `mods/`
4. Restart the server.

> With TinyMessage/TinyMsg installed, chat colors and formatting look better.

---

## How It Works

- When a player joins, their default chat channel is **Local**.
- **Local chat** is only delivered to players:
  - in the **same world**
  - within the configured **radius** (in blocks)

---

## Commands

- `/g` — Switches you to the **Global** chat channel.
- `/l` — Switches you to the **Local** chat channel (distance-based).
- `/msg <player> <message...>` — Sends a **private message** to another player (fully **pink**).
  - Example: `/msg player2 oi tudo bem com voce ?`
- `/clearchat` — Clears chat for all online players and broadcasts:
  - (Optional alias: `/cc`, if supported in build)
- `/chatdebug` — Toggles **chat debug mode** (shows chat mode + target count). *(Admin only)*
- `/localradius <number>` — Sets the **Local chat radius** in blocks (saved/persistent). *(Admin only)*

---

## Permissions

| Command | Permission | Recommended Role |
|--------|------------|------------------|
| `/g` | *(none)* | Everyone |
| `/l` | *(none)* | Everyone |
| `/msg` | *(none)* | Everyone |
| `/clearchat` | `localglobalchat.admin.clearchat` | Admin |
| `/chatdebug` | `hytale.command.chatdebug` | Admin |
| `/localradius` | `hytale.command.localradius` | Admin |

---

## Persistence (Local Radius)

The local radius is saved to a config file and persists after a restart.

Default fallback path: ./plugins/LocalGlobalChat/localglobalchat.properties

```

Content in file "localglobalchat.properties"

```

Stored key: localRadius=50 //example
``` 

## Notes

- Local chat filtering:
  - Same world
  - Distance ≤ configured radius
- The plugin automatically uses TinyMsg if installed.

