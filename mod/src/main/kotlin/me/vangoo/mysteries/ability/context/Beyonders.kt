package me.vangoo.mysteries.ability.context

import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.AbilityIdentity
import net.minecraft.world.entity.Entity
import java.util.UUID

/**
 * Хто з присутніх — Потойбічний і який саме.
 *
 * Реєстр живий: доменний [Beyonder] існує лише поки гравець у грі, а поза грою його
 * тримає сховище (фаза 09). Здібності ходять сюди по чужий стан — власний уже лежить
 * у контексті.
 */
interface Beyonders {

    fun of(id: UUID): Beyonder?

    fun of(entity: Entity): Beyonder? = of(entity.uuid)

    fun isBeyonder(id: UUID): Boolean = of(id) != null

    /**
     * Чи тримає гравець [id] увімкненим тогл [ability] **прямо зараз**.
     *
     * Потрібне тому, що тогл буває **щитом**: `AntiDivination` Дверей нічого не робить
     * сам — він лише робить хибним чуже гадання. Без цього запиту опір довелося б
     * оформити подією, яку публікує кожен, хто ворожить, і на яку підписується щит, —
     * тобто двома точками замість однієї, і жодна з них не була б простішою.
     *
     * Реєстр тоглів живе в `PassiveTicker`; сюди він приїжджає лямбдою, бо той
     * будується пізніше — контекст йому самому й потрібен.
     */
    fun isToggled(id: UUID, ability: AbilityIdentity): Boolean

    val online: Collection<Beyonder>
}
