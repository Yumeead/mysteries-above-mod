package me.vangoo.mysteries.pathways.fool.abilities

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.Beyonders
import me.vangoo.mysteries.ability.context.Fizzle
import me.vangoo.mysteries.ability.context.Messaging
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.effect.privateSound
import me.vangoo.mysteries.effect.privateText
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Pendulum.Swing
import me.vangoo.mysteries.pathways.door.abilities.AntiDivination
import me.vangoo.mysteries.pathways.death.Spirits
import me.vangoo.mysteries.pathways.fool.Faces
import me.vangoo.mysteries.pathways.fool.Marionettes
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.ItemTags
import net.minecraft.tags.StructureTags
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity
import net.minecraft.world.level.block.entity.SpawnerBlockEntity
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fool, Seq. 9 (Seer): **The Arts of Divination**.
 *
 * The Seer asks the spirit world and gets a sign in this one: a coin answers yes or no, a pendulum
 * points, a dream shows a place. Which way is the tool in hand — a gold nugget, an iron chain, or a
 * bed at night — and the question is picked on a screen right after the cast ("Questions"). Leaving
 * the screen costs nothing; without a tool it lists the ways and what each wants.
 *
 * ## The answer can be wrong, and that is the point
 *
 * About whom you ask decides how true it is: of someone your Sequence or weaker, true; one
 * Sequence stronger, foggy — the coin stands on its edge, the chain trembles, the dream blurs; two
 * or more stronger, or shielded by Anti-Divination, the chain spins and snaps, the Seer is struck
 * back (a wound and a fraying mind), and a Beyonder sought feels it. And the more the Seer's mind
 * has frayed, the more often the spirit world lies outright. Questions about places and about the
 * world, not a person, are only exposed to the lying.
 *
 * ## What left and what stayed
 *
 * Astrology went to the Door, whose Astrologer it is. Dream divination stays here, though the
 * Traveller's door also shows a far place: a dream is a blurred grey glimpse near someone, paid for
 * in sanity and once a night — not a road there.
 */
class DivinationArts : ActiveAbility(AbilityIdentity.of("fool_divination_arts")) {

    override val displayName: Component = Component.literal("Мистецтво ворожіння")

    /** The coin's price; the pendulum and the dream pay the rest when the question is picked. */
    override val spiritualityCost: Int = COIN_COST

    override fun cooldownSeconds(sequence: Sequence): Int = COIN_COOLDOWN

