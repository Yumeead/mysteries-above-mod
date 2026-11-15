# Журнал фаз і поточний стан

Читати за потреби: що вже зроблено, коли й що лишилось. Правила тут не живуть.


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
- ✅ Фаза 05 — вертикальний зріз. `mod`: `pathways` (Tyrant, Sun + 3 здібності), `item`
  (`ModItems`, `ModComponents`, `PotionBrew`, `BeyonderPotionItem`), `beyonder` (`Ascension`,
  `AbilityRequests`, `BeyonderStatus`), `MysteriesCommand`, `AbilitySelection`, клавіша касту
  й канал `CAST_REQUEST`, регенерація духовності, `JOIN` → активація пасивок.
  Разом 35 тест-класів / 266 тестів зелені.
- ✅ **Колесо Потойбічного, M1** — читальна модель і пакети на сервері. `mod`: три пакети
  (`AbilityRosterPayload`, `AbilityStatePayload`, `AbilityActionPayload`) + три канали,
  `BeyonderStatus.roster()/state()`, `AbilityStatePump`, `CooldownTable.readyAtTick`,
  `Ability.icon`. Разом 39 тест-класів / 293 тести зелені.
- ✅ **Колесо Потойбічного, M2** — клієнтське сховище стану. `mod/src/client`:
  `beyonder/ClientAbilityState` (кеш двох знімків, відлік кулдаунів, очищення на `DISCONNECT`),
  приймачі годують його замість лога, `BeyonderStatusScreen` друкує список
  здібностей із цінами й секундами, що спадають. Тестів не додалось — віха перевіряється оком.
- ✅ **Колесо Потойбічного, M3** — HUD і файл налаштувань. `mod/src/client`:
  `ClientOptions` (`config/mysteries_above_client.json`, Gson, режими HUD і мапа слотів),
  `render/UiTheme` (палітра + панель, волосок, бар, тінь перепочинку, бейдж),
  `hud/AbilityHud` через `HudElementRegistry.attachElementBefore(CHAT, …)`.
  Тестів не додалось — віха перевіряється оком.
- ✅ **Колесо Потойбічного, M4–M8, режими здібностей, Послідовності й фон** (15.10.2026) — розгорнуто в
  `.claude/rules/wheel-ui.md`. 49 тест-класів / 345 тестів зелені.
- ✅ Фаза 06 — `mod`: `event` (`GameEvent`, `IncomingDamage`, `GameEventBridge`),
  `ability/context/Events` + `Subscription` + `on<E>()`, `ability/runtime/EventHub`,
  **перший міксин** (`mixin/LivingEntityDamageMixin`, `mysteries_above.mixins.json`);
  `PermanentPassiveAbility` більше не зобов'язана цокати; смерть гасить сесії, респавн
  перезапускає пасивки; `IllusoryScales` переписано з опитування на подію.
  Разом 40 тест-класів / 303 тести зелені.
- ✅ Фаза 07 — чуттєвий словник. `mod`: `effect` (`ModParticles`, `ModSounds`; `BodyAura`
  переведено на власну порошинку), `client/render/SpiritMoteParticle`,
  `client/beyonder/SanityAmbience`, **другий міксин** (`mixin/client/GameRendererInvoker`,
  `mysteries_above.client.mixins.json`), `sanityLoss` у `AbilityStatePayload`,
  `/mysteries sanity`. Активи: перша власна текстура (`textures/particle/spirit_mote.png`),
  `sounds.json`, `shaders/post/sanity.fsh`, два `post_effect/*.json`.
  Разом 40 тест-класів / 304 тести зелені.
