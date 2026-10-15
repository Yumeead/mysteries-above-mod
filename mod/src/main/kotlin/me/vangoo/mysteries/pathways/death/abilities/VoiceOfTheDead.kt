package me.vangoo.mysteries.pathways.death.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.SpiritMediumLore
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.pathways.death.DEATH_COLOR
import me.vangoo.mysteries.effect.ModParticles
import net.minecraft.core.BlockPos
import net.minecraft.core.GlobalPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.stats.Stats

/**
 * Death, Seq. 7 (Spirit Medium): **Voice of the Dead** — for now, the half that speaks with the
 * living.
 *
 * "It can also be used on living creatures… but this method holds a lot of danger for them": the
 * medium speaks with a living player's spirit, and the spirit tells what it knows of its own body
 * and its own deaths. A stronger Sequence throws the questioning back and the medium's sanity pays.
 *
 * ## Where the other half went
 *
 * The plugin's main branch questioned a lingering soul and took one potion recipe from it — moved,
 * not copied, so the dead player lost it. The mod has no recipe knowledge at all yet: brewing is a
 * later phase. Porting the questioning of the dead without the thing it takes would be a power
 * with nothing at its end, so that branch waits for brewing and comes back together with it —
 * [me.vangoo.mysteries.pathways.death.DeathTraces] already keeps the souls it will question.
 *
 * Everything the spirit tells is vanilla's own state: statistics, the respawn point, the last death
 * location the game already records for the recovery compass.
 */
class VoiceOfTheDead : ActiveAbility(AbilityIdentity.of("death_voice_of_the_dead")) {

    override val displayName: Component = Component.literal("Голос мертвих")

    override val spiritualityCost: Int = SpiritMediumLore.VOICE_COST

    override fun cooldownSeconds(sequence: Sequence): Int = SpiritMediumLore.voiceLivingCooldownSeconds(sequence)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви говорите з духом живого гравця — і він не може змовчати.\n" +
            "▪ Дальність ${SpiritMediumLore.voiceLivingRange(sequence).toInt()} бл.: шлях, тіло, звички, ліжко, остання смерть\n" +
            "▪ Сильніша Послідовність відкидає допит: ви втрачаєте до " +
            "${SpiritMediumLore.voiceBacklashSanity(sequence, Sequence(0))} глузду",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.playerLookingAt(SpiritMediumLore.voiceLivingRange(context.sequence))
            ?: return AbilityOutcome.failed("Гляньте на живого гравця — допитати можна лише його дух")
        val theirs = context.beyonders.of(target.uuid)

        val backlash = theirs?.let { SpiritMediumLore.voiceBacklashSanity(context.sequence, it.sequence) } ?: 0
        if (backlash > 0) {
            context.beyonder.increaseSanityLoss(backlash)
            context.level.playSound(null, context.caster.x, context.caster.y, context.caster.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 0.6f)
            context.messaging.tell(target, Component.literal("✦ Чужа воля торкнулась вашого духу — і відсахнулась"))
            return AbilityOutcome.done("✦ Дух цієї цілі сильніший за ваш: допит відкинуто, глузд −$backlash")
        }

        beam(context, target)
        context.messaging.tell(target, Component.literal("✦ Хтось говорив із вашим духом — і ви не змогли змовчати"))
        context.messaging.tell(context.caster, dossier(target, theirs?.let { "${it.pathway.name}, Посл. ${it.sequence.level}" }))
        return AbilityOutcome.Done()
    }

    private fun dossier(target: ServerPlayer, pathway: String?): Component {
        val stats = target.stats
        val hours = stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / (20 * 3600)
        return Component.literal(
            "════ Дух каже про ${target.name.string} ════\n" +
                "✦ Шлях: ${pathway ?: "звичайна людина"}\n" +
                "✦ Тіло: ${"%.1f".format(target.health)}/${"%.1f".format(target.maxHealth)} ❤ · ситість ${target.foodData.foodLevel}\n" +
                "✦ Убивав гравців: ${stats.getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS))} · сам гинув: " +
                "${stats.getValue(Stats.CUSTOM.get(Stats.DEATHS))} · у світі $hours год\n" +
                "✦ У руці: ${target.mainHandItem.hoverName.string.takeIf { !target.mainHandItem.isEmpty } ?: "нічого"}\n" +
                "✦ Ліжко: ${target.respawnConfig?.respawnData()?.globalPos()?.let(::describe) ?: "невідомо"}\n" +
                "✦ Остання смерть: ${target.lastDeathLocation.map(::describe).orElse("невідомо")}",
        )
    }

    private fun describe(pos: GlobalPos): String = "${pos.dimension().identifier().path} ${coords(pos.pos())}"

    private fun coords(pos: BlockPos): String = "${pos.x}, ${pos.y}, ${pos.z}"

    /** The spirit's words travel from the target's eyes to the medium's. */
    private fun beam(context: CastContext, target: ServerPlayer) {
        val from = target.eyePosition
        val to = context.caster.eyePosition
        val glyph = ModParticles.glyph(DEATH_COLOR)
        val steps = (from.distanceTo(to) * 3).toInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val point = from.lerp(to, i.toDouble() / steps)
            context.level.sendParticles(glyph, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
        }
        context.level.sendParticles(ParticleTypes.SCULK_SOUL, from.x, from.y, from.z, 8, 0.2, 0.2, 0.2, 0.02)
        context.level.playSound(null, to.x, to.y, to.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 0.7f)
    }
}
