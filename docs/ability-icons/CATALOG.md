# Каталог концепцій здібностей

156 унікальних ability identity; 163 входжень у Pathways з урахуванням спільних сил. Кожен сюжет спирається на поточну реалізацію, посилання на код наведені в таблицях. Готові PNG: [повний Fool — 18 здібностей](fool/README.md), [повний Door — 14 здібностей](door/README.md), [повний Sun — 18 здібностей](sun/README.md), [повний Justiciar — 16 здібностей](justiciar/README.md), [повний Death — 20 здібностей](death/README.md), [повний Error — 19 здібностей](error/README.md), [повний Tyrant — 31 здібність](tyrant/README.md) та [повний WhiteTower — 10 здібностей](whitetower/README.md).

Базовий RGB — `PathwayBranding.liquid`. Тіні й висвітлення будуються навколо нього; legacy-колір тексту не замінює цю палітру.

| Pathway | Базовий RGB | Здібностей |
|---|---|---|
| Error | `#6699A1` | 19 |
| Visionary | `#8FB5F1` | 17 |
| Door | `#6AE6F7` | 14 |
| Justiciar | `#EBC38E` | 16 |
| WhiteTower | `#7686E1` | 10 |
| Fool | `#A26ED7` | 18 |
| Sun | `#FBE86B` | 18 |
| Tyrant | `#4673C7` | 31 |
| HangedMan | `#CC3635` | 0 |
| Darkness | `#7F95B9` | 0 |
| Death | `#728F6C` | 20 |
| TwilightGiant | `#E87863` | 0 |
| RedPriest | `#FF473A` | 0 |
| Demoness | `#D2409F` | 0 |
| Hermit | `#7C62B3` | 0 |
| Paragon | `#F3914B` | 0 |
| Mother | `#3CB799` | 0 |
| Moon | `#FF6068` | 0 |
| Abyss | `#841F11` | 0 |
| Chained | `#433F6B` | 0 |
| BlackEmperor | `#1D2341` | 0 |
| WheelOfFortune | `#A1CED7` | 0 |

13 Pathways без здібностей не отримують вигаданих іконок. Послідовності з тим самим identity — рівні однієї сили, а не різні здібності.

Спільні сили потребують палітри свого Pathway: для Fool, Sun, Justiciar, Death та Error такі варіанти вже створені, решта спільних сил поки використовує попередні піктограми. Для `common_*` не можна визначати палітру лише за префіксом identity; потрібно врахувати джерело сили. Ці варіанти не входять у тестову п’ятірку.

`door_recorded_*` — динамічні одноразові записи чужих активних сил. Для повного набору: сюжет вихідної здібності в палітрі Door з невеликим мотивом сторінки. Універсальний сувій приховав би призначення сили. Викрадені й позичені сили також повинні зберігати впізнаваний сюжет; їхні варіанти залишені наступному етапу.

## common

| Identity / назва | Що зображати | Pathways | Код |
|---|---|---|---|
| `common_combat_proficiency` — Бойова майстерність | Клинок, який відводить ворожий удар; чітка точка парирування біля гарди. | Error, Justiciar, WhiteTower | [CombatProficiency](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/common/CombatProficiency.kt) |
| `common_ritual_magic` — Ритуальна магія | Три запалені свічки навколо матеріальної жертви на вівтарі; без написів. | Death, Fool, Sun, WhiteTower | [RitualMagic](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/common/RitualMagic.kt) |

## death