- ✅ **Фаза 08** — шар здібностей, усі 9 шляхів. **Закрито 9 з 9** (Death — 15.10.2026, у грі ще не перевірено).
  - **WhiteTower** (Посл. 9–5): `pathways/common` (`RitualMagic`, `CombatProficiency`,
    `PhysicalEnhancement`), `pathways/whitetower` (`EnhancedMind`, `Deduction`, `Agility`,
    `CrimeReconstruction`, `Analysis`, `Spellcasting`, `MirrorCurse`), `ability/AttributeGrant`.
  - **Sun** (Посл. 9–5, 16 здібностей): `abilities/HolyLight.kt` (спільний шар шляху),
    `Singing`, `Sunshine`, `Blessing`, `EvilDetection`, `Daytime`, `NightVision`,
    `HolyLightSummoning`, `SunHalo`, `CleaveOfPurification`, `SunHolyWater`, `HolyOath`,
    `FireOfLight`, `Notarization`, `Contract`, `HolyWave`.
  - **Door** (Посл. 9–5, 14 здібностей): `abilities/Doorway.kt` (спільний шар шляху),
    `DoorOpening`, `Flash`, `EscapeTrick`, `Burning`, `ElectricShock`, `DivinationArts`,
    `SpiritualVision`, `SpiritualIntuition`, `AntiDivination`, `Record`, `DecryptPatterns`,
    `TravellersDoor`, `Blink`. Разом із ним у мод приїхали **друга подія шини**
    (`AbilityCast`) і **запит стану тогла** (`Beyonders.isToggled`), а з другою редакцією
    `TravellersDoor` — **перший власний вимір**: `mysteries_above:spirit_world`
    (`data/mysteries_above/` + `mysteries/world/SpiritWorld.kt`).
  - **Error** (Посл. 9–5, 5 Послідовностей — Marauder, Swindler, Cryptologist, Prometheus,
    Dream Stealer): `ShadowTheft`, `SuperiorObservation`, `SwindlerCharm`, `Eloquence`,
    `ThoughtMisdirection`, `MentalDisruption`, `MaterialTheft`, `Decryption`, `AttackTheft`,
    `DreamTheft`, `ThoughtUsurpation`, `MentalFortitude`, `IdealTheft`, `HeartTheft`,
    `TheftOfHeavenlySecrets`, `PowerTheft`, `AbilityTheft` (`AttackTheft`, `DreamTheft`,
    `ThoughtUsurpation`, `IdealTheft`, `HeartTheft`, `AbilityTheft` ділять одну ідентичність
    через `abilities/ConceptualTheft.kt`, спільний шар шляху). Приїхали **реєстр крадіжки**
    (`beyonder/TheftRegistry`) і спільний **вівтар** (`Altar`) для обрядової магії.
  - **Visionary** (Посл. 9–5, 16 здібностей): `GoodMemory`, `ScanGaze`, `SharpVision`,
    `DangerSense`, `IntentReader`, `SurgeOfInsanity`, `Appeasement`, `Telepathy`,
    `ScanGazePassive`, `PsychicCue`, `PsychologicalInvisibility`, `BattleHypnotism`,
    `DragonScale`, `Alteration`, `Guidance`, `DreamTraversal`. Приїхали **сеам вибору без
    меню до фази 11** (`ability/runtime/PendingChoices` + `/mysteries choose`, для
    `PsychicCue` і `Alteration`) і **видимість для одного глядача**
    (`ability/SelectiveVisibility`, пакети `AddEntity`/`SetEntityData`/`SetEquipment` для
    `BattleHypnotism` — перше, для чого публічної ванільної невидимості не вистачило).
    Кілька механік плагіна свідомо звужено, а не перенесено 1:1: `DreamTraversal` замість
    меню з кількох сплячих завжди будить найближчого; `Alteration` з дев'яти тригерів лишила
    два, які справді ловить шина подій мода; навігаційний апгрейд `PsychicCue` (Посл. 6+)
    чекає на примітив прицілювання по блоку, якого ще немає.
  - **Tyrant** (Посл. 9–5, 29 здібностей): `Balance`/`AirCushion` (те саме тіло — параметризований
    `Balance`, не підклас, бо спільна ідентичність мусить нести спільний клас), `SeaLunge`,
    `Wrath`, `RagingBlow`, `SeaMemory`, `PreciseThrow`, `WaterFilm`, `AzureWave`, `RestorativeWater`,
    `CleansingLight`, `Windblades`, `PressureWave`, `WindFlight`, `WindSprint`, `WindImbuedHands`,
    `WindPull`, `Eavesdrop`, `WaterControl`, `StormHeart`, `AbyssalDominion`, `LightningStrike`,
    `LightningBoltArrow`, `ThunderVoice`, `Singing`, `SeaTongue`, `WaterCurtain`, `CorrosiveDeluge`,
    `SlickWater`, `WindBinding`. `IllusoryScales` — фаза 06. Костилі, замінені справжньою механікою:
    `SeaMemory` (десять іменованих міток у меню → лодестон-компас без лодестона,
    `LodestoneTracker(tracked = false)`; кілька міток — кілька компасів), `WindFlight`
    (ручний цикл швидкості → `abilities.mayfly` для Польоту, `SLOW_FALLING` для Ковзання),
    `AirCushion` (формула падіння вручну → `FALL_DAMAGE_MULTIPLIER`, той самий прийом, що
    в `Agility` фази 08 WhiteTower). Два костилі лишились нездоланними в цій фазі, а не
    перенесеними: `LightningBoltArrow` («заряди наступні 5 пострілів» через
    `EntityShootBowEvent`, якого нема в Fabric API — стала пронизливою блискавкою по лінії
    погляду) і `ThunderVoice` (вибір адресата через `PendingChoices`, як у `PsychicCue`;
    вільний рядок у чат за 30 с — не переніс: захоплення чат-вводу належить `ui`, фазі 11).
  Разом 43 тест-класи / 322 тести зелені (Tyrant рахунок не змінює: нових тест-класів не додано,
  дві застарілі перевірки фази 05 виправлено під повний ростер).
  - **Justiciar** (Посл. 9–5, 13 нових здібностей + перевикористаний `AntiDivination` Дверей):
    `Authority`, `ArbitersGaze`, `AreaOfJurisdiction`, `Recognition`, `Intuition`, `WhipOfPain`,
    `PsychicPiercing`, `BrandOfRestraint`, `PsychicLashing`, `Verdict`, `PowerProhibition`,
    `SpawnProhibition`, `Punishment`. Приїхав спільний примітив `pathways/justiciar/Zone.kt`
    (коло, що знає, хто всередині, і само малює межу — заміна трьох ручних зон плагіна:
    `ProhibitionZone`, `SpawnBanZone`, `JurisdictionSession`) та `Restraint.kt`
    (`rootInPlace` — спільний якір-цикл замість двох копій у `BrandOfRestraint` і
    `Punishment`). Шина подій отримала третій тип, і вперше **не** від мода/міксину, а від
    самої здібності: `ProhibitionViolated` (`context.events.publish` — новий метод в `Events`,
    `PowerProhibition` публікує, `Punishment` підписується) замінює плагінний прямий виклик
    `Punishment.registerViolation` через приватну статичну мапу.
    Костилі, замінені справжньою механікою: **чотири** мод-цикли sneak+ПКМ (`Verdict`,
    `PowerProhibition`, `SpawnProhibition`, старий `Punishment`) → інструмент у руці, той самий
    прийом, що вже витягнув Двері з `DivinationArts` (перо/ґрати/ланцюг/чахлик/тризуб для
    `Verdict`; тотем/елітри/меч для `PowerProhibition`; яйця-спавнери для `SpawnProhibition`;
    друга рука для `Punishment`, бо перша вже зайнята вибором зони); еталонний евристичний
    список «хто мирний» в `Authority` (нейметег/житель/голем/кінь/приручене/тварина) →
    ванільний маркер `Enemy`; емуляція Тотема Безсмертя в `PowerProhibition.KILL_BAN`
    (`playEffect` + ручне встановлення HP) → справжнє скасування шкоди через
    `IncomingDamage.cancel()`. `PowerProhibition` звужено з шести режимів до трьох
    (`KILL_BAN`, `FLIGHT_BAN`, `WEAPON_BAN`): заборона телепортації, вхід у чужий домен і
    повний лок блоків не мають готового гачка у Fabric API і вимагали б окремого міксину
    заради однієї утилітарної здібності Посл. 6 — той самий розрахунок, що звузив
    `LightningBoltArrow` і `ThunderVoice` у Tyrant. `SpawnProhibition` лишився поллінгом
    (двічі на секунду виганяє заборонений вид із зони), бо `CreatureSpawnEvent` теж не має
    аналога на Fabric — чесний компроміс, не костиль: зона **виганяє**, а не **скасовує**.
  - **Fool** (Посл. 9–5, шлях закрито): `DivinationArts`, `SeerSpiritVision`, `DangerIntuition`
    (Посл. 9); `PaperCutter`, `ExpressionControl`, `ClownAgility` (Посл. 8); `FlameJump`,
    `PaperSubstitution`, `AirBullet`, `DamageTransfer`, `AquaticBreath`, `IllusionCreation`
    (Посл. 7, плюс `Burning` Дверей і `RitualMagic` даром); `Shapeshifting` (Посл. 6). Приїхали
    `PaperThrowTrigger` (`UseItemCallback` замість касту), `RecentDamageLedger` і `PaperWeaponHits`
    (обидва слухають `IncomingDamage` із самої точки `AbilityServices.publish`, бо залежать від
    того, кого вдарили й що в руці, а не від власника здібності), компонент `PaperWeapon` і
    **перший access widener мода** — заради `Disguise` (див. нижче).
    **Дві еволюції плагіна злились у свій базовий клас**, бо спільна ідентичність мусить нести
    спільний клас: `PaperWeaponry` → `PaperCutter` (плетіння зброї просто «ще не відкрито» вище
    Посл. 7), `GracefulDescent` → `ClownAgility` (крок швидкості й слід падіння читають
    Послідовність). Побічно виявлено: `PassiveTicker.activate` **не** кличе `onActivate` для вже
    активованої пасивки, тож піднесення 8→6 лишало старий модифікатор атрибута до перезаходу —
    `ClownAgility` тепер звіряє живий модифікатор у такті й переставляє лише на розбіжність.
    Викреслено як дублікати: лозошукання й сонне провидіння `DivinationArts` (обидва вже є в
    Дверей і в `TravellersDoor`).
    **Посл. 5 (Маріонетник)** — найбільша окрема здібність порту (1 828 LOC у плагіні):
    `ThreadSight`, `MarionetteControl`, `Possession` плюс `pathways/fool/Marionettes.kt` (усе, що
    не здібність), **перша власна сутність мода** (`entity/MarionetteEntity`, `ModEntities`) з
    **першим рендерером** (`client/render/MarionetteRenderer`), другий предмет
    (`ModItems.CHARACTERISTIC` + компонент), два канали (`MARIONETTE_STATE` / `MARIONETTE_ORDER`),
    ряд ниток у HUD (`client/hud/MarionetteHud`) і клавіша «Нитки». Розгорнуто — `.claude/rules/fool-pathway.md`.
  - **Death** (Посл. 9–5, 20 здібностей): `UndeadKnowledge`, `CadavericResilience`, `GloomyPresence`,
    `DeathSight`, `SpiritVision` (9); `SpiritCommunication`, `EyeOfDeath` (8); `SpiritPerception`,
    `VoiceOfTheDead`, `SpiritChanneling`, `CorpseGuise` + `RitualMagic` (7); `SpiritWorldKnowledge`,
    `LanguageOfTheDead`, `Resurrection`, `SpiritPact` (6); `GatekeeperStep`, `DoorToTheUnderworld`,
    `InternalUnderworld` (5). Приїхали **другий власний вимір** (`mysteries_above:underworld`, датапак +
    `pathways/death/Underworld.kt`), **журнал смертей** (`DeathTraces`), **почет** (`Retinue`), `Spirits`,
    `payBranch`, `common/SoulWard` (читає й `MarionetteControl`) і третій екранний ланцюг
    (`post_effect/death_sight.json` + `shaders/post/death_sight.fsh`). Розгорнуто — `.claude/rules/death-pathway.md`.
  Разом 43 тест-класи / 322 тести зелені (нових тест-класів не додано; ростер Смерті перевіряє `ModPathwaysTest`).
  - **13 заглушок** (15.10.2026): `pathways/StubPathways.kt` — по рядку на шлях (ім'я, група, назви
    Послідовностей із плагінного `PathwayManager`), без здібностей. Зілля, Характеристика й колір уже
    працювали для будь-якого імені, тож `ModPathways` тепер знає всі 22, а `Ascension` каже гравцеві, що
    сили шляху «ще не прокинулись». Межа: засвоєння росте лише з кастів, тож на заглушці гравець без
    `/mysteries mastery` лишається на Посл. 9 — як і в плагіні; обхід навмисно не вигадано.
