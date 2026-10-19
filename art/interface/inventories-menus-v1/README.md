# Інвентарі й меню: старе золото / пергамент

**[Реальні кадри з гри: Scale 2 / 3](game-review.html)** · [Звіт ігрового огляду](GAME-REVIEW.md) · [Макети й кіт](review.html) · [Аудит і рішення](AUDIT.md) · [223 текстури з розмірами та scaling](textures-in-scope.md) · [Повний перелік екранів](screens-all.md) · [Специфікація кіту](kit-spec.json)

**Інтеграція очікує «затверджую».** Робочий код і ресурси мода, налаштування гри й користувацькі світи не змінені. Комітів немає. Крім аудиту й макетів, `live-preview` містить генератор оглядового пака та помічник для ізольованої тимчасової копії гри. В основний мод вони не включені.

Уточнений обсяг: основний інтерфейс Minecraft — інвентарі, контейнери, Escape і ванільні налаштування. Нові представлення B вилучені. Декоративні підкреслення й кольорові смужки не використовуються; вибраний стан позначається яскравішою золотою рамкою або кутовими дужками з палітри кіту.

Актуальне уточнення: усі комірки мають один бронзово-золотий стиль `#BA985B`, включно з хотбаром, бронею, другою рукою, крафтом, результатами й рецептами. Рамка панелей — 2 GUI px із трьох тонів кіту; тепле поле `#DCC58D` має делікатний дізеринг. Попередній поділ слотів на темні та оливкові скасовано.

Затверджена палітра ескізу 04: `#DFC28B / #B99A61 / #65533A`, 1 px темний контур. Світла тепла панель зберігає читабельність зашитого `#404040` — **6,13:1**. Предмети 16×16; вибір позначається тільки золотом кіту. Темний корпус ванільних контейнерів не пропонується через відсутність безпечного глобального кольору підписів. Власні кольори предметів і тексту рідкості збережені.

Відкривайте галерею при масштабі браузера **100%**. Повні PNG — **1920×1080**, GUI Scale **2 / 3**, без зменшення до ширини вікна. ×4 — окреме збільшення від GUI-пікселя, не обіцянка розміщення великого аркуша на Scale 4. Галерея також показує 1/4 там, де екран уміщується, і попереджає про великі дизайн-аркуші на 4.

## Усі ескізи

Для кожного екрана — день і печера, ×4 та обидва реальні масштаби. Денне тло — ясне небо з наявного ігрового F1-знімка; печера — також наявний F1-знімок. Обидва 1920×1080, без генерації чи ретуші. Інтерфейс поверх них — **макет**, не знімок готової реалізації.

| Екран | День ×4 | Печера ×4 | День Scale 2 / 3 | Печера Scale 2 / 3 |
|---|---|---|---|---|
| Кіт | [×4](details/kit-day-x4.png) | [×4](details/kit-cave-x4.png) | [2](screens/kit-day-gs2.png) / [3](screens/kit-day-gs3.png) | [2](screens/kit-cave-gs2.png) / [3](screens/kit-cave-gs3.png) |
| E: броня, друга рука, 2×2, модель, книга, ефекти | [×4](details/inventory-day-x4.png) | [×4](details/inventory-cave-x4.png) | [2](screens/inventory-day-gs2.png) / [3](screens/inventory-day-gs3.png) | [2](screens/inventory-cave-gs2.png) / [3](screens/inventory-cave-gs3.png) |
| Скриня | [×4](details/chest-day-x4.png) | [×4](details/chest-cave-x4.png) | [2](screens/chest-day-gs2.png) / [3](screens/chest-day-gs3.png) | [2](screens/chest-cave-gs2.png) / [3](screens/chest-cave-gs3.png) |
| Подвійна скриня | [×4](details/double-chest-day-x4.png) | [×4](details/double-chest-cave-x4.png) | [2](screens/double-chest-day-gs2.png) / [3](screens/double-chest-day-gs3.png) | [2](screens/double-chest-cave-gs2.png) / [3](screens/double-chest-cave-gs3.png) |
| Верстак | [×4](details/crafting-day-x4.png) | [×4](details/crafting-cave-x4.png) | [2](screens/crafting-day-gs2.png) / [3](screens/crafting-day-gs3.png) | [2](screens/crafting-cave-gs2.png) / [3](screens/crafting-cave-gs3.png) |
| Піч | [×4](details/furnace-day-x4.png) | [×4](details/furnace-cave-x4.png) | [2](screens/furnace-day-gs2.png) / [3](screens/furnace-day-gs3.png) | [2](screens/furnace-cave-gs2.png) / [3](screens/furnace-cave-gs3.png) |
| Підказка | [×4](details/tooltip-day-x4.png) | [×4](details/tooltip-cave-x4.png) | [2](screens/tooltip-day-gs2.png) / [3](screens/tooltip-day-gs3.png) | [2](screens/tooltip-cave-gs2.png) / [3](screens/tooltip-cave-gs3.png) |
| Книга рецептів відкрита | [×4](details/recipe-book-day-x4.png) | [×4](details/recipe-book-cave-x4.png) | [2](screens/recipe-book-day-gs2.png) / [3](screens/recipe-book-day-gs3.png) | [2](screens/recipe-book-cave-gs2.png) / [3](screens/recipe-book-cave-gs3.png) |
| Творчий: вкладки й прокрутка | [×4](details/creative-day-x4.png) | [×4](details/creative-cave-x4.png) | [2](screens/creative-day-gs2.png) / [3](screens/creative-day-gs3.png) | [2](screens/creative-cave-gs2.png) / [3](screens/creative-cave-gs3.png) |
| Творчий: пошук | [×4](details/creative-search-day-x4.png) | [×4](details/creative-search-cave-x4.png) | [2](screens/creative-search-day-gs2.png) / [3](screens/creative-search-day-gs3.png) | [2](screens/creative-search-cave-gs2.png) / [3](screens/creative-search-cave-gs3.png) |
| Пауза | [×4](details/pause-day-x4.png) | [×4](details/pause-cave-x4.png) | [2](screens/pause-day-gs2.png) / [3](screens/pause-day-gs3.png) | [2](screens/pause-cave-gs2.png) / [3](screens/pause-cave-gs3.png) |
| Налаштування: список, поле, повзунок, перемикач, чекбокс | [×4](details/settings-day-x4.png) | [×4](details/settings-cave-x4.png) | [2](screens/settings-day-gs2.png) / [3](screens/settings-day-gs3.png) | [2](screens/settings-cave-gs2.png) / [3](screens/settings-cave-gs3.png) |
| П'ять станів кнопки, три ширини | [×4](details/button-states-day-x4.png) | [×4](details/button-states-cave-x4.png) | [2](screens/button-states-day-gs2.png) / [3](screens/button-states-day-gs3.png) | [2](screens/button-states-cave-gs2.png) / [3](screens/button-states-cave-gs3.png) |

