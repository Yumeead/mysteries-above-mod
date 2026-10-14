---
name: resumed-session-reread-files-first
description: "When resuming work an interrupted session started, re-read the target files before trusting any earlier read in the same conversation"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 9adf615e-a315-4bef-9a15-9bbd0ae9dbb5
  modified: 2026-07-21T08:53:09.294Z
---

Коли користувач каже, що попередня сесія почала роботу й обірвалась (закінчились токени),
файли на диску вже можуть бути новішими за те, що Read віддав раніше в ЦІЙ розмові.
2026-07-21 у `MarionettistControl`/`SkinDisguiseService` це призвело до двох раундів
«виправлень» коду, якого на диску вже не було: сервіси личин були переписані з ручних
PacketEvents-пакетів на серверний Paper `setPlayerProfile`, а я лагодив саме ті пакети.

**Why:** застарілий вміст у контексті виглядає точно як актуальний — помилка мовчазна,
а користувач бачить лише «твоя правка не допомогла».

**How to apply:** перед першою правкою в задачі, продовженій за обірваною сесією, перечитати
цільові файли (або `git diff`/`git status`) і звірити з тим, що вже в контексті. Якщо правка
«мала спрацювати, але не спрацювала» — перша гіпотеза не «не той фікс», а «не той вміст файлу».
Пов'язане: [[subagent-session-limit-finish-fix-locally]].