- 🔄 **Фаза 09** — персистентність (почато 15.10.2026): Потойбічний, маріонетки, крадіжки, паперові ляльки й Клятва Сонця
  лежать в attachment'ах і чанках, а не в окремих файлах. Розгорнуто — `.claude/rules/state-commands-choices.md`.
  Разом 46 тест-класів / 332 тести зелені (з M4 колеса). У грі ще не перевірено.
- ✅ **Фаза 10** — команди (16.10.2026): `/mysteries …` з необов'язковим `[targets]`. Розгорнуто — там само.
- ✅ **Фаза 11** — екрани (16.10.2026): єдиний екран `ChoiceScreen` для вибору посеред касту. Розгорнуто — там само.
  Разом 50 тест-класів / 348 тестів зелені. Перевірено в грі на двох клієнтах.
- ✅ **Фаза 12** — рушій істот замість MythicMobs (19.10.2026, гілка `feat/phase-12-creature-engine`). Три
  датапак-реєстри, розум на ванільних тілах і власна сутність-людина; розгорнуто — `.claude/rules/creature-engine.md`.
  Контент: увесь пак плагіна переїхав конвертером (64 істоти, 49 кітів, 63 прийоми — снаряди тепер видно в
  польоті), плюс нове — городянин, варта, дикий Потойбічний (хижий, ветеран, той, що втрачає контроль) і бос
  «Ліндон Ворт, Деспот Моря» з трьома актами й телеграфованими ударами. Спавн — ambient біля Потойбічних і
  «скриню відчинено» з гейтами відстані й апекса плагіна; `/mysteries creature spawn <id> [к-сть]`. Відкладене
  з фази 08 закрито: «Домовленість з духами» домовляється з істотами Світу Духів за їхніми цінами, а
  «Внутрішній Загробний Світ» читає окупанта за його природою й випускає ту саму істоту. Частину фази 13
  поглинуто: люди-NPC уже є, священики чекають на церкви. Разом 65 тест-класів / 417 юніт-тестів + 14 ігрових
  тестів зелені; клієнтський ігровий тест піднімає справжній клієнт і знімає три сцени.
