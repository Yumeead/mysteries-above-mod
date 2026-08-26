package me.vangoo.mysteries.ability.context

import me.vangoo.domain.entities.Beyonder
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

    val online: Collection<Beyonder>
}
