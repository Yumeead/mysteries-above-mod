package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.ThreadVisionRange
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.ThreadSightPayload
import me.vangoo.mysteries.pathways.fool.Marionettes
import net.minecraft.network.chat.Component

/**
 * Fool, Seq. 5 (Marionettist): **Thread Sight**.
 *
 * The Marionettist sees the Threads of the Spirit Body: pale, nearly transparent strands that run
 * out of the whole of every living thing and up into the Spirit World. Weak or sleeping things
 * have dim threads; a dying thing's threads wither. While someone takes a grip, the victim's
 * threads darken from the caster's end, go taut and twitch; a finished puppet's threads are black,
 * still, and run straight to its master's hand. Invisibility hides the body, not the threads.
 *
 * ## The client draws, the server only says who holds whom
 *
 * The first revision sent a few motes over each head from here, twice a second — which is all a
 * thread could be while it was built on the server: a point in the world, already behind the
 * entity by the time it arrived. A strand that rises out of a moving body, sways, and bends
 * towards a hand needs to be placed **every frame**, against the body as it is drawn, so it is
 * built on the client (`client/render/SpiritThreads`) from what the client already knows.
 *
 * What no entity carries is the bond, and that is what this sends: [Marionettes.bonds]. Owner-only
 * still, because it goes down this one connection and no other.
 *
 * ## It ticks, and there is nothing to wait for instead
 *
 * The beat **is** the subscription: the client keeps drawing only while the packets come, so
 * losing the ability (demotion, `forget`, another pathway) switches the sight off with no
 * farewell packet and no deactivation hook.
 */
class ThreadSight : PermanentPassiveAbility(AbilityIdentity.of("fool_thread_sight")), Ticking {

    override val displayName: Component = Component.literal("Бачення ниток")

    /** Twice a second: the client smooths a grip between beats, so this is plenty. */
    override val tickPeriodTicks: Int = 10

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви бачите Нитки Духовного Тіла: бліді пасма, що здіймаються з усього живого в Духовний Світ.\n" +
            "▪ Радіус ${ThreadVisionRange.rangeFor(sequence)} бл.; нитки бачать лише маріонетники\n" +
            "▪ Слабкі й сплячі тьмяніють, у мертвих нитки в'януть; невидимість ниток не ховає\n" +
            "▪ Під час захвату нитки жертви чорніють і тягнуться до руки; у маріонеток вони чорні й нерухомі",
    )

    override fun tick(context: AbilityContext) {
        ModChannels.THREAD_SIGHT.sendTo(
            context.caster,
            ThreadSightPayload(ThreadVisionRange.rangeFor(context.sequence), Marionettes.bonds(context.level)),
        )
    }
}