- ✅ **Фаза 13** — NPC замість Citizens, **поглинута** (20.10.2026, нуль рядків коду). Звірено всі 22 файли плагіна,
  що згадують Citizens чи NPC: маріонетки й Смерть — `MarionetteEntity` (фази 08–09), городяни й варта — `HumanEntity`
  (фаза 12), `CitizensNameplateGuard` латав баг самого Citizens і зник разом із ним. Лишилось два NPC, і обидва — частина
  механік, яких у моді ще немає: **Посередник** (`OrganizerNpcService`) приїде з підпільним ринком (Зборами),
  **священики** (`ChurchPriestService`, `OrderListener`) — з новими церквами (рішення 4). Правило те саме, що у фазах
  10 і 11: NPC приїжджає разом зі своєю механікою. Тіло обом уже є — запис `creature` з `mysteries_above:human`.
- ✅ **Фаза 14** — ассети й моделі (20.10.2026, гілка `feat/phase-14-assets`). Ресурспак і датапак плагіна більше
  не роздаються окремо — вони всередині мода; розгорнуто — `.claude/rules/assets-and-loot.md`. **Структури:** 24 світові споруди
  (бібліотеки, руїни, будинки, морське містечко) — дані як є, під `data/mysteries_above/`. **Інгредієнти:** 163
  справжні предмети (`item/Ingredients.kt`) з текстурами 16×16, назвами й кольорами плагіна, у вкладці «Інгредієнти»;
  Характеристика дістала власне сіре ядро під фарбу шляху. **Моделі:** чотири BetterModel-істоти (кондор, сирена,
  примара, мисливець) малює власний рендерер, що читає `.bbmodel` як є, з анімаціями й кастами за прийомом.
  `BbModelTest`; клієнтський ігровий тест знімає ще три кадри з моделями й предметами в руках. Разом 66 тест-класів /
  419 юніт-тестів + 14 ігрових зелені.