| Identity / назва | Що зображати | Pathways | Код |
|---|---|---|---|
| `death_cadaveric_resilience` — Стійкість до трупних аур | Холодна долоня лишається неушкодженою під морозом і краплями тліну. | Death | [CadavericResilience](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/CadavericResilience.kt) |
| `death_corpse_guise` — Трупна личина | Людський профіль ховається під виразною зеленою оболонкою зомбі. | Death | [CorpseGuise](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/CorpseGuise.kt) |
| `death_death_sight` — Бачення духів | Прихована темна постать видає себе густим світлим димком душі. | Death | [DeathSight](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/DeathSight.kt) |
| `death_door_to_the_underworld` — Двері в Загробний Світ | Темна брама й примарні руки затягують душу через поріг. | Death | [DoorToTheUnderworld](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/DoorToTheUnderworld.kt) |
| `death_eye_of_death` — Око Смерті | Безбарвне око згори відкриває один уразливий вузол у ребрах цілі. | Death | [EyeOfDeath](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/EyeOfDeath.kt) |
| `death_gatekeeper_step` — Крок Вартового | Важкий крок із зеленою хвилею руху розколює землю при приземленні. | Death | [GatekeeperStep](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/GatekeeperStep.kt) |
| `death_gloomy_presence` — Похмура присутність | Холодний спокійний силует проходить поруч із нежиттю, яка відвертається. | Death | [GloomyPresence](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/GloomyPresence.kt) |
| `death_internal_underworld` — Внутрішній Загробний Світ | Грудна клітка утворює темну камеру з одним замкненим духом. | Death | [InternalUnderworld](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/InternalUnderworld.kt) |
| `death_language_of_the_dead` — Мова мертвих | Вимовлена сила відокремлює душу від тіла, залишаючи коротку духовну прив’язь. | Death | [LanguageOfTheDead](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/LanguageOfTheDead.kt) |
| `death_physique` — Фізичні посилення | Міцні плечі зі спини та холодне світло вздовж хребта. | Death | [Death](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/Death.kt) |
| `death_resurrection` — Воскресіння | Мертва кисть і передпліччя підіймаються з могильної землі. | Death | [Resurrection](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/Resurrection.kt) |
| `death_spirit_channeling` — Допомога середніх духів | Долоня підносить крижане приношення одному морозному духові-помічнику. | Death | [SpiritChanneling](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/SpiritChanneling.kt) |
| `death_spirit_communication` — Призив простого духа | Один дух обвиває щиколотки цілі й міцно їх утримує. | Death | [SpiritCommunication](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/SpiritCommunication.kt) |
| `death_spirit_pact` — Домовленість з духами | Владна долоня спрямовує впорядкований почет нежиті й духів. | Death | [SpiritPact](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/SpiritPact.kt) |
| `death_spirit_perception` — Сприйняття духів | Заплющені очі й духовний відбиток за кам’яною перешкодою. | Death | [SpiritPerception](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/SpiritPerception.kt) |
| `death_spirit_vision` — Духовний зір | Відкрите око бачить зелену душу серед сірих тіл. | Death | [SpiritVision](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/SpiritVision.kt) |
| `death_spirit_world_knowledge` — Знання Світу Духів | Над чистими сторінками розкритої книги проявляється будова духа. | Death | [SpiritWorldKnowledge](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/SpiritWorldKnowledge.kt) |
| `death_undead_knowledge` — Знання нежиті | Череп у розрізі з виразно підсвіченою вразливою тріщиною. | Death | [UndeadKnowledge](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/UndeadKnowledge.kt) |
| `death_voice_of_the_dead` — Голос мертвих | Менший духовний профіль відповідає зсередини грудей живої людини. | Death | [VoiceOfTheDead](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/death/abilities/VoiceOfTheDead.kt) |

## door

| Identity / назва | Що зображати | Pathways | Код |
|---|---|---|---|
| `door_anti_divination` — Протидія ворожінню | Door: призма відхиляє промінь; Justiciar: непрозора світла завіса приховує постать і відхиляє пошукову нитку. | Door, Justiciar | [AntiDivination](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/door/abilities/AntiDivination.kt) |
| `door_blink` — Миттєвий крок | Два послідовні дверні отвори в перспективі, з'єднані коротким світлим слідом руху. | Door | [Blink](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/door/abilities/Blink.kt) |
| `door_burning` — Підпал | Малий спалах на поверхні блоку від кінчика пальця; вузький локальний підпал. | Door, Fool | [Burning](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/door/abilities/Burning.kt) |
| `door_decrypt_patterns` — Розшифрування патернів | Розділені уламки чужого магічного сліду складаються у зрозумілу геометричну фігуру. | Door | [DecryptPatterns](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/door/abilities/DecryptPatterns.kt) |
| `door_divination_arts` — Мистецтво ворожіння | Кристалічний маятник над точкою пошуку; тонкий відблиск указує знайдений напрямок. | Door | [DivinationArts](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/door/abilities/DivinationArts.kt) |
| `door_electric_shock` — Електричний розряд | Короткий ламаний електричний розряд між двома кінцями; без небесної хмари. | Door | [ElectricShock](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/door/abilities/ElectricShock.kt) |
| `door_escape_trick` — Трюк зі зникненням | Постать відскакує назад, залишаючи попереду широку завісу диму. | Door | [EscapeTrick](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/door/abilities/EscapeTrick.kt) |
| `door_flash` — Спалах | Різкий багатопроменевий фотоспалах і маленький силует, що зникає за ним. | Door | [Flash](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/door/abilities/Flash.kt) |
| `door_opening` — Відкриття дверей | Товстий зріз мурованої стіни з прорізаним світлим проходом крізь її товщину. | Door | [DoorOpening](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/door/abilities/DoorOpening.kt) |
| `door_physical_enhancement` — Фізичні посилення | Динамічний торс, крізь який проходять зміщені просторові площини; посилена рухливість. | Door | [Door](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/door/Door.kt) |
| `door_record` — Запис здібності | Відкрита чиста сторінка приймає відбиток чужого магічного ефекту; без тексту. | Door | [Record](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/door/abilities/Record.kt) |
| `door_spiritual_intuition` — Духовна інтуїція | Тонкий сигнал наближається до скроні раніше, ніж темне вістря доходить до постаті. | Door | [SpiritualIntuition](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/door/abilities/SpiritualIntuition.kt) |
| `door_spiritual_vision` — Духовне бачення | Око зі світлим прозорим перерізом живого торса; видно його життєву ауру. | Door | [SpiritualVision](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/door/abilities/SpiritualVision.kt) |
| `door_travellers_door` — Двері Мандрівника | Один відкритий дверний отвір, за яким простягається безмежна біла рівнина Світу Духів. | Door | [TravellersDoor](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/door/abilities/TravellersDoor.kt) |

