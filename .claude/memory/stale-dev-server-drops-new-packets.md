---
name: stale-dev-server-drops-new-packets
description: The user keeps a long-running runServer; after server-side changes it must be restarted or new packets are silently ignored
metadata:
  node_type: memory
  type: feedback
  originSessionId: 205432bc-1290-4c2e-8f6d-1fb55165de4c
  modified: 2026-10-15T15:19:19.436Z
---

The user often keeps `:mod:runServer` running for hours and plays on it from `runClient`. `runClient`
recompiles the client, but the already running server keeps the old code. A new serverbound channel
then reaches a server with no receiver for it and is dropped without a single log line, which looks
like "I do X and nothing happens".

**Why:** Ability wheel M4 (15.10.2026): `ABILITY_ACTION` did nothing; the server had been running since
13:12, before the receiver was added. The bytecode-level investigation of the client was wasted time.

**How to apply:** when a change touches `mod/src/main` and the user reports "nothing happens" in
game, first check the start time of the `KnotServer` java process (Win32_Process CreationDate) and
tell them to restart the server before debugging the code. Related: [[in-game-verification-with-two-clients]].
