# Group TCG

Group TCG is a RuneLite challenge plugin powered by the
[OSRS TCG](https://runelite.net/plugin-hub/show/osrs-tcg) collection. Tracked
NPCs, items, gathering nodes, recipes, and skill interactions remain locked
until the required card has been collected.

Any RuneScape account can use Group TCG. It does not require Group Ironman,
Hardcore Group Ironman, a clan, or a RuneLite Party.

This is an honour-mode plugin. It blocks supported RuneLite menu clicks and
marks locked content, but it cannot change the game server or guarantee that
every possible interaction is intercepted.

## Collection modes

- **Shared server collection:** every approved member on the same private
  Group TCG server contributes cards to one permanent unlock collection.
- **Solo collection:** only the active character's cards unlock content for
  that character. The player may still join a private server, share pack
  openings, browse friends' collections, and play Top Trumps.

The setting belongs to each player. A solo player can duel a shared-collection
player: the server draws the solo player's card from their personal collection
and the shared player's card from the server collection.

## What every player needs

- The official desktop RuneLite client.
- **OSRS TCG** installed and enabled from the Plugin Hub.
- **Group TCG** installed and enabled.
- A RuneLite profile for the active character.
- For multiplayer, the exact same private server URL and an approved server
  membership.

There is no public default server.

## Start solo without a server

1. Enable **OSRS TCG** and **Group TCG**.
2. Set **Collection mode** to **Solo collection**.
3. Open packs in OSRS TCG. Cards immediately unlock their matching content.
4. Review the restriction settings before committing to the challenge.

Server features remain unavailable until **Connect to server** is enabled and
the character creates or joins a private group.

## Create a private multiplayer server

One person hosts the server; everyone else joins it.

1. Follow the beginner deployment guide in
   [Group TCG Server](https://github.com/Sqwiglyy/groupman-tcg-server). Deploy
   one Cloudflare Worker and D1 database, then configure its encrypted
   `SETUP_KEY`.
2. Open `https://YOUR-WORKER.workers.dev/health`. Confirm it reports
   `"status":"ok"`, `"version":4`, and `"setupReady":true`.
3. In RuneLite, the owner enables **Connect to server**, enters the Worker root
   URL under **Server URL**, logs into the character they will use, and opens
   the Group TCG sidebar.
4. Select **Create private group** and enter the private Worker setup key. The
   setup key is sent once over HTTPS and is not saved by the plugin.
5. Send the displayed group ID and invite code privately to friends. Do not
   share bearer tokens or post invite details publicly.
6. Every friend enables **Connect to server**, enters the same Worker URL,
   logs into their character, selects **Join private group**, and enters the
   group ID and invite code.
7. The owner checks the displayed RuneScape name and approves only the expected
   person.
8. Each player chooses **Shared server collection** or **Solo collection**.

The private Worker accepts up to 50 active memberships. Invites last 30 days
and can be rotated. Revoking a member removes their API access; permanent
grow-only shared unlocks remain.

Every member of one group must use the exact same server URL. Changing the URL
does not move a group or its database.

## Multiplayer behaviour

- Shared unlocks, individual collections, and provenance are maintained in D1
  and downloaded after reconnecting.
- Pack openings are uploaded when **Share pack openings** is enabled. Other
  online members receive the miniature popup no sooner than 15 seconds after
  the server receives the opening; the opener does not receive their own popup.
- Missed pack openings replay after a player comes back online.
- Top Trumps challenges travel through the private server, not RuneLite Party.
- Challenge a friend from their button in the sidebar even when they are not
  standing nearby, or right-click their in-game character and select
  **Top Trumps**.
- The challenged player must accept. The server then draws one random card for
  each player from the collection mode each player selected and both clients
  calculate and display the same winner.

## Privacy and network behaviour

**Connect to server** and **Download card artwork** are explicit opt-ins.

The selected private Worker receives:

- the active RuneScape display name, used for membership approval and in-game
  right-click matching;
- random group/member IDs and the player's selected collection mode;
- Group TCG bearer tokens and invite codes over HTTPS, with only hashes stored
  in D1;
- opaque hashes of OSRS TCG source-instance IDs;
- card names, foil/debug flags, pull timestamps, shared unlocks, pack contents,
  and Top Trumps challenge/results needed for multiplayer.

The plugin never sends a Jagex password, Jagex account email, game-session
token, account ID, bank PIN, inventory, bank, equipment, stats, XP, world,
location, clan roster, or chat message to the Worker. The Worker code avoids
logging request bodies, credentials, display names, or gameplay payloads.

The server is privately hosted, but its Cloudflare account owner controls the
database and can access stored data and backups. Only join a server run by
someone you trust. Cloudflare necessarily processes connection metadata such
as IP addresses.

The bearer token is scoped to the RuneLite profile and the Worker that issued
it. If a RuneScape display name changes, disconnect that profile and join
again under the new name.

The field-by-field boundary is documented in [PRIVACY.md](PRIVACY.md).

Optional artwork requests go only to fixed OSRS Wiki image URLs. The Wiki can
see the connecting IP address and requested image URL, but no RuneScape display
name is included in those requests.

## Visual assets and attribution

Pack reveals and Top Trumps use RuneLite's bundled RuneScape-style fonts and
an adaptation of OSRS TCG's card renderer. The rarity frame, themed sections,
examine text, score, foil treatment, card back, lock badge, and standard pack
art are redistributed under OSRS TCG's BSD 2-Clause licence.

The multiplayer labels, badges, countdown, popup layout, and winner treatment
are Group TCG additions. The exact upstream version and attribution are in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Restriction coverage

- Combat and configured interactions with locked tracked NPCs.
- Taking, telegrabbing, equipping, buying, withdrawing, consuming, and using
  locked tracked items.
- Grey NPC and ground-item outlines, shaded inventory/bank/equipment items,
  and optional padlock markers.
- Woodcutting, Mining, Fishing, and Runecrafting gathering requirements.
- Cooking, Firemaking, Smelting, Smithing, Crafting, Enchanting, Fletching,
  and Herblore input/output modes.
- Farming, Hunter, Slayer, Thieving, and Sailing specialist rules.

Items and NPCs without an OSRS TCG card are not restricted. Coins are exempt
from the general item lock by default. Restrictions suspend in live Last Man
Standing matches unless that safety option is disabled.

## Updates, backups, and recovery

- RuneLite updates approved Plugin Hub plugins automatically.
- The server owner updates and backs up the Worker separately using the
  [server update and backup guide](https://github.com/Sqwiglyy/groupman-tcg-server#updates-and-backups).
- Disconnecting removes the local server credential but does not delete D1
  data. Cached shared unlocks are ignored while solo mode is selected.
- Revoking a member prevents future access but does not erase permanent shared
  unlocks or existing history.

## Troubleshooting

- **The plugin switches itself off:** fully restart the dev client after
  rebuilding. Check `%USERPROFILE%\.runelite\logs\client.log` for the first
  `GroupTcg` or `GroupmanTcg` exception.
- **No cards appear:** enable OSRS TCG, open its collection once, and confirm
  the character has an active RuneLite profile.
- **Shared mode uses local cards only:** enable **Connect to server**, use the
  correct Worker URL, and wait for owner approval and the first sync.
- **A member remains pending:** the owner must verify the displayed RuneScape
  name and approve it in the sidebar.
- **Top Trumps is missing on right-click:** both characters must be approved on
  the same server, server sync must be online, and **Enable Top Trumps** must be
  on. The sidebar challenge button works without the player being nearby.
- **The Worker says it is claimed:** that deployment already owns a group. Use
  its existing invite or deploy a separate Worker.
- **Artwork is blank:** artwork downloads are disabled by default.

## Development and Plugin Hub readiness

Group TCG requires Java 11. Run:

```powershell
$env:JAVA_HOME='C:\path\to\jdk-11'
.\gradlew.bat clean build
```

The Plugin Hub artifact is Java-only and uses the standard build. The optional
Cloudflare backend is a separately deployed service configured by the user.
Before submission, complete a two-account test covering create/join/approval,
both collection modes, pack replay, sidebar and right-click Top Trumps,
restrictions, shutdown, and restart. See
[PLUGIN_HUB_CHECKLIST.md](PLUGIN_HUB_CHECKLIST.md).

Maintained by **Sqwiglyy**.
