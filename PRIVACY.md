# Privacy

> [!WARNING]
> **Only connect to private multiplayer servers run by friends you trust.**
> Group TCG deliberately has no official public server, so each group chooses
> which trusted friend controls its endpoint and synced data.
> The server host controls the endpoint and may see or record your IP address
> and synced Group TCG data. Use a VPN if you need to hide your IP address.
> Private servers are independently operated; the Group TCG creator does not
> operate, verify, endorse, or accept responsibility for servers hosted by
> other people.
> Solo play does not contact a group server.

Group TCG can be played entirely offline. Connecting to a group server and
downloading card artwork are separate options, and both are off by default.

## What each service receives

| Data | Your RuneLite profile | Your group's server | OSRS Wiki |
| --- | --- | --- | --- |
| RuneScape display name | Yes | Only after you connect | No |
| Jagex login, email, password, or session | Never read | No | No |
| Account ID or bank PIN | Never read | No | No |
| Inventory, bank, or equipment | Checked locally for restrictions | No | No |
| Stats, XP, world, location, clan, or chat | Not needed | No | No |
| OSRS TCG card names and copies | Yes | Only after you connect | Image name only when artwork is on |
| Foil status and pull time | Yes | Only after you connect | No |
| Original OSRS TCG copy ID | Yes | Replaced with a one-way hashed ID | No |
| Solo or shared collection choice | Yes | Only after you connect | No |
| Pack and Top Trumps events | Yes | Only after you connect | No |
| Group ID, invite, and member token | As needed | Sent over HTTPS; only hashes are stored | No |

## Group servers

Group TCG deliberately has no official public multiplayer server. This avoids
silently sending every group's connection data to one central Group TCG service
and makes the infrastructure owner a person the group deliberately chooses.
Private hosting does not provide IP anonymity: that chosen host may still see
or record connecting IP addresses, so use a VPN when the address must be hidden.
Private servers are independently operated; the Group TCG creator does not
operate, verify, endorse, or accept responsibility for servers hosted by other
people.

Your display name lets the host recognise and approve you. It also lets the
plugin match an in-game player with the correct server member for Top Trumps.

The server belongs to its Cloudflare account owner, not to Group TCG. That
person can access its database and backups, so only join servers run by people
you trust.

Invites and member tokens are Group TCG credentials, not Jagex credentials.
The server stores one-way hashes of them. Your member token is saved only in the
RuneLite profile and server URL that created it, so changing the URL cannot send
an existing token somewhere else.

The setup key is used once when the first group is created. It is stored as an
encrypted Cloudflare secret and is never saved by the plugin or database.

## Logging and Cloudflare

The Worker avoids logging request bodies, names, tokens, invites, card data, or
Top Trumps data. Cloudflare still handles normal connection information such as
IP addresses. The server owner controls Cloudflare's logs, backups, retention,
and deletion.

## Card artwork

If artwork is enabled, RuneLite requests fixed image URLs from the OSRS Wiki.
The Wiki receives your IP address and the requested URL. It does not receive
your RuneScape name, collection, or Group TCG server credentials.