- ✅ **Фаза 15** — лут і числа світу (20.10.2026, гілка `feat/phase-15-loot`). Лут скринь будується з каталогу інгредієнтів
  і зважує ще три речі: місце (корабель — до Тирана), Конвергенцію (свій шлях утричі) і досяжність за Послідовністю;
  Удача піднімає рідкісне. Лут `creatures.yml` — 54 ванільні таблиці `creature/<id>`, зілля з луту добудовується.
  Числа спавну з `config.yml` — п'ять ігрових правил. Розгорнуто — `.claude/rules/assets-and-loot.md`. `LootReachTest`,
  `LootDataTest`, `LootGameTests`. Разом 68 тест-класів / 423 юніт-тести + 19 ігрових зелені.
- 🔄 **Фаза 16** — перетест і катовер (почато 21.10.2026, гілка `feat/phase-16-retest`). **Автоматична половина закрита:**
  `AbilityGameTests` — по ігровому тесту на кожен із дев'яти шляхів і один на 13 заглушок. Кожен шлях іде Посл. 9 → 5;
  на кожному кроці кастер дістає шлях, збирається ростер (назви, описи, режими), кожна активна сила кастується в кожному
  режимі з повним баком — двічі: з нежиттю під поглядом і з гравцем-Потойбічним; кожен тогл вмикається, сесії цокають
  2,5 с. Перед кожним кастом сцена та сама: кастер на місці, вівтар зі свічками, ставок, папір, і повтор із далекоглядом,
  компасом і стрижнем у руці, якщо порожня рука відмовила. Тест падає на тому, що рантайм у грі ковтає: кожен ізольований
  `catch` закінчується `log.error` на логері мода, і апендер log4j бачить їх усі; збій приписується шляху за кадром стека.
  Спрацювало 118 зі 144 «сила × режим»; решта чекають багатокрокових умов (сплячий гравець, маріонетки, слуги, історія
  шкоди) і перевіряються грою. **Перший прогін упіймав справжній баг:** `PowerTheft` Крадія снів падав
  `IllegalArgumentException` уже **після** `grantOffPathway` — сила лишалась у злодія без запису; а `TheftRegistry.sweep`
  прибирав запис із закінченням вікна жертви, навіть коли невикористаний резерв ще тримав силу. Обидва виправлено в
  реєстрі (`TheftRegistryTest.theReserveOutwaitsTheVictimsWindow`). **Лишилось:** перше повне проходження в грі на двох
  клієнтах і рішення про катовер (версія, реліз). Разом 68 тест-класів / 424 юніт-тести + 29 ігрових зелені.
