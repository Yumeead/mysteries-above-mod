# CLAUDE.md

Настанови для Claude Code при роботі в цьому репозиторії.

## Що це

`mysteries-above-mod` — **Fabric-мод мовою Kotlin** за мотивами *Lord of the Mysteries*.
Гравці стають **Потойбічними** (Beyonders), просуваються **Шляхом** (Pathway) через
**Послідовності** (9 — найслабша → 0 — найсильніша) і отримують **Здібності**, випиваючи зілля.

Це **порт** плагіна [`Yumeead/mysteries-above`](https://github.com/Yumeead/mysteries-above)
(Java / Spigot-Paper, 79 054 LOC), а не новий проєкт.

**Плагін ЗАМОРОЖЕНО 26.08.2026.** Нових фіч туди не додається — уся розробка тут.
Але плагін лишається **джерелом правди** для порту: правила, баланс, лор, конфіги, тексти.
Читати його вільно й потрібно; писати в нього — ні.
Локальна копія: `C:\Users\yuryp\IdeaProjects\mysteries-above`.

**Повний план порту (17 фаз, поміряні обсяги, рішення, ризики):**
https://claude.ai/code/artifact/79182c35-c186-4232-b3b3-773466c48901

## Команди

```bash
./gradlew build                  # усе
./gradlew :core:test             # лише правила, без гри — швидко
./gradlew :mod:test              # планувальник і сесії; гру теж не піднімає
./gradlew :core:test --configure-on-demand   # не конфігурує :mod, ще швидше
./gradlew :mod:runServer         # сервер розробки
./gradlew :mod:runClient         # клієнт розробки
```

Maven тут не використовується (це проєкт плагіна). Gradle — через wrapper, окремо ставити не треба.

## Архітектура

| Модуль | Що всередині | Збирається без Minecraft |
|---|---|---|
| `core` | чисті правила: прогресія, баланс, стан, VO | **так** |
| `mod` | Fabric: ефекти, міксини, персистентність, UI | ні |

**Головний інваріант: `core` НЕ має ні `fabric-loom`, ні залежності на `minecraft`.**
Якщо туди протече платформний тип, модуль просто не збереться. У плагіні цю межу тримав
ArchUnit-тест; тут її стереже компілятор — не додавай у `core/build.gradle.kts` нічого,
що це послаблює.

Пакети дзеркалять плагін (`me.vangoo.domain.*`), щоб два дерева порівнювались файл-у-файл.

### Шов здібностей: чим здібність є проти того, що вона робить

Здібність розрізана рівно по межі модулів (фаза 03):

| Половина | Де | Що всередині |
|---|---|---|
| `AbilitySpec` | `core/domain/abilities` | ідентичність, тип, ціна, кулдаун, множник опору глузду |
| `Ability` | `mod/mysteries/ability` | `cast(CastContext)`, назва, опис, ефекти |

`Ability` реалізує `AbilitySpec`, тож `Beyonder` тримає список здібностей, не бачачи
жодного платформного типу. У плагіні цієї межі не було: `Ability` тягнув
`IAbilityContext`, той — `org.bukkit.Location`, і весь домен ставав платформним.

**Каст — це квиток, а не прапорець.** `Beyonder.authorize(spec)` видає `CastTicket`,
`Beyonder.settle(ticket)` гасить його рівно один раз. Здібність, що чекає вводу,
просто тримає квиток непогашеним — це і є плагінний `deferred`, але без коментаря
«КРИТИЧНО: НЕ встановлювати кулдаун». Не погасив — каст був безкоштовний; погасив
двічі — `check()`.

**Контекст не є фасадом над Minecraft.** Кастер — живий `ServerPlayer`, тож
`caster.health`, `caster.isInWater`, `level.sendParticles(…)` кличуться напряму.
У контексті — тільки сервіси самого мода (`beyonders`, `cooldowns`, `targeting`,
`messaging`, `scheduling`), і він росте пофазно: `events` — 06, `effects` — 07,
`ui` — 11, `summons` — 12. Обгортку навколо одного ванільного виклику не додавати —
саме так плагін і набрав `IDataContext.getFoodLevel(UUID)`.

**Квиток лежить у `CastContext`, не в `AbilityContext`.** Пасивка цокає без квитка:
платити нема за що, гасити нема чого. Тому `AbilityContext` (такт пасивки, вмикання
тогла, вхід і вихід гравця) квитка не має зовсім, а `CastContext : AbilityContext`
додає `cast` і видається лише в `ActiveAbility.cast`. Пасивці не доводиться писати
`cast!!` — вона про квиток не знає.

### Час: планувальник і сесії (фаза 04)

У Fabric планувальника немає взагалі — є подія кінця тіку. Свій лежить у
`mod/mysteries/schedule` і не залежить від Minecraft, тому перевіряється звичайним
юніт-тестом.

| Що | Де | Для чого |
|---|---|---|
| `TickScheduler` | `schedule` | черга робіт: `after`, `every`, скасування, ізоляція винятків |
| `ServerTickDispatcher` | `schedule` | `CoroutineDispatcher` + `Delay` поверх тієї ж черги |
| `SessionHost` | `ability/runtime` | реєстр живих сесій, реалізація `Scheduling` |
| `PassiveTicker` | `ability/runtime` | такт пасивок і тоглів, їх життєвий цикл |

**Годинник — тік, а не стінний час.** Лічильник цокає лише в `TickScheduler.advance()`,
тобто рівно тоді, коли цокнув сервер. Одиночна гра на паузі стоїть — і сесії разом із
нею. `delay(1.seconds)` через `ServerTickDispatcher` — це рівно 20 тіків сервера,
скільки б реального часу вони не зайняли. Саме тому `Delay` і реалізовано, попри
опт-ін на `@InternalCoroutinesApi`: без нього перший же `delay()` тікав би на стінний
годинник, і сесія прокидалась би з боргом у тіках.

**Сесія — це корутина, а не клас із `BukkitTask`.** У плагіні таких класів було 22, і
кожен ніс той самий каркас вручну: поле `task`, лічильник `runs`, ідемпотентний
`cancel()`, зняття себе з мапи й `Bukkit.getPlayer(ownerId)` на початку кожного такту.

```kotlin
context.session {                       // ключ — гравець + ця здібність
    try {
        val end = sustain(periodTicks = 2, upkeep = periodicCost) { player -> trail(player) }
        if (end == SessionEnd.EXHAUSTED) /* … */
    } finally {
        owner?.removeEffect(MobEffects.SPEED)   // прибирання на будь-якому виході
    }
}
```

- **Перекаст замінює сесію** — це поведінка реєстру `SessionHost`, а не обов'язок
  здібності. У плагіні це був пункт документації.
- **Прибирання пишеться в `finally`.** Скасування (перекаст, вихід із гри, зупинка
  сервера) прилітає як `CancellationException` і `sustain` із нього не повертається.
  Розкрутка стека диспетчиться в чергу, тож `finally` відпрацьовує **наступним тіком** —
  через це `AbilityServices.reset()` робить іще один прощальний `advance()`.
- **Гравця не кешувати між тактами.** Після смерті чи переходу між вимірами сервер
  створює новий `ServerPlayer`; `SessionScope.owner` щоразу питає список сервера наново.
- **Утримання — `Beyonder.payUpkeep(amount)`**: або списалось повністю, або не списалось
  нічого. Ціну оголошує `AbilitySpec.periodicCost(sequence)`.

**Період пасивки оголошується, а не рахується.** `Ticking.tickPeriodTicks` — число
(`1` — щотік, `20` — раз на секунду). Плагінне `if (++runs % 20 == 0)` було станом у полі
здібності, що розсинхронізовувався з перезаходом гравця.

**`runAsync` не переїхав навмисно.** У Bukkit ним тікали з головного потоку, і половина
викликів робила це про всяк випадок. Тут будь-яка робота і так на серверному потоці, а
справжня фонова — це `withContext(Dispatchers.IO)` усередині сесії, написане явно.

### Клієнт-серверна межа всередині `mod`

`mod` розділений на два source sets (`splitEnvironmentSourceSets()`):

| Source set | Що всередині | Бачить `net.minecraft.client` |
|---|---|---|
| `mod/src/main` | спільне: логіка сервера, пакети, команди | **ні** |
| `mod/src/client` | екрани, рендер, клієнтські приймачі, keybind'и | так |

Інваріант той самий, що й у `core`, і стереже його той самий компілятор: клієнтський
тип у `src/main` — не рантайм-падіння на виділеному сервері, а помилка збірки.

**Пакети — лише через `me.vangoo.mysteries.net.ModChannels`.** Це один об'єкт, де
оголошення каналу і є його реєстрацією; ad-hoc повідомлень по фічах бути не має.
Напрямок — частина типу: `ClientboundChannel.sendTo(player)` є тільки в спільному коді,
`ServerboundChannel.sendToServer()` — тільки в клієнтському. Переплутати сторони не вийде.

## Конвенції конвертації Java → Kotlin

Зафіксовані на перших 44 файлах, тримайся їх:

- Java `record` → `data class` з `require()` в `init`.
- Клас зі статикою й приватним конструктором → `object`; `public static final` → `const val`.
- **Якщо конструктор нормалізує вхід — НЕ `data class`.** Згенерований `copy()` пройшов би
  повз нормалізацію. Так зроблено в `Mastery` (округлення), `LootTableData` (затиск меж),
  `CustomItem` (захисна копія `lore`): звичайний клас із ручними `equals`/`hashCode`.
- `Optional<T>` → `T?`. Гарди `IllegalStateException` → `check()`, аргументів → `require()`.
- Тести лишаються на **JUnit 5**, не `kotlin.test`: тіла переїжджають із плагіна майже
  дослівно, і збережений API прибирає цілий клас помилок перекладу.
- ❌ Не лишай Java-геттер поруч із властивістю: `fun getValue()` і `val value` — це той самий
  `getValue()` на рівні JVM, буде platform declaration clash.
- ❌ Kotlin не змішує `Int` і `Long` у `max`/`min`. Підлоги й стелі, що порівнюються з
  `roundToLong()`, оголошуй як `Long` — інакше затиск станеться після усічення, а не до нього.

## Пастки тулчейну (обидві коштували збірок)

1. **ID плагіна loom — `net.fabricmc.fabric-loom`, НЕ `fabric-loom`.** Короткий вантажиться
   (лог каже «Fabric Loom: 1.17.19»), але потім вимагає непорожню конфігурацію `mappings`.
   Симптом виглядає як проблема мапінгів 26.2 — це не так, і гнатися за мапінгами марно.
2. **На 26.x рядка `mappings` не має бути взагалі** — гра вже не обфускована:
   yarn зупинився на 1.21.11, `officialMojangMappings()` для 26.2 не існує, а
   `intermediary` віддає сентинел `0.0.0`, який дає `Could not find 'named' mapping position`.
3. **MC 26.2 вимагає Java 25**, не 21.

Канонічне джерело версій — `FabricMC/fabric-example-mod` гілка `26.2`
(`gradle.properties` + `build.gradle`). **Не вгадувати версії з пам'яті.**

## API 26.2 проти того, що пам'ятає модель

26.x перейменувала й переробила частину API. Виміряно на фазах 02 і 03 — кожен рядок
нижче спершу не скомпілювався:

| Було (1.21.x, у пам'яті моделі) | Стало на 26.2 |
|---|---|
| `net.minecraft.resources.ResourceLocation` | `net.minecraft.resources.Identifier` |
| `Minecraft.screen` | `Minecraft.gui.screen()` |
| `Minecraft.setScreen(s)` | `Minecraft.gui.setScreen(s)` (є ще `setScreenAndShow`, але той форсує кадр) |
| `Renderable.render(GuiGraphics, …)` | `Renderable.extractRenderState(GuiGraphicsExtractor, …)` — рендер став retained-mode |
| `GuiGraphics.drawString/drawCenteredString` | `GuiGraphicsExtractor.text(…)` / `centeredText(…)` |
| `Screen.keyPressed(int, int, int)` | `Screen.keyPressed(KeyEvent)` |
| `Button.OnPress` без аргументів натискання | `AbstractButton.onPress(InputWithModifiers)` |
| `ServerLevel.getEntity(UUID)` | `ServerLevel.getEntityInAnyDimension(UUID)` (+ `getPlayerInAnyDimension`) |
| `Level.getGameTime()` | нема; монотонний лічильник — `MinecraftServer.getTickCount()` |
| `LivingEntity.hurt(source, amount)` | `hurtServer(ServerLevel, source, amount)` |

**Не вгадувати сигнатури — дивитись у джарі.** Loom уже поклав деобфусковані джари
поруч, тож перевірка коштує секунду:

```bash
MCM=~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.2/minecraft-merged-deobf-26.2.jar
MCC=~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar
javap -cp "$MCM:$MCC" net.minecraft.client.gui.screens.Screen
```

Так само перевіряється Fabric API — його джари лежать у
`~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/`.

## Ухвалені рішення (26.08.2026, остаточні)

1. **Клієнт-серверний мод**, не серверний. Справжні `Screen`, справжній рендер моделей.
   `sgui` і BetterModel викреслені з плану. Розділені source sets + другий entrypoint +
   пакетний шар — фаза 02, рано, бо це розкладка модулів, а не фіча.
2. **Збережень не існує — міграції немає.** Формати персистентності проєктуються з нуля
   під те, як зручно моду; повторювати форму DTO плагіна не треба.
3. **Переносяться всі 22 шляхи**, включно з 13 заглушками (зілля/характеристики/кольори,
   без здібностей).
4. **Церкви й ордени НЕ переносяться взагалі** — підсистему, ймовірно, переробимо з нуля.
   Поза скоупом: `domain.organizations` (23 файли, 15 тест-класів), `SecretOrderService`
   (1 769 LOC), `ChurchService` (1 194), дуелі ініціації, шрайни, кишенькові світи, 2 меню.
5. **Fabric, не NeoForge** — зважено 26.08.2026 під амбіції проєкту (генерація міст,
   живі NPC, складний AI істот, важка візуалка здібностей). Жодна з них у лоадер не
   впирається: це ванільні системи, різниця — відсотки обсягу. За Fabric: 26.2 наявна
   сьогодні (NeoForge історично відстає на кожній версії), `fabric-language-kotlin` у
   лок-степі з Kotlin проти стороннього адаптера, компіляторна межа клієнт/сервер
   (`splitEnvironmentSourceSets`; у NeoForge лише рантаймний `Dist`), і mixin як штатний
   інструмент — а верхні Послідовності переписують правила гри, тобто лізуть туди, куди
   жодна шина подій хука не поставила.
   **Що свідомо платимо, разово, у фазах 06–07:** перехоплення шкоди (у NeoForge готова
   скасовувана подія, тут — один mixin у `hurtServer` + власна міні-шина), хуки поведінки
   предметів для запечатаних артефактів (`IItemExtension` проти mixin'ів у `Inventory.tick`
   / дроп / підбір), і стан Потойбічного на гравці (data attachments проти власного
   `PersistentState` + `ModChannels`). Після того, як інфраструктура написана, кожна
   наступна здібність коштує однаково на обох лоадерах.

**Дедлайну немає.** Це змінює підхід, а не темп: там, де під графік був би сенс
транслітерувати Java в Kotlin, тут правильніше переписати як слід. Шар здібностей
переписується ідіоматично; замість MythicMobs будується data-driven рушій істот.

## Поточний стан

- ✅ Фаза 00 — каркас; `core`: `valueobjects` 41/41 + `services` 3/4, 11 тестів зелені
- ✅ Фаза 01 — решта `core`: `brewing`(4), `contracts`(2), `market`(8), `spells`(3),
  `creatures`(12), `forage`(3), `rituals`(8) = 40 файлів, 21 тест-клас;
  усього 24 тест-класи / 178 тестів зелені. **`core` закритий**, механічний переклад скінчився.
- ✅ Фаза 02 — клієнтський каркас і мережа: `splitEnvironmentSourceSets`, другий entrypoint,
  пакетний шар (`net.ModChannels`), тестовий екран `BeyonderStatusScreen`
- ✅ Фаза 03 — хребет здібностей. `core`: `abilities`(4) + `entities`(3) + `AbilityTransformer`,
  `SanityPenaltyRoller`, `PathwayBranding`, `RgbColor`; `mod`: `Ability`-ієрархія,
  `AbilityContext` + 4 сервіси, `AbilityExecutor`, `PendingCast`, рантайм у `AbilityServices`.
  Разом 30 тест-класів / 231 тест зелені. Скіл `designing-pathway-abilities` переписано
  під нову форму — правило переїхало разом зі своєю механікою.
- ✅ Фаза 04 — час. `core`: `Beyonder.payUpkeep`; `mod`: `TickScheduler`,
  `ServerTickDispatcher`, `Session`/`SessionScope`/`sustain`, `Scheduling`, `SessionHost`,
  `PassiveTicker`, `AbilityContexts`; `AbilityContext` розділено на нього ж і `CastContext`.
  У `mod` з'явився власний тест-сорссет (гри не піднімає): 18 тестів на чергу тіків і
  семантику сесій. Разом 33 тест-класи / 255 тестів зелені.
- ▶ **Фаза 05** — перший вертикальний зріз до касту в грі (**головна віха**)

Порядок фаз — ланцюг залежностей, не список побажань. Не переносити більше трьох здібностей,
поки цикл «видав шлях → випив зілля → скастував» не замкнувся в грі.

## Карта знань плагіна

Плагін накопичив 19 правил (1 676 рядків), 10 проєктних док і 12 специфікацій. **Сюди вони
НЕ скопійовані навмисно** — і не тому, що застарілі. Виміряно: платформної специфіки в них
мало (`ability-theft.md` — 179 рядків і дві згадки Bukkit). Це майже чистий дизайн, і він
платформу переживає.

Причина інша: **правило про механіку, якої ще немає, — це правило ні про що.**
`ability-theft.md` описує `TheftLedger`, `theft.json` і терміни утримання вкраденої сили —
у моді не існує жодного з них (точка примусу, `AbilityExecutor`, з'явилась на фазі 03, але
сама крадіжка — ні). А `paths:`-фронтматер, що вантажить правила умовно, не збіжиться
з новою розкладкою пакетів: вони або висітимуть у контексті завжди, або не вантажитимуться ніколи.

**Правила портуються ПОФАЗНО, разом зі своєю механікою** — коли її форма в Kotlin уже відома.
Перенести правило — частина роботи над механікою, а не окремий етап.

Доти читай оригінали за потреби. Шлях: `C:\Users\yuryp\IdeaProjects\mysteries-above`.

| Треба зрозуміти | Читай у плагіні |
|---|---|
| загальну архітектуру, шари, шов «правила проти ефектів» | `CLAUDE.md` (великий, вичерпний) |
| як влаштована здібність, пайплайн виконання | ✅ перенесено: `.claude/skills/designing-pathway-abilities/api-reference.md` |
| сесії, їх життєвий цикл і чому реєстр не `static` | там само, п. «Sessions»; у плагіні — `.claude/rules/pathway-abilities.md` |
| візуальні ефекти: що є, як не дублювати | `.claude/rules/visual-effects-reuse.md`, `ability-visual-effects.md` |
| кольори шляхів | `.claude/rules/pathway-branding.md` + `domain/PathwayBranding.java` |
| істоти, теги, прикликання | `.claude/rules/summoned-creatures.md`, `docs/mythic-creatures.md` |
| крадіжка сил (Error 6/5) | `.claude/rules/ability-theft.md` — дизайн часу й точок примусу |
| душі, почет нежиті (Death) | `.claude/rules/lingering-souls.md`, `gatekeeper-door.md` |
| ритуали, форедж, ринок, контракти | `docs/ritual-magic.md`, `forage.md`, `market-gathering.md`, `contracts.md` |
| дизайн конкретних Послідовностей | `docs/superpowers/specs/*.md` і `plans/*.md` |
| матеріали предметів, чому музичні диски | `docs/item-materials.md` |

**Уже неактуальне — не переносити:**
`bettermodel-models.md` (BetterModel викреслено рішенням 1), `church-structures.md` +
`docs/church-organizations.md` + `docs/secret-orders.md` (рішення 4), `wiring.md`
(`ServiceContainer` — DI-патерн плагіна, у моді буде інше), `graph-tooling.md` + `graphify.md`
(про граф плагіна), `domain-purity.md` (тут це стереже компілятор, а не ArchUnit).

## Мова

- **User-facing текст — українською**: назви й описи здібностей, повідомлення гравцям, GUI,
  назви предметів і зілль, повідомлення команд.
- Англійською: ідентифікатори коду, назви класів/пакетів, ключі конфігів, логи,
  назви Sequence (канон з новели).
- Коментарі — українською або англійською, як у файлі поруч.

## Git

- **Ніколи не створюй git worktree.** Для ізольованої роботи — звичайна гілка
  (`git checkout -b feat/<опис>`) у тому самому робочому дереві.
- Повідомлення комітів — Conventional Commits, **тільки англійською**, наказовий спосіб,
  ≤ 72 символи в заголовку, без тире як роздільника. Один коміт = одна логічна зміна.
- Комітити чи пушити — лише коли користувач попросив.
- Довгі повідомлення передавай через `git commit -F <файл>`, а не here-string: через bash
  here-string у повідомленні лишаються літеральні `@`.
