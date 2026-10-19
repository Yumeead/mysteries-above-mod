# Ескіз 03 — цілісна нижня панель

**[Відкрити автономний перегляд](review.html)** · [Зміни й перевірки](CHANGES.md) · [Геометрія](layout-spec.json) · [Результати перевірок](verification.json)

Перероблено кам’яний корпус, гнізда слотів, праві трубки, здоров’я та голод. Куля, обидві оправи, брелки й рідина збережені з ескізу 02; прибрано лише дозволений нечіткий знак Дурня на склі. Код, ресурси й налаштування мода не змінено.

## Почати перегляд

| Що | Аркуш |
|---|---|
| Панель ×4 | [00-panel-x4.png](00-panel-x4.png) |
| Новий камінь і сегментовані смуги | [01-stone-kit-x4.png](01-stone-kit-x4.png) |
| Гнізда, порожня здібність, кулдаун | [02-sockets-and-ability-x4.png](02-sockets-and-ability-x4.png) |
| Затверджені оправи й перевірка без кольору | [03-approved-orbs-x4.png](03-approved-orbs-x4.png) |
| Вузьке вікно ×3 / примусовий ×4 | [04-narrow-1280x720-gs3-forced4.png](04-narrow-1280x720-gs3-forced4.png) |
| Приклад підказки символу | [05-auxiliary-tooltip-x4.png](05-auxiliary-tooltip-x4.png) |
| B: повні назви символів | [Scale 2](screen-B-gs2.png) · [Scale 3](screen-B-gs3.png) |
| Редактор під цю композицію | [Scale 2](screen-editor-gs2.png) · [Scale 3](screen-editor-gs3.png) |

У перегляді можна змінювати стан, тло, масштаб, оправу, шлях і руку; наводити на праві символи та приховувати offhand/індикатор атаки. Повний кадр і збільшення не стискаються до ширини сторінки. Для точної піксельної оцінки використовуйте 100% масштаб браузера й горизонтальне прокручування.

## Усі стани разом, ×4 та GUI Scale 2/3

| Тло | ×4 від GUI-пікселя | GUI Scale 2 | GUI Scale 3 |
|---|---|---|---|
| Ваш скрін | [×4](states-provided-x4.png) | [2](states-provided-gs2.png) | [3](states-provided-gs3.png) |
| Небо | [×4](states-sky-x4.png) | [2](states-sky-gs2.png) | [3](states-sky-gs3.png) |
| Печера | [×4](states-cave-x4.png) | [2](states-cave-gs2.png) | [3](states-cave-gs3.png) |
| Сніг | [×4](states-snow-x4.png) | [2](states-snow-gs2.png) | [3](states-snow-gs3.png) |

Аркуші станів — компактні смуги для порівняння однієї повної композиції; усі компоненти мають точний множник 2/3/4. Повні кадри нижче враховують реальну ширину джерела та перехід до вузького корпусу.

## Кожен стан повним кадром, без зменшення