- ✅ **Імерсивні здібності, віха A** (22.10.2026, гілка `feat/immersive-abilities`) — набір, спільний для всіх
  шляхів; розгорнуто — `.claude/rules/world-effects.md`. Відмова стала зривом у руці (іскри гаснуть, флакон
  труситься) і підказкою під прицілом лише з перемикачем «Підказки»; опір — оболонка кольору шляху цілі;
  перша частинка, що світить (`SPIRIT_LIGHT`, адитивна, ореол сходинками); у `Effects` — `beam`, `flare`
  (справжнє світло), `flash`. Тексти успіху поки в action bar — їх прибирає перехід кожного шляху, першим іде
  Сонце (віха B). Bloom — віха C. `LightClientGameTest` знімає все в справжньому клієнті. Разом 68 тест-класів /
  427 юніт-тестів + 29 ігрових + 2 клієнтські ігрові зелені.
- ✅ **Імерсивні здібності, віха B — Сонце** (23.10.2026, гілка `feat/immersive-abilities`, ще не закомічено) — усі 14
  власних сил шляху говорять світом; розгорнуто — «Світ замість чату» → «Віха B». До набору додались гліф світла
  (`SPIRIT_LIGHT_GLYPH`), `burst`, `wave` + `waveArrival`, `worldText` і межа розміру світла біля камери; Нічне бачення —
  перший шар рендеру на гравці (`SunEyesLayer`). `SunClientGameTest` знімає 9 кадрів. Лишилось: гра на двох клієнтах
  (Засвідчення, чужі очі). Разом 69 тест-класів / 429 юніт-тестів + 29 ігрових + 3 клієнтські ігрові зелені.
- 🔄 **Зріз Дверей — рушій явищ** (14.11.2026, гілка `feat/door-slice`, ще не закомічено) — двері одним пакетом,
  стулки зі стіни, коридор у власному шейдері, шов Миттєвого кроку; розгорнуто — «Двері одним пакетом». Відкриття
  дверей, Двері Мандрівника й Миттєвий крок говорять світом. Лишилось: гра на двох клієнтах, Iris, потім заломлення.
  Миттєвий крок — ривок тунелем арок між двома дверима (друга редакція). `DoorClientGameTest` — 9 кадрів. Разом
  4 клієнтські ігрові тести зелені.
