# Group TCG privacy contract

This document records the intended data boundary for the RuneLite plugin and
its optional privately hosted Cloudflare backend.

| Data | Local RuneLite profile | Private Worker/D1 | OSRS Wiki |
| --- | --- | --- | --- |
| Active RuneScape display name | Yes | Yes, after server opt-in | Never |
| Jagex login/email/password/session | Never read | Never | Never |
| Account ID, bank PIN | Never read | Never | Never |
| Inventory, bank, equipment | Used transiently for visible restrictions | Never | Never |
| Stats, XP, world, location, clan, chat | Not required | Never | Never |
| OSRS TCG card names and copies | Yes | Yes, after server opt-in | Image name only when artwork is enabled |
| Foil/debug flags and pull times | Yes | Yes, after server opt-in | Never |
| Raw OSRS TCG source-instance ID | Yes | Never; SHA-256-derived opaque ID only | Never |
| Selected solo/shared mode | Yes | Yes | Never |
| Pack events and Top Trumps data | Yes | Yes | Never |
| Group/member ID, invite, bearer token | Yes as required | Received over HTTPS; only invite/token hashes stored | Never |

## Why the display name is stored

Approved server membership replaces the old GIM-roster and RuneLite-Party
requirement. The display name lets the owner verify join requests and lets the
plugin match a right-clicked in-game player to an approved server member.

The display name is sent only after **Connect to server** is enabled and a
create/join action is performed. The backend is private, but its Cloudflare
account owner can inspect D1 and backups. Players should join only servers run
by people they trust.

## Credentials

Member bearer tokens and invite codes are high-entropy Group TCG credentials,
not Jagex credentials. The Worker stores SHA-256 hashes rather than their raw
values. The one-time setup key remains an encrypted Worker secret and is not
stored by the plugin or D1.

The local member token is scoped to one RuneLite character profile and the
exact server URL that issued it. Changing the configured URL does not redirect
an existing token.

## Logging

Worker error logging uses generic messages. Request bodies, authorization
headers, setup keys, display names, group/member IDs, card data, and challenge
payloads must not be deliberately written to logs.

Cloudflare necessarily processes normal connection metadata, including IP
addresses. The deployment owner controls Cloudflare settings, D1 retention,
backups, and deletion.

## Artwork

Artwork downloads are disabled by default. When enabled, the client requests
only fixed OSRS Wiki image URLs. The Wiki receives the connection IP and image
URL but no display name, server credential, collection, or challenge data.

## Review checklist for data changes

1. Search request DTOs and Worker routes for new identity or gameplay fields.
2. Update this table and the README before releasing any new field.
3. Confirm no Jagex credential, session, inventory, bank, location, or chat data
   is introduced.
4. Confirm sensitive values are absent from logs, exceptions, and analytics.
5. Run the plugin and backend privacy/build checks from a clean checkout.