    override val icon: ItemStack get() = ItemStack(Items.IRON_CHAIN)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ворожіння: інструмент у руці обирає спосіб, екран «Питання» — питання чи ціль.\n" +
            "▪ Золотий самородок — монета: так чи ні; $COIN_COST духовності, відкат $COIN_COOLDOWN с\n" +
            "▪ Залізний ланцюг — маятник тягнеться до цілі ~15 с; ${COIN_COST + PENDULUM_EXTRA} + $PENDULUM_UPKEEP/с, відкат $PENDULUM_COOLDOWN с\n" +
            "▪ Ліжко вночі — сон-провидіння: сіре видіння біля цілі; ${COIN_COST + DREAM_EXTRA} духовності й глузд, раз на ніч\n" +
            "▪ Про сильнішого — туманно або віддача; що більше втрачено глузду, то частіше брехня",
    )

    override fun cast(context: CastContext): AbilityOutcome = when {
        context.caster.isSleeping -> dream(context)
        context.caster.mainHandItem.`is`(Items.GOLD_NUGGET) -> coin(context)
        context.caster.mainHandItem.`is`(Items.IRON_CHAIN) -> pendulum(context)
        else -> ways(context)
    }

    /** No tool: the screen lists the ways and what each needs, and choosing changes nothing. */
    private fun ways(context: CastContext): AbilityOutcome {
        context.pendingChoices.offer(
            context.caster.uuid,
            Component.literal("Питання: чим ворожити"),
            listOf(
                Component.literal("Монета — візьміть золотий самородок у руку"),
                Component.literal("Маятник духу — візьміть залізний ланцюг у руку"),
                Component.literal("Сон-провидіння — ляжте в ліжко вночі"),
            ),
        ) { _, _ -> }
        return AbilityOutcome.Awaiting()
    }

    // ── The coin ───────────────────────────────────────────────────────────────

    private enum class Ask(val label: String) {
        SAFE("Чи безпечно туди, куди я дивлюсь?"),
        WATCHED("Чи стежить за мною хтось?"),
        TRUE_FACE("Чи той, хто переді мною, той, ким здається?"),
        HIDDEN("Чи є тут щось сховане?"),
    }

    /**
     * A question about what is around: the coin leaves the finger, every eye can follow it, and it
     * lands heads or tails. What that means only the Seer knows — the meaning is theirs alone.
     */
    private fun coin(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val ticket = context.cast
        val beyonders = context.beyonders
        val messaging = context.messaging
        val scheduling = context.scheduling
        val sequence = context.sequence
        val facing = context.targeting.lookingAt(FACE_RANGE)
        context.pendingChoices.offer(caster.uuid, Component.literal("Питання до монети"), Ask.entries.map { Component.literal(it.label) }) { index, _ ->
            val seer = caster.takeIf { !it.isRemoved } ?: return@offer
            val ask = Ask.entries[index]
            val about = (facing as? ServerPlayer).takeIf { ask == Ask.TRUE_FACE }
            val clarity = clarity(beyonders, seer, sequence, about)
            ticket.complete()
            val level = seer.level()
            val toss = seer.eyePosition.add(seer.getViewVector(1f).scale(0.6)).add(0.0, -0.3, 0.0)
            val nugget = ItemEntity(level, toss.x, toss.y, toss.z, ItemStack(Items.GOLD_NUGGET))
            nugget.setNeverPickUp()
            nugget.setDeltaMovement(0.0, COIN_TOSS, 0.0)
            level.addFreshEntity(nugget)
            // A coin of the spirit world, not a nugget to keep: it does not come back from disk.
            nugget.addTag(Spirits.EPHEMERAL)
            level.playSound(null, toss.x, toss.y, toss.z, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.6f, 2.0f)
            scheduling.after(COIN_FALL_TICKS) {
                val landed = nugget.position()
                nugget.discard()
                level.playSound(null, landed.x, landed.y, landed.z, SoundEvents.CHAIN_FALL, SoundSource.PLAYERS, 0.5f, 1.8f)
                val answer = when (clarity) {
                    Clarity.SNAPPED -> {
                        backlash(beyonders, messaging, seer, about)
                        Component.literal("Монета розсипалась на порох")
                    }
                    Clarity.FOGGY -> Component.literal("Монета стала на ребро — дух не відповідає")
                    Clarity.TRUE -> {
                        val yes = truth(seer, ask, facing).let { if (lies(beyonders, seer)) !it else it }
                        Component.literal(if (yes) "Герб — так" else "Решка — ні")
                    }
                }
                // Read a couple of steps ahead, where the coin fell — not in the Seer's face.
                val read = seer.eyePosition.add(seer.getViewVector(1f).let { Vec3(it.x, 0.0, it.z) }.normalize().scale(ANSWER_REACH)).add(0.0, ANSWER_DROP, 0.0)
                privateText(seer, read, answer, ANSWER_TICKS, COIN_GOLD)
                privateSound(seer, landed, SoundEvents.NOTE_BLOCK_BELL.value(), 0.4f, if (clarity == Clarity.TRUE) 1.6f else 0.7f)
            }
        }
        return AbilityOutcome.Awaiting()
    }

    /** The truth of [ask], as the world has it right now. */
    private fun truth(seer: ServerPlayer, ask: Ask, facing: LivingEntity?): Boolean = when (ask) {
        Ask.SAFE -> safe(seer)
        Ask.WATCHED -> watched(seer)
        // "Is the one before me who they seem?" — a mask or a puppet in a stolen face says no.
        Ask.TRUE_FACE -> facing != null && !me.vangoo.mysteries.ability.Disguise.isWorn(facing.uuid) &&
            (facing !is me.vangoo.mysteries.entity.MarionetteEntity || facing.capture == null)
        Ask.HIDDEN -> hidden(seer)
    }

    /** Nothing ahead to fall into, burn in or be struck by, as far as 48 blocks along the gaze. */
    private fun safe(seer: ServerPlayer): Boolean {
        val level = seer.level()
        val look = seer.getViewVector(1f).let { Vec3(it.x, 0.0, it.z) }.takeIf { it.lengthSqr() > 1e-4 }?.normalize() ?: return true
        val from = seer.position()
        var along = 2.0
        while (along <= SAFE_REACH) {
            val at = from.add(look.scale(along))
            val ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.x.toInt(), at.z.toInt())
            if (from.y - ground > CLIFF) return false
            val pos = BlockPos.containing(at.x, ground - 1.0, at.z)
            if (level.getFluidState(pos).`is`(Fluids.LAVA) || level.getFluidState(pos.above()).`is`(Fluids.LAVA)) return false
            along += 2.0
        }
        val path = seer.boundingBox.expandTowards(look.scale(SAFE_REACH)).inflate(SAFE_WIDTH, 4.0, SAFE_WIDTH)
        return level.getEntitiesOfClass(LivingEntity::class.java, path) {
            it !== seer && it.isAlive && (it is Enemy || (it is ServerPlayer && it.mainHandItem.typeHolder().`is`(ItemTags.WEAPON_ENCHANTABLE)))
        }.isEmpty()
    }

    /** Someone unseen near by, or someone out of the Seer's sight with their eyes on the Seer. */
    private fun watched(seer: ServerPlayer): Boolean {
        val look = seer.getViewVector(1f)
        return seer.level().getEntitiesOfClass(LivingEntity::class.java, seer.boundingBox.inflate(WATCH_RADIUS)) { it !== seer && it.isAlive }.any { other ->
            val unseen = other.isInvisible || other.hasEffect(MobEffects.INVISIBILITY)
            val toSeer = seer.eyePosition.subtract(other.eyePosition).normalize()
            val staring = other.getViewVector(1f).dot(toSeer) > STARE && other.hasLineOfSight(seer)
            val behind = look.dot(toSeer.reverse()) < 0.3
            (other is ServerPlayer || other is Enemy) && (unseen || (staring && behind))
        }
    }

    /** An unopened chest with its loot still to roll, or a spawner, within reach of the place. */
    private fun hidden(seer: ServerPlayer): Boolean {
        val level = seer.level()
        val center = seer.blockPosition()
        val reach = HIDDEN_RADIUS.toInt()
        for (cx in ((center.x - reach) shr 4)..((center.x + reach) shr 4)) for (cz in ((center.z - reach) shr 4)..((center.z + reach) shr 4)) {
            val chunk = level.getChunkSource().getChunkNow(cx, cz) ?: continue
            for ((pos, entity) in chunk.blockEntities) {
                if (pos.distSqr(center) > HIDDEN_RADIUS * HIDDEN_RADIUS) continue
                if (entity is SpawnerBlockEntity || (entity is RandomizableContainerBlockEntity && entity.lootTable != null)) return true
            }
        }
        return false
    }

    // ── The spirit pendulum ─────────────────────────────────────────────────────

    /** Where the chain is pulled: a person the Seer has seen, a place of their own, a puppet. */
    private class Mark(val label: Component, val person: UUID?, val where: (ServerPlayer) -> Vec3?)

    private fun marks(context: CastContext, withPuppets: Boolean): List<Mark> {
        val seer = context.caster
        val server = context.level.server
        val people = Faces.seenBy(seer).filter { (face, _) -> server.playerList.getPlayer(face.id) != null }.take(MAX_PEOPLE).map { (face, _) ->
            Mark(Component.literal(face.name), face.id) { asker -> server.playerList.getPlayer(face.id)?.takeIf { it.level() === asker.level() }?.position() }
        }
        val places = buildList {
            add(Mark(Component.literal("Місце моєї смерті"), null) { asker ->
                asker.lastDeathLocation.orElse(null)?.takeIf { it.dimension() == asker.level().dimension() }?.pos()?.let(Vec3::atCenterOf)
            })
            add(Mark(Component.literal("Моє ліжко"), null) { asker ->
                asker.respawnConfig?.respawnData()?.takeIf { it.dimension() == asker.level().dimension() }?.pos()?.let(Vec3::atCenterOf)
            })
            add(Mark(Component.literal("Найближче село"), null) { asker ->
                asker.level().findNearestMapStructure(StructureTags.VILLAGE, asker.blockPosition(), VILLAGE_SEARCH, false)?.let(Vec3::atCenterOf)
            })
            if (withPuppets) Marionettes.of(seer.uuid).forEach { puppet ->
                add(Mark(Component.literal("Маріонетка: ${Marionettes.nameOf(puppet)}"), null) { asker -> puppet.takeIf { it.isAlive && it.level() === asker.level() }?.position() })
            }
        }
        return people + places
    }

    /**
     * The chain hangs from the hand for fifteen seconds and leans towards the mark, swinging the
     * harder the nearer it is. Everyone near sees it sway; only the Seer knows what it seeks.
     */
    private fun pendulum(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val marks = marks(context, withPuppets = true)
        val ticket = context.cast
        val beyonders = context.beyonders
        val messaging = context.messaging
        val cooldowns = context.cooldowns
        val sequence = context.sequence
        val identity = identity
        val chosen = kotlinx.coroutines.CompletableDeferred<Mark>()
        context.pendingChoices.present(
            caster.uuid, marks.size,
            show = { id -> ModChannels.CHOICE_OFFER.sendTo(caster, me.vangoo.mysteries.net.payload.ChoiceOfferPayload(id, Component.literal("Кого чи що шукає маятник"), marks.map { it.label }, false)) },
            onDropped = { chosen.cancel() },
        ) { index, _ -> chosen.complete(marks[index]) }

        context.session {
            val mark = kotlinx.coroutines.withTimeoutOrNull(CHOICE_WAIT_TICKS.toLong() * 50) { chosen.await() } ?: return@session
            val seer = owner ?: return@session
            val beyonder = beyonders.of(seer.uuid) ?: return@session
            if (!beyonder.payUpkeep(PENDULUM_EXTRA)) {
                messaging.hint(seer, Component.literal("Бракує духовності: маятнику треба ${COIN_COST + PENDULUM_EXTRA}"), Fizzle.EMPTY)
                return@session
            }
            ticket.complete()
            cooldowns.set(seer.uuid, identity, PENDULUM_COOLDOWN)
            val person = mark.person?.let { seer.level().server.playerList.getPlayer(it) }
            val clarity = clarity(beyonders, seer, sequence, person)
            if (clarity == Clarity.SNAPPED) {
                swing(seer, Swing.SNAP, 0f, 1f, SNAP_TICKS)
                seer.level().playSound(null, seer.x, seer.y + 1.0, seer.z, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f)
                backlash(beyonders, messaging, seer, person)
                return@session
            }
            // A lie is told once and kept: the chain leans the same wrong way for the whole swing.
            val lie = if (lies(beyonders, seer)) seer.random.nextFloat() * 360f else null
            sustain(periodTicks = SWING_PERIOD, upkeep = PENDULUM_UPKEEP, upkeepPeriodTicks = TICKS_PER_SECOND, durationTicks = PENDULUM_TICKS) { holder ->
                val goal = mark.where(holder)
                if (goal == null) {
                    swing(holder, Swing.STILL, 0f, 0f, SWING_PERIOD + 2)
                    return@sustain
                }
                val to = goal.subtract(holder.position())
                val yaw = lie ?: Math.toDegrees(atan2(-to.x, to.z)).toFloat()
                val strength = (1.0 - to.horizontalDistance() / NEAR_FAR).coerceIn(MIN_SWING, 1.0).toFloat()
                swing(holder, if (clarity == Clarity.FOGGY) Swing.FOG else Swing.SEEK, yaw, strength, SWING_PERIOD + 2)
            }
        }
        return AbilityOutcome.Awaiting()
    }

    /** One beat of the chain, for everyone near. */
    private fun swing(holder: ServerPlayer, kind: Swing, yaw: Float, strength: Float, ticks: Int) {
        ModChannels.WORLD_EFFECT.sendTo(
            PlayerLookup.around(holder.level(), holder.position(), AUDIENCE),
            WorldEffectPayload.Pendulum(holder.id, yaw, strength, kind, ticks),
        )
    }

    // ── The dream ───────────────────────────────────────────────────────────────

    /**
     * Asleep at night, the Seer asks after someone or somewhere and, instead of the black of sleep,
     * stands four seconds near it — grey, blurred, no names, no coordinates — and wakes in bed.
     */
    private fun dream(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val level = context.level
        if (level.isBrightOutside) return AbilityOutcome.failed("Віщі сни сняться лише вночі")
        val night = level.overworldClockTime / DAY_TICKS
        if (dreamt[caster.uuid] == night) return AbilityOutcome.failed("Цієї ночі сон уже був")
        val marks = marks(context, withPuppets = false).filter { it.label.string != "Моє ліжко" }
        val ticket = context.cast
        val beyonders = context.beyonders
        val messaging = context.messaging
        val cooldowns = context.cooldowns
        val sequence = context.sequence
        val identity = identity
        val chosen = kotlinx.coroutines.CompletableDeferred<Mark>()
        context.pendingChoices.present(
            caster.uuid, marks.size,
            show = { id -> ModChannels.CHOICE_OFFER.sendTo(caster, me.vangoo.mysteries.net.payload.ChoiceOfferPayload(id, Component.literal("Про кого чи що сон"), marks.map { it.label }, false)) },
            onDropped = { chosen.cancel() },
        ) { index, _ -> chosen.complete(marks[index]) }

        context.session {
            val mark = kotlinx.coroutines.withTimeoutOrNull(CHOICE_WAIT_TICKS.toLong() * 50) { chosen.await() } ?: return@session
            val sleeper = owner ?: return@session
            val beyonder = beyonders.of(sleeper.uuid) ?: return@session
            val goal = mark.where(sleeper)
            if (goal == null) {
                messaging.hint(sleeper, Component.literal("Сон не знаходить дороги — це в іншому світі"), Fizzle.NONE)
                return@session
            }
            if (!beyonder.payUpkeep(DREAM_EXTRA)) {
                messaging.hint(sleeper, Component.literal("Бракує духовності: снові треба ${COIN_COST + DREAM_EXTRA}"), Fizzle.EMPTY)
                return@session
            }
            ticket.complete()
            cooldowns.set(sleeper.uuid, identity, DREAM_COOLDOWN)
            dreamt[sleeper.uuid] = night
            beyonder.increaseSanityLoss(DREAM_SANITY)
            val person = mark.person?.let { sleeper.level().server.playerList.getPlayer(it) }
            val clarity = clarity(beyonders, sleeper, sequence, person)
            if (clarity == Clarity.SNAPPED) {
                backlash(beyonders, messaging, sleeper, person)
                return@session
            }
            // A lying dream shows somewhere else, as convincingly as the truth.
            val lie = lies(beyonders, sleeper)
            val far = if (clarity == Clarity.FOGGY) FOG_DISTANCE else DREAM_DISTANCE
            val angle = sleeper.random.nextDouble() * 2 * Math.PI
            val near = (if (lie) goal.add(LIE_SHIFT * cos(angle), 0.0, LIE_SHIFT * sin(angle)) else goal)
                .add(far * cos(angle), DREAM_RISE, far * sin(angle))
            Dreams.enter(sleeper, near, goal)
            ModChannels.WORLD_EFFECT.sendTo(sleeper, WorldEffectPayload.Vision(DREAM_TICKS, if (clarity == Clarity.FOGGY) FOG_HAZE else DREAM_HAZE))
            try {
                kotlinx.coroutines.delay(DREAM_TICKS.toLong() * 50)
            } finally {
                owner?.let(Dreams::wake)
            }
        }
        return AbilityOutcome.Awaiting()
    }

    /** Whose night has already had its dream: a night is a day count, so it resets by itself. */
    private val dreamt = HashMap<UUID, Long>()

    // ── How true the answer is ──────────────────────────────────────────────────

    private enum class Clarity { TRUE, FOGGY, SNAPPED }

    /** How clearly the spirit world speaks of [about]: nobody, or someone your Sequence or weaker — truly. */
    private fun clarity(beyonders: Beyonders, seer: ServerPlayer, sequence: Sequence, about: ServerPlayer?): Clarity {
        if (about == null) return Clarity.TRUE
        val theirs = beyonders.of(about.uuid) ?: return Clarity.TRUE
        if (beyonders.isToggled(about.uuid, AntiDivination.IDENTITY)) return Clarity.SNAPPED
        val stronger = sequence.level - theirs.sequenceLevel
        return when {
            stronger >= 2 -> Clarity.SNAPPED
            stronger == 1 -> Clarity.FOGGY
            else -> Clarity.TRUE
        }
    }

    /** The frayed mind hears lies: up to one answer in two, at the edge of breaking. */
    private fun lies(beyonders: Beyonders, seer: ServerPlayer): Boolean {
        val frayed = beyonders.of(seer.uuid)?.sanityLoss?.scale ?: 0
        return seer.random.nextDouble() < frayed / LIE_SCALE
    }

    /** Reaching too high: a wound and a fraying mind for the Seer, and the one sought feels it. */
    private fun backlash(beyonders: Beyonders, messaging: Messaging, seer: ServerPlayer, sought: ServerPlayer?) {
        seer.hurtServer(seer.level(), seer.level().damageSources().magic(), BACKLASH_DAMAGE)
        beyonders.of(seer.uuid)?.increaseSanityLoss(BACKLASH_SANITY)
        messaging.hint(seer, Component.literal("Духовний світ відкинув питання"), Fizzle.NONE)
        if (sought != null && beyonders.of(sought.uuid) != null) {
            privateText(sought, sought.eyePosition.add(sought.getViewVector(1f).scale(1.5)), Component.literal("Хтось шукає вас"), ANSWER_TICKS, SOUGHT)
            privateSound(sought, seer.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.6f)
        }
    }

    private companion object {
        const val COIN_COST = 15
        const val COIN_COOLDOWN = 5
        const val PENDULUM_EXTRA = 35
        const val PENDULUM_UPKEEP = 2
        const val PENDULUM_COOLDOWN = 30
        const val DREAM_EXTRA = 85
        const val DREAM_COOLDOWN = 60
        const val DREAM_SANITY = 6

        const val FACE_RANGE = 24.0
        const val ANSWER_TICKS = 4 * TICKS_PER_SECOND
        const val CHOICE_WAIT_TICKS = 60 * TICKS_PER_SECOND
        const val AUDIENCE = 32.0

        const val COIN_TOSS = 0.42
        const val ANSWER_REACH = 2.5
        const val ANSWER_DROP = -0.15
        const val COIN_FALL_TICKS = 18

        const val SAFE_REACH = 48.0
        const val SAFE_WIDTH = 3.0
        const val CLIFF = 8.0
        const val WATCH_RADIUS = 32.0
        const val STARE = 0.95
        const val HIDDEN_RADIUS = 16.0

        const val MAX_PEOPLE = 12
        const val VILLAGE_SEARCH = 64
        const val PENDULUM_TICKS = 15 * TICKS_PER_SECOND
        const val SWING_PERIOD = 5
        const val SNAP_TICKS = 24
        /** The swing is full this near, and never weaker than [MIN_SWING] however far. */
        const val NEAR_FAR = 256.0
        const val MIN_SWING = 0.15

        const val DAY_TICKS = 24000L
        const val DREAM_TICKS = 4 * TICKS_PER_SECOND
        const val DREAM_DISTANCE = 7.0
        const val FOG_DISTANCE = 16.0
        const val DREAM_RISE = 3.0
        const val LIE_SHIFT = 60.0
        const val DREAM_HAZE = 0.55f
        const val FOG_HAZE = 0.8f

        /** Sanity loss at which half the answers lie. */
        const val LIE_SCALE = 200.0
        const val BACKLASH_DAMAGE = 4f
        const val BACKLASH_SANITY = 4

        val COIN_GOLD: RgbColor = RgbColor.of(255, 214, 122)
        val SOUGHT: RgbColor = RgbColor.of(196, 84, 84)
    }
}