## Важливі межі ескізів

- **Пауза 26.2:** широка кнопка 204×20; запитані 200×20 також показані в кіті. Вузька 98×20, значки 20×20. Дії кнопок лишаються ванільними. Використані справжні силуети значків, перефарбовані в палітру кіту, включно з FriendsButton.
- **Підписи:** безпечної глобальної заміни #404040 немає. E у ванілі малює лише назву крафту над 2×2 (у 26.2 — «Майстрування»); зайвий заголовок над основною сіткою не додається. Інші контейнери мають «Інвентар» на своїх штатних координатах.
- **Друга рука:** рамка слота `(76,61)` і предмет `(77,62)` лишаються на ванільних координатах. Виправлено область моделі на `(25,7,51,72)`: вона більше не перекриває слот, обидва нижні краї закінчуються на `y=78`, як у ванілі. [Ванільна геометрія ×4](references/vanilla-inventory-x4.png).
- **Титульний екран:** спільні `widget/*` і `tooltip/*` не можна безумовно перефарбувати в глобальному pack. Пропозиція — вимиканий pack контейнерів/книги плюс умовні власні віджети/підказки лише на дозволених екранах. У «ванільному» всі нові гілки й pack вимкнені.
- **Перевірочні предмети:** золото в сітках і вкладках показує контраст, не є пропозицією міняти категорії чи результати пошуку. Рецепти в книзі — демонстраційний набір. Модель E — схематична фронтальна проєкція оригінального скіну, не живий 3D-рендер.
- **Українська й пікселі:** наявні ванільні гліфи, включно з `і ї є ґ І Ї Є Ґ`; жодної нової гарнітури. Перевірено цілі масштаби й незмінні кути 9-slice; реальні input і narration перевіряються після реалізації.

## Перевірки й відтворення

[verification.json](verification.json) — 52 повні PNG, 26 фрагментів ×4, усі гліфи, контраст, відсутність переповнення підписів кнопок, кути 9-slice на ширинах 20/98/200/204, єдиний матеріал слотів і вирівнювання другої руки. [browser-verification.json](browser-verification.json) — завантаження зображень і перемикачі галереї у локальному headless Chrome. [protected-before.json](protected-before.json) — контрольні суми 1786 попередніх відстежуваних файлів; перевірено незмінність.

Локальне відтворення з кореня репозиторію, з наявними Python/Pillow, JDK 25 і локальним JAR 26.2:

```powershell
python art/interface/inventories-menus-v1/audit.py
python art/interface/inventories-menus-v1/build-review.py
python art/interface/inventories-menus-v1/check-review.py
```

В ізольованому ігровому огляді перевірені замовлені інвентарі й пауза, а також справжній екран налаштувань звуку: перетягування повзунків, наведення, перехід сторінки книги рецептів і фокус паузи. Оновлені стрілки й лупа малюються в точних розмірах без стискання. Сторонні доповнення рецептів не запускалися. Геометрична сумісність обґрунтована збереженням PNG/UV/Slot, але її перевірка з конкретними сторонніми модами лишається ігровим етапом після реалізації.

Після «затверджую» — ваші п'ять етапів, починаючи з кіту й генератора; після E та одинарної скрині — ваша перевірка в грі до продовження решти контейнерів.