| Стан | Ваш скрін, 2 / 3 | Небо, 2 / 3 | Печера, 2 / 3 | Сніг, 2 / 3 |
|---|---|---|---|---|
| Усе повне | [2](panel-provided-full-gs2.png) · [3](panel-provided-full-gs3.png) | [2](panel-sky-full-gs2.png) · [3](panel-sky-full-gs3.png) | [2](panel-cave-full-gs2.png) · [3](panel-cave-full-gs3.png) | [2](panel-snow-full-gs2.png) · [3](panel-snow-full-gs3.png) |
| Малий запас | [2](panel-provided-low-spirit-gs2.png) · [3](panel-provided-low-spirit-gs3.png) | [2](panel-sky-low-spirit-gs2.png) · [3](panel-sky-low-spirit-gs3.png) | [2](panel-cave-low-spirit-gs2.png) · [3](panel-cave-low-spirit-gs3.png) | [2](panel-snow-low-spirit-gs2.png) · [3](panel-snow-low-spirit-gs3.png) |
| Низьке здоров’я | [2](panel-provided-low-health-gs2.png) · [3](panel-provided-low-health-gs3.png) | [2](panel-sky-low-health-gs2.png) · [3](panel-sky-low-health-gs3.png) | [2](panel-cave-low-health-gs2.png) · [3](panel-cave-low-health-gs3.png) | [2](panel-snow-low-health-gs2.png) · [3](panel-snow-low-health-gs3.png) |
| Голод | [2](panel-provided-hungry-gs2.png) · [3](panel-provided-hungry-gs3.png) | [2](panel-sky-hungry-gs2.png) · [3](panel-sky-hungry-gs3.png) | [2](panel-cave-hungry-gs2.png) · [3](panel-cave-hungry-gs3.png) | [2](panel-snow-hungry-gs2.png) · [3](panel-snow-hungry-gs3.png) |
| Під водою | [2](panel-provided-underwater-gs2.png) · [3](panel-provided-underwater-gs3.png) | [2](panel-sky-underwater-gs2.png) · [3](panel-sky-underwater-gs3.png) | [2](panel-cave-underwater-gs2.png) · [3](panel-cave-underwater-gs3.png) | [2](panel-snow-underwater-gs2.png) · [3](panel-snow-underwater-gs3.png) |
| 31/40 HP | [2](panel-provided-health-40-gs2.png) · [3](panel-provided-health-40-gs3.png) | [2](panel-sky-health-40-gs2.png) · [3](panel-sky-health-40-gs3.png) | [2](panel-cave-health-40-gs2.png) · [3](panel-cave-health-40-gs3.png) | [2](panel-snow-health-40-gs2.png) · [3](panel-snow-health-40-gs3.png) |
| Кулдаун здібності | [2](panel-provided-cooldown-gs2.png) · [3](panel-provided-cooldown-gs3.png) | [2](panel-sky-cooldown-gs2.png) · [3](panel-sky-cooldown-gs3.png) | [2](panel-cave-cooldown-gs2.png) · [3](panel-cave-cooldown-gs3.png) | [2](panel-snow-cooldown-gs2.png) · [3](panel-snow-cooldown-gs3.png) |
| Порожня здібність | [2](panel-provided-empty-ability-gs2.png) · [3](panel-provided-empty-ability-gs3.png) | [2](panel-sky-empty-ability-gs2.png) · [3](panel-sky-empty-ability-gs3.png) | [2](panel-cave-empty-ability-gs2.png) · [3](panel-cave-empty-ability-gs3.png) | [2](panel-snow-empty-ability-gs2.png) · [3](panel-snow-empty-ability-gs3.png) |
| Порожня куля | [2](panel-provided-empty-spirit-gs2.png) · [3](panel-provided-empty-spirit-gs3.png) | [2](panel-sky-empty-spirit-gs2.png) · [3](panel-sky-empty-spirit-gs3.png) | [2](panel-cave-empty-spirit-gs2.png) · [3](panel-cave-empty-spirit-gs3.png) | [2](panel-snow-empty-spirit-gs2.png) · [3](panel-snow-empty-spirit-gs3.png) |
| Остання крапля | [2](panel-provided-minimum-gs2.png) · [3](panel-provided-minimum-gs3.png) | [2](panel-sky-minimum-gs2.png) · [3](panel-sky-minimum-gs3.png) | [2](panel-cave-minimum-gs2.png) · [3](panel-cave-minimum-gs3.png) | [2](panel-snow-minimum-gs2.png) · [3](panel-snow-minimum-gs3.png) |

## Вузьке вікно й шульга

| Рука | 1280×720, Scale 3 | 1280×720, примусовий ×4 |
|---|---|---|
| Правша | [3](narrow-1280x720-right-x3.png) | [×4](narrow-1280x720-right-x4.png) |
| Шульга | [3](narrow-1280x720-left-x3.png) | [×4](narrow-1280x720-left-x4.png) |

Звичайний максимум Minecraft на 1280×720 — 3. ×4 показаний як примусовий тест, а не звичайне доступне налаштування цього вікна. У вузькому корпусі зберігаються і куля 1:1, і оригінальний брелок. Друга рука та індикатор атаки дзеркаляться; іконки предметів хотбара не зсуваються.

## Що саме перевірено

- Рідина: 8592 вибірки, 0 розбіжностей RGBA.
- Кулі: 24 порівняння з ескізом 02; зміни тільки в дозволеній ділянці гравіювання Дурня. Обидві оправи й брелки поза нею ідентичні.
- Корпус: дзеркальна форма та матеріал, висота крил 85 GUI px, усі похилі ребра 45°, один піксель зовнішнього контуру.
- Вкладення: однакова нижня лінія внутрішніх комірок, відсутність їхніх взаємних перетинів на восьми ширинах GUI для обох рук.
- Третя трубка: нульове заповнення й окремий тьмяний стан. Назви всіх трьох символів винесені в підказки та B.
- Наявні файли `core/src`, `mod/src` й попереднього `art` захищені знімком контрольних сум. Старий ескіз 02 не перезаписаний.

Предмети, шрифт і вихідні тла походять із наявних матеріалів попереднього огляду та локального Minecraft 26.2. Камінь і нові компоненти — окремий піксельний макет на цілій сітці. Нових викликів imagegen і перегенерації рідини не було. Ваш скрін — уже наявний crop 986×328 з художньо очищеним тлом; оригінал залишено незмінним.

Це **106 PNG**, автономна сторінка й дані макета. Тут немає реалізації HUD, запуску Minecraft або перевірки шейдерів. Вони належать наступному етапу після явного **«затверджую»**.