## error

| Identity / назва | Що зображати | Pathways | Код |
|---|---|---|---|
| `error_conceptual_attack` — Крадіжка удару | Ворожий удар згортається всередину долоні й стає зарядом для відповіді. | Error | [AttackTheft](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/abilities/AttackTheft.kt) |
| `error_conceptual_capacity` — Крадіжка здатності | Примарне крило переходить від цілі до крадія як символ забраного польоту. | Error | [AbilityTheft](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/abilities/AbilityTheft.kt) |
| `error_conceptual_dream` — Крадіжка сну | Сон витягується з голови на подушці та зберігається в руці крадія. | Error | [DreamTheft](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/abilities/DreamTheft.kt) |
| `error_conceptual_heart` — Крадіжка серця | Червоне серце переходить під світлий хват крадія; біля власника лишається порожній відбиток. | Error | [HeartTheft](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/abilities/HeartTheft.kt) |
| `error_conceptual_ideal` — Крадіжка ідеалу | Тепла іскра прагнення виходить із грудей, а постать без неї схиляється. | Error | [IdealTheft](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/abilities/IdealTheft.kt) |
| `error_conceptual_thought` — Крадіжка думки | Нитка наміру залишає спорожнілу голову й змушує крадія кинутися вперед. | Error | [ThoughtUsurpation](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/abilities/ThoughtUsurpation.kt) |
| `error_decryption` — Дешифрування | Вузол прихованих слідів розплутується в чіткі світлі нитки над каменем. | Error | [Decryption](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/abilities/Decryption.kt) |
| `error_eloquence` — Красномовність | Світлі хвилі голосу змушують ворожий клинок опуститися. | Error | [Eloquence](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/abilities/Eloquence.kt) |
| `error_material_theft` — Крадіжка матеріалів | Духовний матеріал проходить крізь кам’яну стіну до простягнутих пальців. | Error | [MaterialTheft](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/abilities/MaterialTheft.kt) |
| `error_mental_disruption` — Ментальний розлад | Навколо голови розходяться несумісні площини фальшивих стін. | Error | [MentalDisruption](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/abilities/MentalDisruption.kt) |
| `error_mental_fortitude` — Стійкість розуму | Міцне світле ядро розуму лишається цілим, відкидаючи темні тріщини. | Error | [MentalFortitude](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/abilities/MentalFortitude.kt) |
| `error_observation` — Покращена спостережливість | Уважне око відчуває коштовну жилу за каменем за напрямком світлого пилу. | Error | [SuperiorObservation](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/abilities/SuperiorObservation.kt) |
| `error_physique` — Фізичні посилення | Спритна постать робить низький бічний крок; світло підкреслює рух м’язів. | Error | [Error](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/Error.kt) |
| `error_power_theft` — Крадіжка сили | Сформований магічний заряд відривається від чужої долоні й переходить до крадія. | Error | [PowerTheft](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/abilities/PowerTheft.kt) |
| `error_shadow_theft` — Крадіжка тіні | Рука з темряви за спиною цілі дістає коштовність із кишені. | Error | [ShadowTheft](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/abilities/ShadowTheft.kt) |
| `error_swindler_charm` — Шарм Афериста | Спокійна усмішка й розслаблена чужа рука показують довіру, що виникає сама. | Error | [SwindlerCharm](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/abilities/SwindlerCharm.kt) |
| `error_theft_of_heavenly_secrets` — Крадіжка Таємниць Небес | Зілля на вівтарі й запозичене небесне світло надають чужій личині прийняття. | Error | [TheftOfHeavenlySecrets](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/abilities/TheftOfHeavenlySecrets.kt) |
| `error_thought_misdirection` — Підміна думок | Роздвоєна хвиля хибної уваги повертає двох ворогів один проти одного. | Error | [ThoughtMisdirection](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/error/abilities/ThoughtMisdirection.kt) |

