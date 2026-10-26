# Syncing inkstash across your devices

A **mesh** links the inkstash installs on your devices, for example a phone with a good camera and a child's tablet. Linked devices keep the collection the same:

- Cards you add, move or remove on one device show up on the others within a moment.
- Pools and temp sets are shared too, including renames and deletes.
- Each device keeps a full copy, so every device works on its own when the others are away, and catches up when they come back.

Nothing goes through a cloud service, and there is no account. Your devices connect to each other directly, so they must be able to reach each other: the same Wi-Fi at home, or a VPN such as Tailscale or ZeroTier. The card database, prices and settings stay per device.

## Setting it up

Open **Settings → Sync with other devices**.

### Start a mesh on the first device

1. Check the device's name. Other devices show it under this name.
2. Under **Start a mesh here**, pick a passphrase of at least 8 characters (three words work well), enter it twice and tap **Start mesh**.

### Add a device with a QR code

1. On a device that is already in the mesh, tap **Show a code to scan**.
2. On the new device, open **Settings → Sync with other devices**, tap **Scan a code** and point the camera at the code.

That is all: the device joins and starts syncing. The code works for ten minutes and for one device. It holds the passphrase, so only show it to the device you want to add.

### Add a device by typing

1. On the new device, under **Join a mesh**, type the address of a device in the mesh (shown under **This device**, for example `192.168.1.20`) and the passphrase, then tap **Ask to join**.
2. The new device shows a six-digit number. The device it contacted shows a request with a number too.
3. If the two numbers match, tap **Yes, same number** on the existing device. If they differ, tap **No**: something sits between the two devices.

## Removing and leaving

Under **Devices in this mesh**, tap **Remove** next to a device. It stops getting updates but keeps the copy it already has. **Leave this mesh** takes the current device out: it tells the members it can reach, forgets the passphrase and keeps its own copy of everything. It can join again later.

## What happens when devices disagree

Changes are kept as a list of small steps ("add 2 of this card to Trade binder"), and every device applies the same steps. Two devices adding the same card both count. If one device deletes a pool while another adds cards to it, the cards end up in General. Nothing is lost by syncing, with one exception: if two devices each remove the last copy of a card at the same time, the count can end below what either expected, and the app shows it as none.

Both devices must be open to sync. A device that was away catches up the next time it connects.

## How it stays private

- **The passphrase never crosses the network.** Each connection proves both sides know it with SPAKE2 (RFC 9382, P-256), a password-authenticated key exchange. A recording of a connection gives no way to test guesses offline, and each guess needs a live attempt, which inkstash slows down after every failure (1 s, doubling to 5 minutes, per address).
- **Everything is encrypted.** The SPAKE2 result is the pre-shared key of a Noise `XX` handshake (X25519, ChaCha20-Poly1305, SHA-256), which also exchanges each device's long-term key and encrypts all traffic with forward secrecy. A wrong passphrase fails on the first Noise message, before either side shows its key.
- **The passphrase alone doesn't let a device in.** A member approves each new device by comparing the six-digit number, or hands it a QR code. Admissions and removals are Ed25519-signed records that every device checks against the members it already trusts. Each member's keys are fixed in its record, so a device that knows the passphrase still can't pose as a member.
- **A QR invite pins the key.** The code carries a fingerprint of the inviting device's key, and the new device refuses to continue if the device it reached has a different one.

Every member is fully trusted: any member can add or remove cards, places and members. Add only devices you control.

## Limits

- No relays: two devices that can't reach each other directly don't sync, even if both reach a third. Both reaching a third device does let them find each other's addresses through it.
- Devices sync only while the app is running; there is no background service.
- IPv4 addresses only, and no tunnel support (the protocol is plain TCP on port 41890, changeable in code).
- Loading a backup while in a mesh replaces the collection on every device.
- Clocks: renames use the newest by device clock, so a device with a wrong clock can win or lose a rename it shouldn't.
- Removing a device doesn't change the passphrase. A removed device that still knows it can't rejoin by itself (its record says removed, and joining needs approval), but if it might be in wrong hands, start a new mesh.

## For developers

| Path | What |
| --- | --- |
| `mesh/Spake2.kt` | SPAKE2 over P-256, tested against the RFC 9382 vectors |
| `mesh/MeshHandshake.kt` | SPAKE2 + Noise, framing, `SecureChannel` |
| `mesh/MeshRecords.kt`, `MeshIdentity.kt` | Signed admission and removal records, device keys, six-digit code |
| `mesh/MeshProtocol.kt` | Wire messages, `MeshInvite` (the QR payload) |
| `mesh/MeshNode.kt` | Listener, connector, admission, sessions, sync, throttle |
| `data/SyncStore.kt`, `SyncPayload.kt` | Operation log, vectors and the merge rules |

Collection changes go through `CollectionRepository`, which records each as operations in the log and applies them with the code that applies other devices' operations. Quantity changes are additions (they commute); a place is created once, renamed by the newest rename, and soft-deleted (`deleted`), with copies left in it or added to it later sent home (the place a temp set borrowed them from, else General). Operations a device receives are accepted in order per origin, and anything referring to a place not here yet waits unapplied and is retried.
