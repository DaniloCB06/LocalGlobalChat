# LocalGlobalChat (Hytale Plugin)

Chat plugin for Hytale featuring **Global/Local chat**, **private messaging**, **admin tools**, a **configurable local chat radius**, and an **optional chat lockdown mode** (allowlist).

---

## Features

- Global and Local chat channels
- Local chat filtered by **distance radius** (same world)
- Private messages with `/msg` (message is **fully pink**)
- Admin command to **clear the chat** (Global + Local)
- Admin command to **inspect/debug chat variables**
- Admin-only local radius setting (**persists after restart**)
- Admin-only **chat lockdown** mode (disable chat for normal players)
- **Chat Admin allowlist**: players can be allowed to talk even while chat is disabled
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
- When **chat is disabled** via `/chatdisable`, only:
  - server admins (who can run admin commands), and/or
  - players added with `/chatadmin add ...`
  
  will be able to send chat messages (Global/Local).

---

## Commands

### Everyone (no permission required)

- `/g`
  - Switches you to the **Global** chat channel.

- `/l`
  - Switches you to the **Local** chat channel (distance-based).

- `/msg <player> <message...>`
  - Sends a **private message** to another player (fully **pink**).
  - Example: `/msg player2 oi tudo bem com voce ?`

---

### Admin-only commands

- `/localradius <number>`
  - Sets the **Local chat radius** in blocks.
  - Default is `50`.
  - This value is **saved/persistent** after restart.
  - Example: `/localradius 80`

- `/clearchat`
  - Clears the game chat for all online players (**Global + Local**).
  - Alias: `/cc`

- `/chatdebug`
  - Displays **debug information / variables** related to the server chat
    (useful for verifying current settings/state).

- `/chatdisable`
  - Toggles chat lockdown mode:
    - If chat is enabled: disables chat for normal players (only allowed roles/users can talk).
    - If chat is disabled: enables chat globally again.
  - Alias: `/cdb`

- `/chatadmin`
  - Manages the **Chat Admin allowlist** (players who can talk while chat is disabled).
  - Variants:

  - `/chatadmin add (<Player> ou <UUID>)`
    - Adds chat-admin permission to the chosen player (allow to talk even if chat is disabled).
    - Example: `/chatadmin add PlayerName`
    - Example: `/chatadmin add 123e4567-e89b-12d3-a456-426614174000`

  - `/chatadmin remove (<Player> ou <UUID>)`
    - Removes chat-admin permission from the chosen player.

  - `/chatadmin list`
    - Lists all players (and their UUIDs) who currently have chat-admin permission.

---

## Permissions / Access

This plugin uses two practical access levels:

- **Everyone (permission zero)**:
  - `/g`, `/l`, `/msg`

- **Admin level**:
  - `/localradius`, `/clearchat` (`/cc`), `/chatdebug`, `/chatdisable` (`/cdb`), `/chatadmin ...`

Additionally:
- **Chat Admin (allowlist)** is managed by admins via `/chatadmin add/remove/list`.
- Chat Admins are the players who can **still talk when chat is disabled**.

---

## Persistence (Local Radius)

The local radius is saved to a config file and persists after a restart.

Default fallback path:
`./plugins/LocalGlobalChat/localglobalchat.properties`

Content example (`localglobalchat.properties`):

Stored key example:
`localRadius=50`

---

## Notes

- Local chat filtering:
  - Same world
  - Distance ≤ configured radius
- The plugin automatically uses TinyMsg if installed.