## fool

| Identity / назва | Що зображати | Pathways | Код |
|---|---|---|---|
| `fool_agility` — Спритність клоуна | Акробатична постать перевертається над вузькою опорою; розведені кінцівки й контрольоване падіння. | Fool | [ClownAgility](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/fool/abilities/ClownAgility.kt) |
| `fool_air_bullet` — Повітряна куля | Пальці у формі пістолета й стислий світлий удар повітря з коротким слідом спотворення. | Fool | [AirBullet](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/fool/abilities/AirBullet.kt) |
| `fool_aquatic_breath` — Підводне дихання | Профіль під поверхнею води; тонкий канал повітря з'єднує рот із поверхнею. | Fool | [AquaticBreath](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/fool/abilities/AquaticBreath.kt) |
| `fool_damage_transfer` — Перенос шкоди | Свіжа тріщина на руці стягується до меншого шраму; світлий потік закриває рану. | Fool | [DamageTransfer](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/fool/abilities/DamageTransfer.kt) |
| `fool_danger_intuition` — Інтуїція небезпеки | Зігнута тіньова голка зупиняється перед уже відхиленою головою; передчуття удару. | Fool | [DangerIntuition](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/fool/abilities/DangerIntuition.kt) |
| `fool_divination_arts` — Мистецтво ворожіння | Монета під маятником, над якими відкривається маленький фрагмент сновидіння. | Fool | [DivinationArts](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/fool/abilities/DivinationArts.kt) |
| `fool_expression_control` — Контроль міміки | Незворушна театральна маска закриває мінливий темний профіль; обличчя нічого не видає. | Fool | [ExpressionControl](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/fool/abilities/ExpressionControl.kt) |
| `fool_flame_jump` — Стрибок крізь полум'я | Постать занурюється в одну язикоподібну полум'яну щілину й виходить із другої. | Fool | [FlameJump](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/fool/abilities/FlameJump.kt) |
| `fool_illusion_creation` — Створення ілюзій | Напівпрозора примарна стопа залишає переконливий слід там, де немає тіла. | Fool | [IllusionCreation](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/fool/abilities/IllusionCreation.kt) |
| `fool_marionette_possession` — Вселення | Світла душа переходить у маріонетку, позаду залишається безвільне власне тіло. | Fool | [Possession](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/fool/abilities/Possession.kt) |
| `fool_marionettist_control` — Контроль Маріонетки | Пальці зверху натягують нитки до ще живої постаті; одна половина ниток уже потемніла. | Fool | [MarionetteControl](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/fool/abilities/MarionetteControl.kt) |
| `fool_paper_cutter` — Паперовий різак | Зігнутий аркуш стає гострим метальним лезом; одна велика кромка й видимий згин паперу. | Fool | [PaperCutter](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/fool/abilities/PaperCutter.kt) |
| `fool_paper_substitution` — Заміна паперовою лялькою | Паперова людська фігурка розірвана навскіс; із розриву виходять магія й великі клаптики. | Fool | [PaperSubstitution](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/fool/abilities/PaperSubstitution.kt) |
| `fool_shapeshifting` — Перевтілення | Обличчя змінює профіль, друга личина розгортається з паперової площини. | Fool | [Shapeshifting](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/fool/abilities/Shapeshifting.kt) |
| `fool_spirit_vision` — Духовне бачення | Око розділяє живу ауру на шари навколо силуету; без ниток керування. | Fool | [SeerSpiritVision](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/fool/abilities/SeerSpiritVision.kt) |
| `fool_thread_sight` — Бачення ниток | Невидимий темний силует окреслений лише кількома блідими нитками, що йдуть угору. | Fool | [ThreadSight](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/fool/abilities/ThreadSight.kt) |

## justiciar

