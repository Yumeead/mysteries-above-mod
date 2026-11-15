---
name: run-only-the-needed-game-test
description: When verifying one pathway/feature in game tests, run with -Ponly=<word> instead of the whole suite
metadata:
  type: feedback
---
Run game tests filtered: `./gradlew :mod:runClientGameTest -Ponly=fool` (or `runGameTest -Ponly=fool`), not the full suite.

**Why:** the user complained (14.11.2026) that every run replays every scene of every pathway in the same order, so they wait for the queue to reach the part that matters.

**How to apply:** pick the word from the test class / test method name (fool, door, sun, light, creature, loot…). Run the full suite only before a commit or when asked.
