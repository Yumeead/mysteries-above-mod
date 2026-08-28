package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.contracts.ContractTerm
import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import me.vangoo.domain.contracts.Contract as DomainContract

/**
 * Sun, Посл. 6 (Нотаріус): **Магічне засвідчення**.
 *
 * Нотаріус пропонує гравцеві мир — і мир набуває чинності лише тоді, коли той
 * **вклониться у відповідь**: присяде й подивиться на Нотаріуса протягом
 * [OFFER_SECONDS] с. Поки контракт діє, жоден із двох не може зашкодити іншому.
 *
 * ## Уклін замість меню
 *
 * Плагінний `ContractMenu` — 437 рядків: скриня з іконками «Мир», «Обмін», «Борг»,
 * друга сторінка з підтвердженням, третя з предметом обміну. Меню там існувало не тому,
 * що угода складна, а тому що іншого способу спитати «згоден?» на Spigot не було.
 *
 * Спосіб є, і він у грі з першого дня: **два гравці дивляться один на одного і
 * присідають**. Це той самий жест, яким гравці Minecraft вітаються двадцять років — і
 * він, на відміну від скрині, видимий збоку, не паузить нікого й не потребує клієнта.
 *
 * ## Обіцянка виконується, а не карається
 *
 * Мир не «забороняє» шкоду штрафом за порушення — він її **скасовує**
 * (`IncomingDamage.cancel`). Порушити контракт неможливо, тож ні стану «BREACHED», ні
 * покарання, ні перевірки не потрібно. Обіцянка тримається сама, і саме це робить її
 * вартою Посл. 6.
 *
 * ## Чому лише `PEACE`
 *
 * `ContractTerm` має ще `DEBT` — і саме він потребує того, чого в моді ще немає:
 * борг, від якого можна втекти виходом із гри, це не борг. `DEBT` увімкнеться разом із
 * фазою 09, коли контракти буде де зберігати; тут для нього нема сховища, а не задуму.
 */
class Contract : ActiveAbility(AbilityIdentity.of("sun_contract")) {

    override val displayName: Component = Component.literal("Магічне засвідчення")

    override val spiritualityCost: Int = 100

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Пропонує гравцеві під прицілом Золотий Сувій миру.\n" +
            "▪ Він приймає, присівши й подивившись на вас протягом $OFFER_SECONDS с\n" +
            "▪ Поки діє (${peaceSeconds(sequence)} с), ви двоє не можете зашкодити одне одному\n" +
            "▪ Відмова не коштує нічого",
    )

    /** Друга пропозиція поверх першої — сесія в гравця одна. */
    override fun canCast(context: CastContext): Boolean = !context.sessionRunning

    override fun cast(context: CastContext): AbilityOutcome {
        val other = context.targeting.playerLookingAt(RANGE)
            ?: return AbilityOutcome.failed("Немає кому пропонувати: наведіть погляд на гравця")
        if (other.uuid == context.caster.uuid) {
            return AbilityOutcome.failed("Із собою не домовляються")
        }

        val notary = context.caster.uuid
        val party = other.uuid
        val pending = context.cast
        val messaging = context.messaging
        val events = context.events
        val mote = ModParticles.mote(context.effects.pathwayColor)
        val peaceTicks = peaceSeconds(context.sequence) * TICKS_PER_SECOND

        messaging.tell(other, Component.literal("${context.caster.name.string} пропонує вам мир."))
        messaging.actionBar(party, Component.literal("Присядьте й подивіться на нього, щоб прийняти"))

        context.session {
            repeat(OFFER_SECONDS * TICKS_PER_SECOND / POLL_TICKS) {
                val self = owner ?: return@session
                val guest = self.level().server.playerList.getPlayer(party) ?: return@session
                scroll(self, guest, mote)

                if (bowsTo(guest, self)) {
                    // Домен уже вміє контракт — тут він потрібен рівно для того, щоб угода
                    // мала ім'я, стан і термін, а не була анонімною підпискою.
                    val contract = DomainContract.propose(notary, party, ContractTerm.PEACE, emptyMap())
                    contract.sign()

                    events.on<IncomingDamage>(durationTicks = peaceTicks) { damage ->
                        val victim = damage.victim.uuid
                        val attacker = damage.attacker?.uuid ?: return@on
                        if (!contract.involves(victim) || !contract.involves(attacker)) return@on
                        if (victim == attacker) return@on
                        damage.cancel()
                        messaging.actionBar(attacker, Component.literal("Сувій тримає: ви не можете його вдарити"))
                    }

                    pending.complete()
                    seal(self, guest)
                    val seconds = peaceTicks / TICKS_PER_SECOND
                    messaging.tell(notary, Component.literal("Сувій підписано: мир на $seconds с"))
                    messaging.tell(party, Component.literal("Сувій підписано: мир на $seconds с"))
                    return@session
                }
                waitTicks(POLL_TICKS)
            }
            // Квиток лишився непогашеним — відмова справді нічого не коштує.
            messaging.actionBar(notary, Component.literal("Сувій згорнувся: відповіді не було"))
        }

        return AbilityOutcome.Awaiting(Component.literal("Сувій розгорнуто перед ${other.name.string}"))
    }

    /**
     * Уклін: присів і дивиться на Нотаріуса.
     *
     * Погляд перевіряється скалярним добутком, а не «чи в перехресті» — уклін це жест,
     * а не постріл, і вимагати від гравця точності прицілу тут було б знущанням.
     */
    private fun bowsTo(guest: ServerPlayer, notary: ServerPlayer): Boolean {
        if (!guest.isShiftKeyDown) return false
        val toNotary = notary.position().subtract(guest.position())
        if (toNotary.lengthSqr() > RANGE * RANGE) return false
        return guest.getViewVector(1.0f).dot(toNotary.normalize()) >= FACING_DOT
    }

    /** Сувій між двома: нитка порошинок, поки пропозиція жива. */
    private fun scroll(notary: ServerPlayer, guest: ServerPlayer, mote: ParticleOptions) {
        val level = notary.level() as? ServerLevel ?: return
        val from = notary.eyePosition
        val to = guest.eyePosition
        val steps = (from.distanceTo(to) / STEP).toInt().coerceIn(1, MAX_STEPS)
        for (i in 0..steps) {
            val point: Vec3 = from.lerp(to, i.toDouble() / steps)
            level.sendParticles(mote, point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.0)
        }
    }

    /** Печатка — обом, і в один і той самий момент. */
    private fun seal(notary: ServerPlayer, guest: ServerPlayer) {
        val level = notary.level() as? ServerLevel ?: return
        listOf(notary, guest).forEach { party ->
            level.sendParticles(
                net.minecraft.core.particles.ParticleTypes.END_ROD,
                party.x, party.y + party.bbHeight, party.z,
                18, 0.4, 0.3, 0.4, 0.04,
            )
        }
        level.playSound(
            null, notary.x, notary.y, notary.z,
            SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7f, 1.6f,
        )
    }

    private fun peaceSeconds(sequence: Sequence): Int =
        scale(BASE_PEACE_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val RANGE = 15.0
        const val COOLDOWN_SECONDS = 40
        const val OFFER_SECONDS = 15
        const val BASE_PEACE_SECONDS = 60
        const val POLL_TICKS = 4
        /** ~66° конус: уклін, а не приціл. */
        const val FACING_DOT = 0.4
        const val STEP = 0.4
        const val MAX_STEPS = 60
    }
}