| Identity / назва | Що зображати | Pathways | Код |
|---|---|---|---|
| `justiciar_arbiters_gaze` — Погляд Судді | Одне суворе золоте око схиляє чужу голову під тиском погляду. | Justiciar | [ArbitersGaze](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/justiciar/abilities/ArbitersGaze.kt) |
| `justiciar_area_of_jurisdiction` — Сфера Юрисдикції | Окреслена світлом територія закону на камені з постаттю всередині. | Justiciar | [AreaOfJurisdiction](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/justiciar/abilities/AreaOfJurisdiction.kt) |
| `justiciar_authority` — Авторитет | Замкнена золота рамка над долонею тисне на схилену постать. | Justiciar | [Authority](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/justiciar/abilities/Authority.kt) |
| `justiciar_brand_of_restraint` — Клеймо Обмеження | Три ланцюги сковують тіло й ноги; залізний обруч замикається на голові. | Justiciar | [BrandOfRestraint](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/justiciar/abilities/BrandOfRestraint.kt) |
| `justiciar_intuition` — Інтуїція Небезпеки | Золоті хвилі передчуття біля вуха помічають клинок позаду. | Justiciar | [Intuition](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/justiciar/abilities/Intuition.kt) |
| `justiciar_physique` — Фізичні посилення | Напружена зігнута рука й плече з теплим світлом у м’язах, сліди швидкого руху. | Justiciar | [Justiciar](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/justiciar/Justiciar.kt) |
| `justiciar_power_prohibition` — Заборона | Закон замикає світлий затискач навколо клинка, припиняючи заборонену дію. | Justiciar | [PowerProhibition](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/justiciar/abilities/PowerProhibition.kt) |
| `justiciar_psychic_lashing` — Психічне Шмагання | Сталеве лезо сокири вкривають ілюзорні блискавки для удару по духу. | Justiciar | [PsychicLashing](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/justiciar/abilities/PsychicLashing.kt) |
| `justiciar_psychic_piercing` — Психічний Прокол | Два розряди з очей пробивають голову та зсувають блідий духовний двійник. | Justiciar | [PsychicPiercing](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/justiciar/abilities/PsychicPiercing.kt) |
| `justiciar_punishment` — Покарання | Силует судді виникає позаду порушника для неминучого карального удару. | Justiciar | [Punishment](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/justiciar/abilities/Punishment.kt) |
| `justiciar_recognition` — Ідентифікація | Чіткий світлий контур вивченої цілі між золотими осередками уваги. | Justiciar | [Recognition](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/justiciar/abilities/Recognition.kt) |
| `justiciar_spawn_prohibition` — Заборона Спавну | Нечисть розпадається на темні уламки біля світлої межі забороненої території. | Justiciar | [SpawnProhibition](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/justiciar/abilities/SpawnProhibition.kt) |
| `justiciar_verdict` — Вердикт | Суддівський молот опускається над терезами як символ винесення різних вироків. | Justiciar | [Verdict](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/justiciar/abilities/Verdict.kt) |
| `justiciar_whip_of_pain` — Хлист Болю | Батіг із залізних ланок розгортається дугою та б’є світлим ударом. | Justiciar | [WhipOfPain](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/justiciar/abilities/WhipOfPain.kt) |

## sun

| Identity / назва | Що зображати | Pathways | Код |
|---|---|---|---|
| `sun_blessing` — Благословення | Розкриті долоні оберігають союзника золотим світлом; нефритовий акцент відновлення. | Sun | [Blessing](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/sun/abilities/Blessing.kt) |
| `sun_cleave_of_purification` — Очищувальний удар | Реальний клинок несе загострену смугу святого світла на кромці; коротка атакувальна діагональ. | Sun | [CleaveOfPurification](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/sun/abilities/CleaveOfPurification.kt) |
| `sun_contract` — Магічне засвідчення | Двоє тримають чистий золотий сувій із печаткою; взаємний мир або боргова клятва. | Sun | [Contract](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/sun/abilities/Contract.kt) |
| `sun_daytime` — Денне світло | Маленьке сонце поряд із постаттю освітлює зріз печери й відсуває темряву від стін. | Sun | [Daytime](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/sun/abilities/Daytime.kt) |
| `sun_evil_detection` — Виявлення зла | Сяючий контур нежиті проступає крізь темний мур; акцент на виявленій нечисті. | Sun | [EvilDetection](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/sun/abilities/EvilDetection.kt) |
| `sun_fire_of_light` — Вогонь Світла | Широке золоте полум'я з трьома нерівними язиками тримається на короткому уступі землі. | Sun | [FireOfLight](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/sun/abilities/FireOfLight.kt) |
| `sun_halo` — Німб Сонця | Один виразний німб над головою, від постаті нижче розходиться світлий імпульс. | Sun | [SunHalo](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/sun/abilities/SunHalo.kt) |
| `sun_holy_light_summoning` — Святе світло | Масивний вертикальний стовп святого вогню падає з відкритого неба в точку на землі. | Sun | [HolyLightSummoning](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/sun/abilities/HolyLightSummoning.kt) |
| `sun_holy_oath` — Свята клятва | Піднятий стиснутий кулак палає під прямим сонячним променем; сила клятви й видимість. | Sun | [HolyOath](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/sun/abilities/HolyOath.kt) |
| `sun_holy_water` — Свята вода Сонця | Скляна пляшечка виплескує освячену воду; прозора форма й золоті відблиски крапель. | Sun | [SunHolyWater](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/sun/abilities/SunHolyWater.kt) |
| `sun_holy_wave` — Хвиля святості | Широкий фронт світла виходить назовні: піднімає живий силует і розчиняє темну нежить. | Sun | [HolyWave](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/sun/abilities/HolyWave.kt) |
| `sun_night_vision` — Нічне бачення | Два маленьких сонця горять у темних очницях; великі читабельні очі замість загального сонця. | Sun | [NightVision](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/sun/abilities/NightVision.kt) |
| `sun_notarization` — Нотаріальність | Рука ставить рельєфну світлу печатку на чистому аркуші; геометрія печатки без літер. | Sun | [Notarization](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/sun/abilities/Notarization.kt) |
| `sun_physical_enhancement` — Фізичні посилення | Живий торс зі світлом у м'язах та розкритими плечима; без броні й палаючого кулака. | Sun | [Sun](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/sun/Sun.kt) |
| `sun_resistance_to_horror` — Стійкість до жаху | Спокійний профіль під світлом, перед яким поступово коротшає пазур темного страху. | Sun | [ResistanceToHorror](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/sun/abilities/ResistanceToHorror.kt) |
| `sun_singing` — Спів | Відкриті вуста барда й послідовні світлі хвилі, що збираються біля слухача; без нотних знаків. | Sun | [Singing](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/sun/abilities/Singing.kt) |
| `sun_sunshine` — Сонячне сяйво | Один вузький прямий промінь святого світла влучає в темну ціль; переважно горизонтальна композиція. | Sun | [Sunshine](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/sun/abilities/Sunshine.kt) |

