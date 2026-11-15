---
name: player-text-no-dots-arrows-dashes
description: "Player-facing text never uses middle dots, arrow characters or em dashes, and never explains the UI itself (no \"rest the cursor for modes\")"
metadata:
  node_type: memory
  type: feedback
  originSessionId: 3a97717d-b68b-4de6-8290-7794a6e8000f
  modified: 2026-11-15T11:19:35.604Z
---

In anything the player reads (GUI, HUD, wheel, ability descriptions, hints, chat lines) never use the middle dot `·`,
arrow characters (`→ ← ↑ ↓ ▸`) or the long dash `—`. Rephrase with a comma, a colon, a full stop or plain words
("За десять хвилин вона обірветься", "Enter: сказати"). A direction is drawn (a pixel dial, a pixel chevron), not typed.
Also never write text that only explains the widget ("Затримайте курсор — режими", "Відпустіть тут — скасувати",
"Закріплено · R або Esc — закрити"): show it with layout, colour and icons instead.

**Why:** the user (15.11.2026) called the old marionette card under the wheel "максимально AI-LIKE": dots between every
field, dashes, arrows and hint lines read as generated text, not a game. They asked for laconic info with icons in
different colours instead.

**How to apply:** check every new player-facing string for these characters before finishing. Code comments are not
covered (they are English, for developers). As of 15.11.2026 about 336 older literals in 127 Kotlin files still contain
`—`/`·`/arrows (ability descriptions, hints); the wheel, the marionette card and the marionette messages are clean. Offer a
sweep rather than assume one was asked for. Related: [[marionette-will-and-orders]], [[immersive-abilities-direction]].