- ✅ **Дурень і колесо: план переробки** (14.11.2026, гілка `feat/fool-wheel-rework`; план — артефакт «Дурень і колесо: план
  переробки», https://claude.ai/artifact/4RLkvr5wxXZgEAK5xkRzKP). Сім кроків: колесо R ходить і закріплюється (4 с); копія
  кольору кадру й заломлення (OpenGL + Vulkan, `-Pvulkan`); Повітряна куля летить і стіна її зупиняє; Інтуїція небезпеки
  прогнозує снаряд і показує стрілки біля прицілу; друге коло «Нитки» в колесі (10 дій після 15.11); реєстр облич, галерея й виправлений
  скін маски; ворожіння монетою, маятником і сном. Клієнтські тести: `Wheel`, `AirBullet`, `Danger`, `Threads`, `Faces`,
  `Divination`. Лишилось: Iris для кулі, гра на двох клієнтах (маріонетка з гравця в колесі, маска очима іншого, віддача
  ворожіння цілі).
- 🔄 **Дурень — папір і нитки** (14.11.2026, гілка `feat/fool-immersive`, ще не закомічено) — усі 16 власних сил
  говорять світом; розгорнуто — «Папір і нитки». Лишилось: гра на двох клієнтах (маріонетка з гравця й клубок,
  Перевтілення, чужі нитки). `FoolClientGameTest` — 13 кадрів. Разом 431 юніт-тест + 5 клієнтських ігрових зелені.


### Вертикальний зріз (фаза 05)

Цикл замкнувся: **видав шлях → випив зілля → скастував**.

| Ланка | Де |
|---|---|
| зілля як предмет | `item.ModItems.BEYONDER_POTION` + компонент `PotionBrew` |
| «випив» | `item.BeyonderPotionItem` → `beyonder.Ascension` |
| «скастував» | `/mysteries cast`, клавіша **R** → `beyonder.AbilityRequests` → `AbilityExecutor` |
| «видно» | `beyonder.BeyonderStatus.pushRoster` / `statePump` після кожної зміни |

```
/mysteries potion Tyrant 9     # видати зілля (оператор)
/mysteries abilities           # список із кулдаунами; ▶ — обране
/mysteries select <identity>   # під клавішу R
/mysteries cast <identity>
/mysteries mastery 100         # добити засвоєння (оператор)
/mysteries forget              # зняти статус Потойбічного (оператор)
```

**Одне зілля на всі 220 варіантів.** Що налито, каже компонент `PotionBrew` (ім'я шляху +
номер Послідовності), а не окремий зареєстрований предмет. Колір рідини йде з
`PathwayBranding` через ванільний `POTION_CONTENTS`, а модель — ванільна ж пляшка з тінтом,
тож **мод не несе жодної власної текстури**. Шлях їде **іменем**: імена стабільні й читаються
в `/data get`, а порядок шляхів у реєстрі змінюється щофази.

**Перенесено два шляхи, не двадцять два.** Здібностей у фазі рівно три (`IllusoryScales`,
`RestorativeWater` — Tyrant; `ResistanceToHorror` — Sun), і більше шляхів було б нічим
наповнити. `ModPathways.byName` повертає `null` для решти — зілля може існувати раніше
за свій шлях, і це не помилка.

**Вибір здібності живе на сервері.** Пакет касту без ідентичності навмисно: клієнт не знає
ні списку здібностей, ні того, чи вони ще є, тож ідентичність у пакеті довелося б звіряти
тим самим пошуком, що й порожній. Клавіша лише натискає спуск; вибір тримає
`AbilitySelection` (до фази 11 — увесь вибір, що є).

**Духовність починається з нуля** й відновлюється раз на секунду
(`SpiritualityCalculator.REGEN_RATES` — 1/с на Посл. 9). Свіжий Потойбічний чекає перед
першим кастом — це баланс плагіна, не недогляд.

**Тести мода можуть підняти реєстри.** `ModPathwaysTest` і `PotionBrewTest` кличуть
`SharedConstants.tryDetectVersion()` + `Bootstrap.bootStrap()` у `@BeforeAll`: константа виду
`MobEffects.NAUSEA` у полі здібності інакше падає `ExceptionInInitializerError`. Це реєстри, а
не світ — секунда на весь клас; розкладати константи по лінивих обгортках заради тесту не варто.

Порядок фаз — ланцюг залежностей, не список побажань. Не переносити більше трьох здібностей,
поки цикл «видав шлях → випив зілля → скастував» не замкнувся в грі.