## tyrant

| Identity / назва | Що зображати | Pathways | Код |
|---|---|---|---|
| `tyrant_abyssal_dominion` — Володіння Безоднею | Постать під важкою товщею води захищена глибиною й сама б’є сильніше. | Tyrant | [AbyssalDominion](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/AbyssalDominion.kt) |
| `tyrant_azure_wave` — Лазурна Хвиля | Високий водяний гребінь котиться вздовж землі й зносить темну постать. | Tyrant | [AzureWave](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/AzureWave.kt) |
| `tyrant_cleansing_light` — Очищувальне Світло | Чиста синя вода змиває гарячі язики полум’я з поверхні. | Tyrant | [CleansingLight](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/CleansingLight.kt) |
| `tyrant_corrosive_deluge` — Їдка Злива | Підвішена синя сфера тріскається й обрушує їдкі краплі на землю. | Tyrant | [CorrosiveDeluge](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/CorrosiveDeluge.kt) |
| `tyrant_eavesdrop` — Шепіт Вітру | Велике вухо перехоплює принесений вітром відбиток далекого дихання. | Tyrant | [Eavesdrop](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/Eavesdrop.kt) |
| `tyrant_illusory_scales` — Ілюзорна луска | Під водою на шкірі проявляються великі міцні риб’ячі луски. | Tyrant | [IllusoryScales](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/IllusoryScales.kt) |
| `tyrant_lightning_bolt_arrow` — Стріла-Блискавка | Прямий блискавичний спис наскрізь пробиває ряд темних цілей. | Tyrant | [LightningBoltArrow](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/LightningBoltArrow.kt) |
| `tyrant_lightning_strike` — Удар Блискавки | Вертикальний розгалужений розряд уражає головну ціль і перескакує на сусідні. | Tyrant | [LightningStrike](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/LightningStrike.kt) |
| `tyrant_physique` — Фізичні посилення | Сильна постать пливе під водою, обтікаючи плечима глибоку течію. | Tyrant | [Tyrant](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/Tyrant.kt) |
| `tyrant_precise_throw` — Точний Кидок | Кинутий водяний болт вивертає коротку точну дугу до однієї цілі. | Tyrant | [PreciseThrow](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/PreciseThrow.kt) |
| `tyrant_pressure_wave` — Владар Вітру | Кільце стисненого повітря розходиться від постаті й відкидає уламки. | Tyrant | [PressureWave](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/PressureWave.kt) |
| `tyrant_raging_blow` — Нищівний Удар | Один заряджений кулак розриває повітря фронтальним ударом. | Tyrant | [RagingBlow](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/RagingBlow.kt) |
| `tyrant_restorative_water` — Цілюща вода | Синя вода огортає руку союзника й змикає над нею світлий відновлювальний потік. | Tyrant | [RestorativeWater](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/RestorativeWater.kt) |
| `tyrant_sea_lunge` — Морський ривок | Постать виривається вперед із водяного сплеску, відкидаючи ворога. | Tyrant | [SeaLunge](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/SeaLunge.kt) |
| `tyrant_sea_memory` — Морська Пам'ять | Заряджений компас пам’ятає берег і веде стрілку до нього. | Tyrant | [SeaMemory](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/SeaMemory.kt) |
| `tyrant_sea_tongue` — Мова Моря | Морська істота повертається на поклик і веде за собою союзну живність. | Tyrant | [SeaTongue](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/SeaTongue.kt) |
| `tyrant_singing` — Спів | Широкий духовний голос хвилями пригнічує й розладнує ворогів. | Tyrant | [Singing](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/Singing.kt) |
| `tyrant_slick_water` — Слизька Вода | Стопа з’їжджає убік по плоскому дзеркальному розливу води. | Tyrant | [SlickWater](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/SlickWater.kt) |
| `tyrant_stance` — Рівновага | Дві стійкі стопи над хиткою палубою та подушкою стисненого повітря. | Tyrant | [Balance](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/Balance.kt) |
| `tyrant_storm_heart` — Штормове Серце | Виснажене серце спалахує синім штормом усередині грудей. | Tyrant | [StormHeart](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/StormHeart.kt) |
| `tyrant_thunder_voice` — Громовий Голос | Голос-блискавка переносить послання від уст до одного далекого адресата. | Tyrant | [ThunderVoice](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/ThunderVoice.kt) |
| `tyrant_water_control` — Владарювання Водою | Рука піднімає й закручує струмінь із наявної води. | Tyrant | [WaterControl](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/WaterControl.kt) |
| `tyrant_water_curtain` — Водяна Завіса | Стіна синьої води поглинає палаючий снаряд і закриває союзника. | Tyrant | [WaterCurtain](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/WaterCurtain.kt) |
| `tyrant_water_film` — Плівка Задухи | Тонка вода щільно огортає голову, затримуючи останню бульбашку біля рота. | Tyrant | [WaterFilm](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/WaterFilm.kt) |
| `tyrant_wind_binding` — Вітряні Пута | Замкнений спіральний вихор охоплює тіло й утримує стопи на землі. | Tyrant | [WindBinding](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/WindBinding.kt) |
| `tyrant_wind_flight` — Політ на вітрі | Постать підіймається над урвищем на широкій спіралі повітря. | Tyrant | [WindFlight](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/WindFlight.kt) |
| `tyrant_wind_imbued_hands` — Руки Вітру | Дві долоні охоплені щільними синіми обручами повітря для ближніх ударів. | Tyrant | [WindImbuedHands](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/WindImbuedHands.kt) |
| `tyrant_wind_pull` — Поклик Вітру | Вихор біля долоні підхоплює з землі кілька кинутих предметів. | Tyrant | [WindPull](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/WindPull.kt) |
| `tyrant_wind_sprint` — Вітряний біг | Нога в швидкому кроці тягне за собою довгі сині смуги вітру. | Tyrant | [WindSprint](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/WindSprint.kt) |
| `tyrant_windblades` — Леза Вітру | Три нерівні вигнуті леза стисненого повітря розходяться різальним віялом. | Tyrant | [Windblades](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/Windblades.kt) |
| `tyrant_wrath` — Лють | Палаючий погляд і напружені плечі в синьому вихорі гніву. | Tyrant | [Wrath](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/tyrant/abilities/Wrath.kt) |

