# Chat Interactions Plugin (Hytale Plugin)

Chat plugin for Hytale featuring **Global/Local chat**, **private messaging**, **admin tools**, a **configurable local chat radius**, an **optional chat lockdown mode** (allowlist), and a **configurable periodic chat-mode warning**.

---

## Requirements (Dependencies)

* **ChatInteractions** has **no required dependencies** to run.
* **TinyMessage / TinyMsg (Optional)**
  If installed, it enables **better colors and formatting** in chat.
  Just drop `tinymessage-*.jar` into `mods/`.

---

## Features

* Global and Local chat channels
* Local chat filtered by **distance radius** (same world)
* Private messages with `/msg` (message is **fully pink**)
* Admin command to **clear the chat** (Global + Local)
* Admin command to **inspect/debug chat variables**
* Admin-only local radius setting (**persists after restart**)
* Admin-only **chat lockdown** mode (disable chat for normal players)
* **Chat Admin allowlist**: players can be allowed to talk even while chat is disabled
* **Periodic chat-mode warning (per-player)**:

  * Sends a private reminder telling the player whether they are in **LOCAL** or **GLOBAL**
  * Fully colored when TinyMessage/TinyMsg is installed
  * Clean plain-text fallback when TinyMessage is not present
  * Configurable interval (or disabled) via `/chatwarning` / `/cw`
* Optional TinyMessage/TinyMsg support for better colors/formatting

---

## Installation

1. Build the plugin and get the generated `.jar`.
2. Move the plugin `.jar` to:

   * `mods/`
3. (Optional, recommended) Install TinyMessage/TinyMsg:

   * Move `tinymessage-*.jar` to `mods/`
4. Restart the server.

> With TinyMessage/TinyMsg installed, chat colors and formatting look better.

---

## How It Works

* When a player joins, their default chat channel is **Local**.

![image](https://media.forgecdn.net/attachments/description/null/description_2a5ad1f2-d25d-4c88-a376-b6dae4a424a9.png)

* **Local chat** is only delivered to players:

  * in the **same world**
  * within the configured **radius** (in blocks)

![image](https://media.forgecdn.net/attachments/description/null/description_9a2d5a82-b6b2-40af-b5cc-75a6a1085a58.png)

![image](https://media.forgecdn.net/attachments/description/null/description_20b2e4d2-37c8-4bb0-9b8d-c8f1a38ce28d.png)

* When **chat is disabled** via `/chatdisable`, only:

  * server admins (who can run admin commands), and/or
  * players added with `/chatadmin add ...`

  will be able to send chat messages (Global/Local).

* **Periodic chat warning** (optional):

  * Each player can receive a **private reminder** message every X minutes.
  * Admins can change the interval or disable it using `/cw <minutes>`.

### "Mini" Tutorial

![image](https://media.forgecdn.net/attachments/description/null/description_14efe3cf-db65-4df8-be88-34b190050427.png)

![image](https://media.forgecdn.net/attachments/description/null/description_a1af0b69-5ba2-489d-8196-46b6392fef36.png)

![image](https://media.forgecdn.net/attachments/description/null/description_7ea56eed-6c28-4d47-9fb5-48771a940855.png)

![image](https://media.forgecdn.net/attachments/description/null/description_b327c6cd-cd0b-4df0-8b13-ac3d5a3d575f.png)

---

## Commands

### Everyone (no permission required)

* `/g`

  * Switches you to the **Global** chat channel.
* `/l`

  * Switches you to the **Local** chat channel (distance-based).
* `/msg <player> <message...>`

  * Sends a **private message** to another player (fully **pink**).
  * Example: `/msg player2 hi how are you?`

---

### Admin-only commands

* `/localradius <number>`

  * Sets the **Local chat radius** in blocks.
  * Default is `50`.
  * This value is **saved/persistent** after restart.
  * Example: `/localradius 80`

* `/clearchat`

  * Clears the game chat for all online players (**Global + Local**).
  * Alias: `/cc`

* `/chatdebug`

  * Displays **debug information / variables** related to the server chat (useful for verifying current settings/state).

* `/chatdisable`

  * Toggles chat lockdown mode:
  * If chat is enabled: disables chat for normal players (only allowed roles/users can talk).
  * If chat is disabled: enables chat globally again.
  * Alias: `/cdb`

* `/chatadmin`

  * Manages the **Chat Admin allowlist** (players who can talk while chat is disabled).

  * Variants:

  * `/chatadmin add (<Player> or <UUID>)`

  * Adds chat-admin permission to the chosen player (allow to talk even if chat is disabled).

  * Example: `/chatadmin add PlayerName`

  * Example: `/chatadmin add 123e4567-e89b-12d3-a456-426614174000`

  * `/chatadmin remove (<Player> or <UUID>)`

  * Removes chat-admin permission from the chosen player.

  * `/chatadmin list`

  * Lists all players (and their UUIDs) who currently have chat-admin permission.

* `/chatwarning <minutes>`

  * Configures the **periodic chat-mode warning** (private reminder).
  * `<minutes>` is the interval in minutes.
  * Use `0` to **disable** the warning.
  * Alias: `/cw`
  * Examples:

    * `/cw 5` (send every 5 minutes)
    * `/cw 0` (disable)

---

## Permissions / Access

This plugin uses two practical access levels:

* **Everyone (permission zero)**:

  * `/g`, `/l`, `/msg`
* **Admin level**:

  * `/localradius`, `/clearchat` (`/cc`), `/chatdebug`, `/chatdisable` (`/cdb`), `/chatadmin ...`, `/chatwarning` (`/cw`)

Additionally:

* **Chat Admin (allowlist)** is managed by admins via `/chatadmin add/remove/list`.
* Chat Admins are the players who can **still talk when chat is disabled**.

---

## Persistence (Local Radius / Chat Admins / Warning Interval)

Settings are saved to a config file and persist after a restart.

Default fallback path: `./plugins/com.example_ChatInteractions/localglobalchat.properties`

Stored keys example:

* `localRadius=50`
* `chatAdmins=<uuid1>,<uuid2>,...`
* `chatWarningMinutes=5`

---

## Notes

* Local chat filtering:

  * Same world
  * Distance ≤ configured radius
* The plugin automatically uses TinyMsg if installed (otherwise clean plain text is used).
