package me.vangoo.mysteries.pathways.death.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.pathways.death.Retinue
import me.vangoo.mysteries.pathways.death.Spirits
import me.vangoo.mysteries.pathways.death.isUndead
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.tags.EntityTypeTags
import net.minecraft.world.entity.LivingEntity

/**
 * Death, Seq. 6 (Spirit Usher): **Spirit World Knowledge**.
 *
 * "Considerable information about the Spirit World." Knowledge is not an act, so it is free and
 * passive: look at a dead soul and it names itself — its kind, its strength and how to take it.
 * The living it quietly ignores; the Spirit World says nothing about them.
 *
 * The weakness is vanilla's own data, not a line of flavour: whether Smite bites it
 * (`SENSITIVE_TO_SMITE`), whether daylight burns it, whether it already serves someone.
 */
class SpiritWorldKnowledge :
    PermanentPassiveAbility(AbilityIdentity.of("death_spirit_world_knowledge")),
    Ticking {

    override val tickPeriodTicks: Int = TICKS_PER_SECOND

    override val displayName: Component = Component.literal("Знання Світу Духів")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Кожна мертва душа сама називає вам себе.\n" +
            "▪ Погляд на нежить чи духа (до ${LOOK_RANGE.toInt()} бл.): рід, здоров'я, чиїй волі служить і чим її брати",
    )

    override fun tick(context: AbilityContext) {
        val target = context.targeting.lookingAt(LOOK_RANGE) ?: return
        val kind = when {
            Spirits.isSpirit(target) -> "Дух"
            target.isUndead() -> "Нежить"
            else -> return
        }
        val master = Retinue.ownerOf(target)?.let { id -> context.level.server.playerList.getPlayer(id)?.name?.string ?: "чужий провідник" }
        context.messaging.actionBar(
            context.caster,
            Component.literal(
                "✦ $kind ${target.name.string} — ${"%.0f".format(target.health)} ❤ · слабке місце: ${weakness(target)}" +
                    (master?.let { " · служить: $it" } ?: ""),
            ),
        )
    }

    private fun weakness(target: LivingEntity): String = when {
        Spirits.isSpirit(target) -> "наказ провідника, а не клинок"
        target.type.builtInRegistryHolder().`is`(EntityTypeTags.SENSITIVE_TO_SMITE) -> "освячена сталь (Кара) і сонце"
        else -> "сонце"
    }

    private companion object {
        const val LOOK_RANGE = 16.0
    }
}