## visionary

| Identity / назва | Що зображати | Pathways | Код |
|---|---|---|---|
| `visionary_alteration` — Видозміна | Усередині профілю голови один вузол з'єднує маленький тригер і приховану пастку. | Visionary | [Alteration](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/visionary/abilities/Alteration.kt) |
| `visionary_appeasement` — Умиротворення | Відкрита заспокійлива долоня розгладжує ламані хвилі навколо голови в одну рівну дугу. | Visionary | [Appeasement](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/visionary/abilities/Appeasement.kt) |
| `visionary_battle_hypnotism` — Бойовий гіпноз | Одне вороже око втрачає відбиток гіпнотизера; сусідній маленький силует залишається видимим. | Visionary | [BattleHypnotism](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/visionary/abilities/BattleHypnotism.kt) |
| `visionary_danger_sense` — Психічне відлуння | Довге уважне спостереження відкриває схований за спиною ворога великий ніж. | Visionary | [DangerSense](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/visionary/abilities/DangerSense.kt) |
| `visionary_dragon_scale` — Луска дракона | Кілька великих драконячих лусок перекривають одна одну, відводячи язик полум'я. | Visionary | [DragonScale](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/visionary/abilities/DragonScale.kt) |
| `visionary_dream_traversal` — Блукання у снах | Постать проходить у світлий сон над головою сплячого; розрив простору має форму сновидіння. | Visionary | [DreamTraversal](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/visionary/abilities/DreamTraversal.kt) |
| `visionary_good_memory` — Хороша пам'ять | Чітке обличчя зберігається світлим відбитком, тоді як його власник уже зникає за стіною. | Visionary | [GoodMemory](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/visionary/abilities/GoodMemory.kt) |
| `visionary_guidance` — Керівництво | Світла ментальна прив'язь повертає сплячу постать до руки провідника. | Visionary | [Guidance](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/visionary/abilities/Guidance.kt) |
| `visionary_intent_reader` — Зчитування намірів | Профіль із відкритим над скронею наміром: маленький спрямований клинок; без чужої багатоколірної палітри. | Visionary | [IntentReader](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/visionary/abilities/IntentReader.kt) |
| `visionary_physical_enhancement` — Фізичні посилення | Гнучкий живий торс зі світлими нервовими шляхами від мозку до рук і ніг. | Visionary | [Visionary](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/visionary/Visionary.kt) |
| `visionary_psychic_cue` — Психічний сигнал | Палець торкається скроні й замикає один світлий психологічний блок у темному профілі. | Visionary | [PsychicCue](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/visionary/abilities/PsychicCue.kt) |
| `visionary_psychological_invisibility` — Психологічна невидимість | Постать розчиняється всередині поля чужої уваги, залишаючи порожній людський контур. | Visionary | [PsychologicalInvisibility](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/visionary/abilities/PsychologicalInvisibility.kt) |
| `visionary_scan_gaze` — Сканування поглядом | Вузький спрямований погляд розкриває життєві шари одного торса; чіткий активний фокус. | Visionary | [ScanGaze](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/visionary/abilities/ScanGaze.kt) |
| `visionary_scan_gaze_passive` — [Пасивна] Сканування поглядом | Спокійне напіввідкрите око й уже проявлений життєвий відбиток поруч; без атакувального променя. | Visionary | [ScanGazePassive](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/visionary/abilities/ScanGazePassive.kt) |
| `visionary_sharp_vision` — Гострий зір | Широко відкрите око витягує з темряви кілька живих контурів; широке поле огляду. | Visionary | [SharpVision](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/visionary/abilities/SharpVision.kt) |
| `visionary_surge_of_insanity` — Всплеск божевілля | Різкі нерівні промені вириваються з голови й розколюють зовнішній спокійний контур. | Visionary | [SurgeOfInsanity](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/visionary/abilities/SurgeOfInsanity.kt) |
| `visionary_telepathy` — Телепатія | Два профілі торкаються світлими шарами думок; глибокий фрагмент проходить між скронями. | Visionary | [Telepathy](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/visionary/abilities/Telepathy.kt) |

