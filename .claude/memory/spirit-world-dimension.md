---
name: spirit-world-dimension
description: Духовний світ — перший власний вимір мода; далі планується ще кілька світів
metadata: 
  node_type: memory
  type: project
  originSessionId: be9dd7e5-ec6a-4f65-99f4-e0128cf0fb8e
  modified: 2026-09-01T18:30:12.059Z
---

`mysteries_above:spirit_world` — біле дзеркало реального світу (кальцит під сніговою
кіркою, поверхня ~y 63 хвилею ±2 бл., біла імла, вічний полудень, `coordinate_scale: 1.0`).
Оголошений даними в `mod/src/main/resources/data/mysteries_above/`, коду — лише
`mysteries/world/SpiritWorld.kt`.

**Чому:** це **перший із багатьох** запланованих світів, а не деталь шляху Дверей.
Наступні світи додаються так само — датапаком, не кодом.

**How to apply:** нові виміри класти поруч тими самими чотирма файлами (`dimension_type`,
`dimension`, `worldgen/noise_settings`, `worldgen/biome`); формати 26.2 перевіряти
розпакуванням `data/minecraft/...` із деобфускованого джара, а не з пам'яті. Перевірка
без клієнта: `./gradlew :mod:runServer` із командами в stdin (`execute in <dim> if block …
run say …`) — вимагає `mod/run/eula.txt`. Див. [[pathway-abilities-should-be-hard-and-lore-heavy]].
