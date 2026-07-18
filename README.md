# Group TCG

[![Build](https://github.com/Sqwiglyy/groupman-tcg/actions/workflows/build.yml/badge.svg)](https://github.com/Sqwiglyy/groupman-tcg/actions/workflows/build.yml)
[![License: BSD 2-Clause](https://img.shields.io/badge/license-BSD%202--Clause-blue.svg)](LICENSE)
[![Fork on GitHub](https://img.shields.io/badge/fork-on%20GitHub-2ea44f.svg)](https://github.com/Sqwiglyy/groupman-tcg/fork)

Group TCG turns cards from
[OSRS TCG](https://runelite.net/plugin-hub/show/osrs-tcg) into Bronzeman-style
rules. Pull a card to unlock the NPC, item, resource, or activity it represents.

> [!WARNING]
> **Only connect to private multiplayer servers run by friends you trust.**
> Group TCG deliberately has no official public server. This avoids silently
> sending every multiplayer group's connection data to one central Group TCG
> service and lets each group choose who controls its endpoint and synced data.
> The server host controls the endpoint and may see or record your IP address
> and synced Group TCG data. Use a VPN if you need to hide your IP address.
> Private servers are independently operated; the Group TCG creator does not
> operate, verify, endorse, or accept responsibility for servers hosted by
> other people.
> Solo play does not contact a group server.

OSRS TCG still handles every pack and card. Group TCG only reads that collection
and applies your chosen restrictions. It does not add cards to OSRS TCG or change
its saved data.

You can play alone or share unlocks with friends. Any RuneScape account type can
join; a Group Ironman team, clan, and RuneLite Party are not required.

## Quick start

1. Install and enable **OSRS TCG** from RuneLite's Plugin Hub.
2. Install and enable **Group TCG**.
3. Open your OSRS TCG collection once so Group TCG can read it.
4. Choose **Solo collection** or **Shared server collection** in the settings.
5. Open packs through OSRS TCG as normal.

No server or Cloudflare account is needed for solo play.

If Group TCG is not yet available in the Plugin Hub, run it from your own fork:

```powershell
git clone https://github.com/YOUR-GITHUB-NAME/groupman-tcg.git
cd groupman-tcg
.\gradlew.bat run
```

Use Eclipse Temurin Java 11. On macOS or Linux, run `./gradlew run` instead.

## Collections

The sidebar lets you:

- search the shared collection or any member's collection;
- see the 20 most recent cards in the selected collection;
- open a full OSRS TCG-style collection album;
- see normal copies, foils, duplicates, and missing cards;
- compare server members on the collection leaderboard.

Leaderboard points are the combined OSRS TCG score of a player's unique cards.
Duplicate copies do not add points. The plugin calculates the leaderboard from
collection data it has already synced, so opening another player's album does
not make another download.

Card artwork is optional. When enabled, it is downloaded from fixed OSRS Wiki
image URLs.

## Playing with friends

Multiplayer uses a small private Cloudflare Worker owned by someone in your
group. Group TCG deliberately does not provide an official public server.

This is a multiplayer safety boundary: server sync is off by default, players
choose the friend who controls their endpoint and data, and joining requires an
explicit trust acknowledgement. It is not IP anonymity—the private host may
still see or record connecting IP addresses. Use a VPN if that address must be
hidden. Private servers are independently operated; the Group TCG creator does
not operate, verify, endorse, or accept responsibility for servers hosted by
other people.

1. The host follows the
   [Group TCG Server setup guide](https://github.com/Sqwiglyy/groupman-tcg-server).
2. Everyone enables **Connect to server** and enters the same Worker URL.
3. The host creates the group and privately shares its group ID and invite code.
4. Friends join, then the host checks each RuneScape name before approving it.
5. Each player chooses whether the shared cards or only their own cards control
   their restrictions.

The server also supports delayed pack popups and consent-based Top Trumps. A
server can have up to 50 active members. Revoking a member removes their access,
but cards already added to the shared unlocks stay unlocked.

## Restrictions

Group TCG can lock:

- combat and other configured NPC actions;
- taking, buying, withdrawing, equipping, eating, drinking, and using items;
- Woodcutting, Mining, Fishing, and Runecrafting;
- Cooking, Firemaking, Smelting, Smithing, Crafting, Enchanting, Fletching,
  and Herblore;
- Farming, Hunter, Slayer, Thieving, and Sailing activities.

Items and NPCs without an OSRS TCG card are left alone. Coins are allowed by
default by can be removed from the exceptions. Restrictions also pause during
live Last Man Standing matches unless you turn that safety option off.

This is an honour-mode plugin. It can block supported RuneLite menu actions and
mark locked targets, but it cannot change the game server or catch every unusual
interaction path.

## Privacy

Server sync and artwork downloads are both off by default.

When you connect to a group server, it receives your RuneScape display name,
Group TCG login token, collection choice, card copies, pack events, and Top
Trumps events. It does not receive your Jagex login, session, account ID, bank
PIN, inventory, bank, equipment, stats, location, clan, or chat messages.

Only join a server run by someone you trust. Its Cloudflare account owner can
access the database and backups. See [PRIVACY.md](PRIVACY.md) for the exact data
list and [SECURITY.md](SECURITY.md) before reporting anything sensitive.

## Troubleshooting

- **No cards appear:** enable OSRS TCG, open its collection once, and check that
  the character has an active RuneLite profile.
- **Shared cards are missing:** check the server URL, owner approval, and sync
  status in the sidebar.
- **A player is still pending:** the host needs to approve their displayed
  RuneScape name.
- **Top Trumps is unavailable:** both players must be approved on the same
  server and have **Enable Top Trumps** turned on.
- **Artwork is blank:** enable **Download card artwork** if you want Wiki images.
- **A development build switches off:** restart RuneLite and check
  `%USERPROFILE%\.runelite\logs\client.log` for the first Group TCG error.

## Building

Group TCG uses Java 11 and the standard RuneLite build:

```powershell
$env:JAVA_HOME='C:\path\to\jdk-11'
.\gradlew.bat clean build
```

The optional Cloudflare server is kept in the separate
[groupman-tcg-server](https://github.com/Sqwiglyy/groupman-tcg-server)
repository.

- [Changelog](CHANGELOG.md)
- [Privacy](PRIVACY.md)
- [Security](SECURITY.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

Maintained by [Sqwiglyy](https://github.com/Sqwiglyy).