## whitetower

| Identity / назва | Що зображати | Pathways | Код |
|---|---|---|---|
| `whitetower_agility` — Спритність | Легка постать перестрибує розрив між уступами, залишаючи короткий світлий слід. | WhiteTower | [Agility](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/whitetower/abilities/Agility.kt) |
| `whitetower_analysis` — Аналіз | Чужа магічна сила розбирається на складники й тимчасово відтворюється на долоні. | WhiteTower | [Analysis](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/whitetower/abilities/Analysis.kt) |
| `whitetower_crime_reconstruction` — Реконструкція | Два примарні учасники минулого бою з’єднані відновленою траєкторією удару. | WhiteTower | [CrimeReconstruction](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/whitetower/abilities/CrimeReconstruction.kt) |
| `whitetower_deduction` — Дедукція | Кілька слідів послідовних ударів сходяться у знайдену вразливу точку. | WhiteTower | [Deduction](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/whitetower/abilities/Deduction.kt) |
| `whitetower_enhanced_mind` — Загострений розум | Профіль читача розпізнає внутрішню структуру каменю й живої цілі. | WhiteTower | [EnhancedMind](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/whitetower/abilities/EnhancedMind.kt) |
| `whitetower_mirror_curse` — Дзеркальне прокляття | Латунне дзеркало відбиває постать із тріщинами давніх ран. | WhiteTower | [MirrorCurse](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/whitetower/abilities/MirrorCurse.kt) |
| `whitetower_physical_enhancement` — Фізичні посилення | Міцні передпліччя читача підіймають важкий фоліант. | WhiteTower | [WhiteTower](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/whitetower/WhiteTower.kt) |
| `whitetower_spellcasting` — Складене заклинання | Розкрита книга складає геометричні фрагменти у спрямований магічний промінь. | WhiteTower | [Spellcasting](../../mod/src/main/kotlin/me/vangoo/mysteries/pathways/whitetower/abilities/Spellcasting.kt) |
