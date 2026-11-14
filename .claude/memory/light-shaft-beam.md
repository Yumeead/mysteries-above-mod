---
name: light-shaft-beam
description: "The solid light-shaft beam (custom shader, not particles): how it is built, the sun-angled sky beams, the Iris fallback, tuning knobs, what is still open, how to verify"
metadata:
  node_type: memory
  type: project
  originSessionId: 58eae855-6b37-499a-9634-dbdf537553e6
  modified: 2026-10-25T21:57:43.597Z
---

**Стан на 25.10.2026.** Промінь (`effects.beam`) — суцільний стовп світла у власному шейдері. Гілка
`feat/immersive-abilities`, **не закомічено** (разом із віхою B, див. [[immersive-sun-pilot-next]]). 25.10 закрито всі
вісім вад першої редакції. Розкладку описано в `CLAUDE.md` → «Світ замість чату» → таблиця вад. Додатково зроблено:
- промінь з неба йде від сонця;
- поява плавна;
- є запасний шлях під Iris.

**Чого хоче користувач:** «настоящий промінь», сонце крізь хмари, жовтий туман стовпом. Не маяк і не частинки. Свій
шейдер — схвалений шлях (виняток із [[ability-visuals-pixel-and-on-theme]]). 25.10 додав: промінь «аж від самого
сонця», кут — за часом доби, поява плавніша.

**Шлях даних.**
- Сервер: `effect/Light.kt` `beam(scheduling, …)` шле пакет `WORLD_EFFECT` `Beam` і ставить `flare` уздовж
  останніх 16 бл.
- Клієнт: `client/render/WorldEffects` малює через `COLLECT_SUBMITS` → `LightShaft.draw`, шейдер
  `shaders/core/light_shaft.*`.
- Копія глибини: `AFTER_OPAQUE_TERRAIN` → `LightShaft.captureDepth()` → `SceneDepth` (AbstractTexture у
  TextureManager), шейдер читає її як `Sampler0`.
- Формат вершин `ENTITY`:
  - UV0 — поперек і вздовж;
  - UV1 — вік у 1/16 тіку та м'якість фронту;
  - UV2 — довжина й радіус;
  - нормаль — вісь.

**Сонце.** `sunward/lightReaches/SUN_REACH` лежать у `pathways/sun/abilities/HolyLight.kt`. Напрям —
`(−sin a, cos a, 0)`, де a — `SUN_ANGLE` у радіанах (так ставить диск `SkyRenderer`). Якщо сонце нижче sin = 0.26 —
промінь іде вертикально. Святе світло: тінь стіни чи дерева ховає ціль. Шкода — у похилому циліндрі після
`BEAM_REACH_TICKS` (3). 26.10 користувач попросив «жирніше й прозоріше, радіус як у сонця». Тепер стовп — конус:
біля сонця `SUN_WIDTH` (0.055 × 240 ≈ 13), унизу `PILLAR_WIDTH` 2 (Святе світло) / 1.4 (Клятва). Широкий стовп рідший
(`DENSE_RADIUS` 2, `MIN_DENSITY` 0.3).

**Ручки.**
- Шейдер:
  - `CORE` 0.16;
  - `HAZE_GLOW` / `CORE_GLOW` 0.42;
  - `VEIL` 0.45, `CORE_VEIL` 0.5, `MAX_VEIL` 0.92;
  - `WHITE` 0.22, `SATURATION` 1.6;
  - `SOURCE_RADII` 3, `FULL_LENGTH` 48, `HIGH_SHARE` 0.4;
  - `MAX_PATH` 2.5, `NEAR` 0.3–1.0;
  - `SOFT_RADII` 0.45 (0.8 з'їдало вдень кілька блоків над землею);
  - `DENSE_RADIUS` 2, `MIN_DENSITY` 0.3;
  - пилинки: `MOTE_*`.
- Kotlin:
  - `SEGMENT` 3, `SOURCE_WIDTH` 0.25, `PLAIN_ALPHA` 0.55;
  - `APPEAR_TICKS` 5, `THIN` 0.55, `FRONT_SHARE` 1.2;
  - `BEAM_FADE_TICKS` 10.

Урок 25.10: вуаль серпанку більша за його світло фарбує край неба в сіре.

**Iris.** Iris 1.11.4+26.2 і Sodium 0.9.2 існують. Під Complementary r5.9.3 будь-який конвеєр мода без призначеної
програми не малюється зовсім. Тому:
- `ShaderPacks` (рефлексія) віддає `SPIRIT_LIGHT` → `PARTICLES_TRANSLUCENT`;
- стовп під паком = `RenderTypes.lightning()` + `drawPlain`.

Під Complementary стовп виходить блідим: пак фарбує блискавку по-своєму. Можливе покращення — `beaconBeam`-тип,
бо його колір паки поважають.

**Відкрите.**
1. Камера всередині стовпа на самій осі: Свята клятва опівдні з першої особи. Смуга вироджується, видно лише
   світло довкола.
2. Під шейдерпаком стовп блідий.
3. Не перевірено на двох живих клієнтах.
4. У першій особі гліфи, що їдуть на кастері (кулаки Клятви, іскри Очищення), заливають кадр великими пікселями.
   Помічено в кадрах, промінь ні до чого.
5. Ігровий тест `creature_game_tests_a_ranged_caster_keeps_its_distance` нестабільний: упав раз зі трьох.

**Як перевіряти.**
- `./gradlew :mod:runClientGameTest`. Кадри потрібні такі:
  - `light-beams-night`, `light-beams-day` — плюс промінь уздовж погляду;
  - `light-beams-day-fabulous`;
  - `sun-holy-light-morning`, `sun-holy-light-into-the-sun`;
  - `sun-sunshine-day`, `sun-holy-oath-morning`.
- Кадри лягають у `mod/build/run/clientGameTest/screenshots`. Каталог стирається перед кожним прогоном, тож
  кадри копіювати одразу.
- PIL — без `python -I`.
- Iris — тимчасово:
  - у `mod/build.gradle.kts` додати `repositories { maven("https://api.modrinth.com/maven") }`;
  - `runtimeOnly("maven.modrinth:sodium:mc26.2-0.9.2-fabric")` і `…iris:1.11.4+26.2-fabric`;
  - `clearRunDirectory = false` у `configureTests`;
  - пак і `config/iris.properties` (`enableShaders=true`, `shaderPack=…zip`) покласти в run-каталог;
  - після перевірки все відкотити.

**Коміти, коли попросять:** спершу віха B, потім стовп. `Light.kt`, `Effects.kt`, access widener, `CLAUDE.md`,
`api-reference.md` і Sun-сили (`HolyLightSummoning`, `HolyOath`, `Sunshine`, `HolyLight.kt`) зачіпають обидві зміни.
Їх розділяти патчами (`git diff > x.patch`, `git apply --cached`). Лише стовпу належать:
- `WorldEffects.kt`, `LightShaft.kt`, `ShaderPacks.kt`;
- `shaders/core/`;
- `WorldEffectPayload` (обрізка) і його тест.