/**
 * The dreaming body: while the Seer dreams they hang unseen near the place, and they wake back in
 * bed. Where to come back to lies on the player (saved), so a crash or a quit mid-dream wakes them
 * at the next join rather than leaving them a ghost far from home.
 */
object Dreams {

    private class Return(val dimension: ResourceKey<net.minecraft.world.level.Level>, val at: Vec3, val mode: GameType)

    private val RETURN_CODEC: Codec<Return> = RecordCodecBuilder.create { row ->
        row.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Return::dimension),
            Vec3.CODEC.fieldOf("at").forGetter(Return::at),
            GameType.CODEC.fieldOf("mode").forGetter(Return::mode),
        ).apply(row, ::Return)
    }

    private val RETURN: AttachmentType<Return> by lazy {
        AttachmentRegistry.create(modId("dream_return")) { it.persistent(RETURN_CODEC) }
    }

    fun install() {
        RETURN
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> wake(handler.player) }
    }

    /** Out of bed and away, unseen, looking at [toward]. */
    fun enter(sleeper: ServerPlayer, near: Vec3, toward: Vec3) {
        sleeper.setAttached(RETURN, Return(sleeper.level().dimension(), sleeper.position(), sleeper.gameMode()))
        sleeper.stopSleeping()
        sleeper.setGameMode(GameType.SPECTATOR)
        val look = toward.subtract(near)
        val yaw = Math.toDegrees(atan2(-look.x, look.z)).toFloat()
        val pitch = Math.toDegrees(-atan2(look.y, look.horizontalDistance())).toFloat()
        sleeper.teleportTo(sleeper.level(), near.x, near.y, near.z, emptySet(), yaw, pitch, false)
    }

    /** Back in bed's place, in their own game mode. Nothing to do for someone not dreaming. */
    fun wake(sleeper: ServerPlayer) {
        val back = sleeper.removeAttached(RETURN) ?: return
        val level: ServerLevel = sleeper.level().server.getLevel(back.dimension) ?: sleeper.level()
        sleeper.teleportTo(level, back.at.x, back.at.y, back.at.z, emptySet(), sleeper.yRot, sleeper.xRot, false)
        sleeper.setGameMode(back.mode)
    }
}
