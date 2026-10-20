package me.vangoo.mysteries.creature

import me.vangoo.domain.creatures.AbilityCastPlanner
import me.vangoo.domain.creatures.BossPhases
import me.vangoo.domain.creatures.SequencePressure
import me.vangoo.domain.creatures.ThreatTable
import me.vangoo.mysteries.ability.grantAttribute
import me.vangoo.mysteries.ability.modifierId
import me.vangoo.mysteries.ability.revokeAttribute
import me.vangoo.mysteries.pathways.death.Retinue
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.CreatureMovePayload
import me.vangoo.mysteries.pathways.fool.Marionettes
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.BossEvent
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.IdentityHashMap
import java.util.UUID

/**
 * Everything a creature knows and wants, for as long as its body is loaded.
 *
 * The goals ([me.vangoo.mysteries.creature.ai]) are its limbs — they move, strike, run — and this
 * is what they consult. Nothing here is saved: a grudge, a wound-up technique, the current act of a
 * boss fight are all things a creature may honestly lose when its chunk sleeps. What must survive —
 * who it is, where it lives — is on the entity itself ([Creatures.PERSONA], [Creatures.ANCHORS]).
 *
 * ## Three ideas the plugin's creatures did not have
 *
 * 1. **Grudges, not proximity.** MythicMobs aimed every skill at `@NearestPlayer`. A creature here
 *    fights whoever hurt it most ([threat]) and only falls back on the nearest when nobody has.
 * 2. **A Sequence is felt.** A creature with a characteristic will not start a fight two Sequences
 *    above its own, and breaks one off if such a Beyonder walks up ([dreads]).
 * 3. **A technique can be seen coming.** A wound-up technique marks its spot and lands there; the
 *    plugin's `kitcast` fired instantly at wherever the player happened to be.
 */
class Mind(
    val mob: Mob,
    val key: ResourceKey<CreatureSpec>,
    val spec: CreatureSpec,
    val persona: Persona?,
    val anchors: Anchors,
) {

    val level: ServerLevel get() = mob.level() as ServerLevel

    /** The body's own age: it pauses with its chunk, and so does everything timed here. */
    val now: Long get() = mob.tickCount.toLong()

    val pathway: String? = persona?.pathway ?: spec.pathway
    val sequence: Int? = persona?.sequence ?: spec.sequence
    val role: Role get() = spec.mind.role
    val temperament: Temperament get() = spec.mind.temperament

    val threat = ThreatTable<UUID>()

    var kit: CreatureKit? = null
        private set
    private var planner: AbilityCastPlanner? = null
    private val kitSkills = HashMap<String, CreatureSkill>()
    private val readyAt = IdentityHashMap<Reaction, Long>()

    /** A technique being wound up: the spot is marked, the blow has not landed. */
    class Channel(val skill: CreatureSkill, val target: LivingEntity?, val impact: Vec3, val releaseAt: Long)

    var channel: Channel? = null
        private set

    var provokedUntil = 0L
    private var panicUntil = 0L
    private var dangerFrom: LivingEntity? = null
    private var lastHurtAt = Long.MIN_VALUE / 2
    private var combatSince = -1L

    /** A boss walking back to its arena: it neither picks fights nor answers them on the way. */
    var returning = false

    private val acts: BossPhases? = spec.boss?.let { BossPhases(listOf(1f) + it.phases.map(Phase::below)) }
    var act = 0
        private set
    var pausedUntil = 0L
        private set
    var enraged = false
        private set
    private var bar: ServerBossEvent? = null

    private var spokeAt = Long.MIN_VALUE / 2
    private val greeted = HashMap<UUID, Long>()

    /** How hard the techniques land: an enraged boss half again. */
    val power: Float get() = if (enraged) ENRAGED_POWER else 1f

    init {
        equip(spec.kit ?: Creatures.kitFor(level, pathway, sequence))
    }

    // ── Who it is to others ──────────────────────────────────────────────────────

    /** On someone's threads or in someone's retinue: the will is theirs, not its own. */
    val isOwned: Boolean get() = Retinue.isServant(mob) || Marionettes.ownerOf(mob) != null

    fun dreads(other: LivingEntity): Boolean =
        !spec.mind.fearless && SequencePressure.dreads(sequence, Creatures.sequenceOf(other))

    private fun preysOn(other: LivingEntity): Boolean = SequencePressure.preysOn(sequence, Creatures.sequenceOf(other))

    fun canFight(other: LivingEntity): Boolean =
        other !== mob && other.isAlive && !(other is Player && (other.isCreative || other.isSpectator)) &&
            !Creatures.areAllies(mob, other)

    // ── The kit ──────────────────────────────────────────────────────────────────

    private fun equip(kitKey: ResourceKey<CreatureKit>?) {
        val resolved = kitKey?.let { Creatures.kit(level, it) }
        kit = resolved
        kitSkills.clear()
        readyAt.clear()
        planner = resolved?.let { k ->
            k.skills.forEach { entry -> Creatures.skill(level, entry.skill)?.let { kitSkills[entry.skill.identifier().toString()] = it } }
            AbilityCastPlanner(
                k.skills.filter { it.skill.identifier().toString() in kitSkills }
                    .map { AbilityCastPlanner.KitEntry(it.skill.identifier().toString(), it.cooldown.toLong()) },
                k.gcd.toLong(),
            )
        }
        val haste = resolved?.haste ?: 0
        if (haste > 0) {
            mob.grantAttribute(Attributes.MOVEMENT_SPEED, HASTE_ID, haste * HASTE_STEP, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
        } else {
            mob.revokeAttribute(Attributes.MOVEMENT_SPEED, HASTE_ID)
        }
    }

    // ── Choosing a fight ─────────────────────────────────────────────────────────

    /**
     * Whom to fight now, or `null` to stand down. Grudges come first, then the temperament's own
     * appetite; a dreaded Beyonder is never chosen — [shouldFlee] answers them instead.
     */
    fun chooseTarget(): LivingEntity? {
        if (returning || temperament == Temperament.WILLLESS || temperament == Temperament.TIMID || role == Role.CITIZEN) return null
        val range = mob.getAttributeValue(Attributes.FOLLOW_RANGE)
        val around = level.getEntitiesOfClass(LivingEntity::class.java, mob.boundingBox.inflate(range, range / 2, range)) {
            canFight(it) && !dreads(it) && it.distanceToSqr(mob) <= range * range
        }
        threat.pick(mob.target?.uuid, around.map { it.uuid })?.let { id -> return around.first { it.uuid == id } }

        val sees = { other: LivingEntity -> mob.sensing.hasLineOfSight(other) }
        return when {
            role == Role.GUARD -> around
                .filter { (it is Enemy && !Creatures.isCreature(it)) || Creatures.mindOf(it)?.isHostileBeast == true }
                .filter(sees)
                .minByOrNull { it.distanceToSqr(mob) }

            temperament == Temperament.HOSTILE -> around
                .filter { (it is Player || Creatures.mindOf(it)?.role.let { r -> r == Role.CITIZEN || r == Role.GUARD }) && sees(it) }
                // A characteristic calls to another: a Beyonder eight blocks further off is chosen first.
                .minByOrNull { it.distanceTo(mob) - (if (Creatures.sequenceOf(it) != null) BEYONDER_PULL else 0.0) - (if (it is Player) PLAYER_PULL else 0.0) }

            temperament == Temperament.PREDATORY -> around
                .filter { it is Player && preysOn(it) && sees(it) }
                .minByOrNull { it.distanceToSqr(mob) }

            else -> null
        }
    }

    val isHostileBeast: Boolean get() = role == Role.BEAST && temperament == Temperament.HOSTILE

    /** Takes [target] as its own — or lets the current one go when it is no longer worth fighting. */
    fun engage(target: LivingEntity?) {
        val before = mob.target
        if (target === before) return
        Creatures.aim(mob, target)
        if (target != null && mob.target === target) {
            if (combatSince < 0) combatSince = now
            if (before == null) say(spec.speech.fight)
        }
    }

    fun stillWorthFighting(target: LivingEntity): Boolean {
        if (!canFight(target) || dreads(target) || target.level() !== mob.level()) return false
        val range = mob.getAttributeValue(Attributes.FOLLOW_RANGE) * LOSE_INTEREST
        if (mob.distanceToSqr(target) > range * range) return false
        val boss = spec.boss ?: return true
        return target.position().distanceToSqr(anchors.homeVec) <= boss.leash * boss.leash
    }

    // ── Fighting ─────────────────────────────────────────────────────────────────

    /** One tick of a fight: the reflexes at arm's length, then the rotation. */
    fun combatTick() {
        val target = mob.target ?: return
        val kit = kit ?: return
        if (channel != null || now < pausedUntil) return

        val distanceSq = mob.distanceToSqr(target)
        for (reaction in kit.reactions) {
            if (reaction.trigger == Reaction.Trigger.NEAR && ready(reaction) && distanceSq <= reaction.distance * reaction.distance) {
                fire(reaction, target)
            }
        }

        val sees = mob.sensing.hasLineOfSight(target)
        val distance = Math.sqrt(distanceSq)
        val pick = planner?.pickNext(now) { id ->
            val skill = kitSkills[id]
            skill != null && distance <= (skill.range ?: kit.range) && (sees || skill.aroundSelf) &&
                (!skill.beyondersOnly || Creatures.sequenceOf(target) != null)
        } ?: return
        begin(kitSkills.getValue(pick), target)
    }

    private fun begin(skill: CreatureSkill, target: LivingEntity) {
        perform(skill)
        if (skill.windup <= 0) {
            release(skill, target, target.position())
            return
        }
        channel = Channel(skill, target, target.position(), now + skill.windup)
        level.playSound(null, mob.x, mob.y, mob.z, SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 0.6f, 1.4f)
    }

    /** A tick of winding up: face the mark, show it, and land when the time comes. */
    fun channelTick() {
        val wound = channel ?: return
        mob.lookControl.setLookAt(wound.impact.x, wound.impact.y + 1.0, wound.impact.z)
        if ((wound.releaseAt - now) % TELEGRAPH_EVERY == 0L) wound.skill.telegraph?.at(level, wound.impact)
        if (now >= wound.releaseAt) {
            channel = null
            release(wound.skill, wound.target, wound.impact)
        }
    }

    fun release(skill: CreatureSkill, target: LivingEntity?, impact: Vec3) {
        val cast = SkillCast(mob, target, impact, power)
        skill.self.forEach { it.apply(cast, mob) }
        subjectsOf(skill, target, impact).forEach { subject -> skill.target.forEach { it.apply(cast, subject) } }
    }

    private fun subjectsOf(skill: CreatureSkill, target: LivingEntity?, impact: Vec3): List<LivingEntity> {
        if (skill.target.isEmpty()) return emptyList()
        if (skill.area > 0.0) {
            val center = if (skill.aroundSelf) mob.position() else impact
            val reach = skill.area * 2
            return level.getEntitiesOfClass(LivingEntity::class.java, AABB.ofSize(center, reach, reach, reach)) {
                it.distanceToSqr(center) <= skill.area * skill.area && Creatures.isFoe(mob, it)
            }
        }
        if (target == null || !target.isAlive) return emptyList()
        // A projectile carries its own chance to miss; anything else wound up misses whoever stepped off the mark.
        val dodged = skill.windup > 0 && skill.target.none { it is Projectile } &&
            target.position().distanceToSqr(impact) > DODGE * DODGE
        return if (dodged) emptyList() else listOf(target)
    }

    private fun ready(reaction: Reaction): Boolean = now >= (readyAt[reaction] ?: Long.MIN_VALUE)

    private fun fire(reaction: Reaction, target: LivingEntity?) {
        val skill = Creatures.skill(level, reaction.skill) ?: return
        readyAt[reaction] = now + reaction.cooldown
        perform(skill)
        release(skill, target, target?.position() ?: mob.position())
    }

    /** The technique's own animation, told to whoever sees the caster. Played from its start, windup included. */
    private fun perform(skill: CreatureSkill) {
        val move = skill.animation ?: return
        ModChannels.CREATURE_MOVE.sendTo(PlayerLookup.tracking(mob), CreatureMovePayload(mob.id, move))
    }

    /** One blow with the body itself — for bodies vanilla gave no attack, and for humans. */
    fun strike(target: LivingEntity) {
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND)
        if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            mob.doHurtTarget(level, target)
        } else {
            target.hurtServer(level, level.damageSources().mobAttack(mob), spec.stats.damage * power)
        }
    }

    // ── Being hurt ───────────────────────────────────────────────────────────────

    fun onHurt(source: DamageSource, amount: Float) {
        lastHurtAt = now
        if (mob.isSleeping) mob.stopSleeping()
        val attacker = (source.entity as? LivingEntity)?.takeIf { it !== mob }
        if (attacker != null && !Creatures.areAllies(mob, attacker)) {
            threat.add(attacker.uuid, amount.toDouble())
            if (mob.distanceToSqr(attacker) < PROVOKE * PROVOKE) provokedUntil = now + PROVOKED_TICKS
            if (mob.target == null && !isOwned && !returning && temperament != Temperament.WILLLESS &&
                temperament != Temperament.TIMID && role != Role.CITIZEN && canFight(attacker) && !dreads(attacker)
            ) {
                engage(attacker)
            }
            alarm(attacker)
        }
        if (combatSince < 0) combatSince = now
        if (temperament == Temperament.TIMID || role == Role.CITIZEN) panic(attacker)

        val fraction = mob.health / mob.maxHealth
        kit?.reactions?.forEach { reaction ->
            if (reaction.trigger == Reaction.Trigger.HURT && fraction <= reaction.below && ready(reaction)) {
                fire(reaction, attacker ?: mob.target)
            }
        }
        advanceAct(fraction)
    }

    /** Word of the blow spreads: a beast's kin join in, a town's guards come running and its people scatter. */
    private fun alarm(attacker: LivingEntity) {
        val radius = if (role == Role.BEAST) PACK_RADIUS else TOWN_RADIUS
        if (role == Role.BEYONDER) return
        level.getEntitiesOfClass(Mob::class.java, mob.boundingBox.inflate(radius)) { it !== mob }
            .mapNotNull(Creatures::mindOf)
            .forEach { other ->
                when {
                    role == Role.BEAST && other.key == key && other.mob.target == null -> other.threat.add(attacker.uuid, 1.0)
                    role != Role.BEAST && other.role == Role.GUARD -> other.threat.add(attacker.uuid, GUARD_CALL)
                    role != Role.BEAST && other.role == Role.CITIZEN -> other.panic(attacker)
                }
            }
    }

    fun panic(from: LivingEntity?) {
        if (now >= panicUntil) say(spec.speech.panic)
        panicUntil = now + PANIC_TICKS
        dangerFrom = from ?: dangerFrom
    }

    /**
     * What to run from, if anything: a wound too deep, a Beyonder too strong, a monster at a
     * citizen's door. `null` — stand and do whatever else there is to do.
     */
    fun shouldFlee(): LivingEntity? {
        if (isOwned || returning) return null
        val nearby = level.getEntitiesOfClass(LivingEntity::class.java, mob.boundingBox.inflate(FEAR_RADIUS)) { canFight(it) }
        nearby.firstOrNull(::dreads)?.let { return it }
        if (spec.mind.fleeBelow > 0f && mob.health <= mob.maxHealth * spec.mind.fleeBelow) {
            return mob.target ?: mob.lastHurtByMob
        }
        val timid = temperament == Temperament.TIMID || role == Role.CITIZEN
        if (!timid) return null
        if (now < panicUntil) dangerFrom?.takeIf { it.isAlive && it.level() === mob.level() }?.let { return it }
        return nearby.firstOrNull { (it is Enemy && !Creatures.isCreature(it)) || Creatures.mindOf(it)?.isHostileBeast == true }
            ?.takeIf { it.distanceToSqr(mob) <= DANGER_NOTICE * DANGER_NOTICE }
    }

    // ── Bosses ───────────────────────────────────────────────────────────────────

    private fun advanceAct(fraction: Float) {
        val acts = acts ?: return
        val boss = spec.boss ?: return
        val next = acts.at(fraction, act)
        if (next == act) return
        act = next
        val phase = boss.phases[next - 1]
        channel = null
        pausedUntil = now + phase.pause
        phase.kit?.let(::equip)
        mob.addEffect(MobEffectInstance(MobEffects.GLOWING, phase.pause, 0, false, false))
        level.sendParticles(ParticleTypes.SONIC_BOOM, mob.x, mob.y + mob.bbHeight / 2, mob.z, 1, 0.0, 0.0, 0.0, 0.0)
        level.playSound(null, mob.x, mob.y, mob.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.5f, 0.8f)
        phase.enter.forEach { key -> Creatures.skill(level, key)?.let { release(it, mob.target, mob.target?.position() ?: mob.position()) } }
        say(spec.speech.fight)
    }

    /** The fight is abandoned: everything it learnt in it is forgotten, and it is whole again. */
    fun reset() {
        threat.clear()
        engage(null)
        channel = null
        act = 0
        combatSince = -1
        if (enraged) {
            enraged = false
            mob.revokeAttribute(Attributes.ATTACK_DAMAGE, ENRAGE_ID)
        }
        equip(spec.kit ?: Creatures.kitFor(level, pathway, sequence))
        mob.health = mob.maxHealth
    }

    private fun bossTick() {
        val boss = spec.boss ?: return
        val bar = bar ?: ServerBossEvent(mob.uuid, mob.displayName, boss.bar, BossEvent.BossBarOverlay.NOTCHED_10).also { bar = it }
        bar.progress = (mob.health / mob.maxHealth).coerceIn(0f, 1f)
        val watchers = level.getPlayers { it.distanceToSqr(mob) <= BAR_RANGE * BAR_RANGE }
        bar.players.filter { it !in watchers }.forEach(bar::removePlayer)
        watchers.filter { it !in bar.players }.forEach(bar::addPlayer)

        if (mob.position().distanceToSqr(anchors.homeVec) > boss.leash * boss.leash && !returning) {
            returning = true
            reset()
        }
        if (combatSince >= 0 && watchers.isEmpty() && now - lastHurtAt > ABANDON_TICKS) reset()
        if (!enraged && boss.enrageAfter > 0 && combatSince >= 0 && now - combatSince >= boss.enrageAfter) {
            enraged = true
            mob.grantAttribute(Attributes.ATTACK_DAMAGE, ENRAGE_ID, ENRAGED_POWER - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            bar.color = BossEvent.BossBarColor.RED
            level.playSound(null, mob.x, mob.y, mob.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5f, 0.6f)
        }
        if (!enraged && bar.color != boss.bar) bar.color = boss.bar
    }

    fun dispose() {
        bar?.removeAllPlayers()
        bar = null
    }

    // ── The heartbeat ────────────────────────────────────────────────────────────

    /** Every tick of the body's life, fighting or not. */
    fun heartbeat() {
        val tick = now
        val target = mob.target
        kit?.reactions?.forEach { reaction ->
            if (reaction.trigger == Reaction.Trigger.PULSE && target != null && tick % reaction.every == 0L) fire(reaction, target)
        }
        if (tick % BAR_EVERY == 0L) bossTick()
        keepOutOfTheSun()
        if (tick % SECOND != 0L) return

        val concealed = role == Role.BEYONDER && target == null
        if (!concealed && !mob.isInvisible) spec.body.aura?.at(level, mob.position())
        if (kit?.stalker == true && level.getNearestPlayer(mob, STALK_RADIUS) == null) {
            mob.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, STALK_TICKS, 0, false, false))
        }
        if (tick - lastHurtAt > CALM_TICKS) {
            threat.decay(GRUDGE_FADE)
            if (target == null) combatSince = -1
            // A Beyonder out of the fight closes their wounds; beasts and the dead do not.
            if (role == Role.BEYONDER && mob.health < mob.maxHealth) mob.heal(1f)
        }
        if (returning && mob.position().distanceToSqr(anchors.homeVec) < HOME_REACHED * HOME_REACHED) returning = false
        if (tick % DESPAWN_CHECK == 0L && !mob.isPersistenceRequired && (role == Role.BEAST || role == Role.BEYONDER) &&
            level.getNearestPlayer(mob, DESPAWN_RADIUS) == null
        ) {
            mob.discard()
        }
    }

    /**
     * A siren on a drowned's body is not undead, and a sea condor on a phantom's is a bird: the
     * body's vanilla sun-burn is put out the tick it starts, under the open daytime sky only —
     * `Retinue`'s trick for its shaded servants.
     *
     * ponytail: a flame someone else sets on such a creature under the noon sky goes out too; tell
     * the fires apart if a fire-based power ever needs to burn one at noon.
     */
    private fun keepOutOfTheSun() {
        if (!spec.body.sunProof || !mob.isOnFire || mob.isInLava || !level.isBrightOutside) return
        if (level.canSeeSky(net.minecraft.core.BlockPos.containing(mob.x, mob.eyeY, mob.z))) mob.clearFire()
    }

    // ── Words ────────────────────────────────────────────────────────────────────

    /** Says one of [lines] over its head, if anyone is near enough to read it and it has not just spoken. */
    fun say(lines: List<String>) {
        if (lines.isEmpty() || now - spokeAt < SPEECH_GAP) return
        if (level.getNearestPlayer(mob, SpeechBubbles.HEARING) == null) return
        spokeAt = now
        SpeechBubbles.say(mob, lines[mob.random.nextInt(lines.size)])
    }

    /** A word for a player who came close — [lines] by the hour — at most once a minute each. */
    fun greet(player: ServerPlayer, lines: List<String>) {
        if (now - (greeted[player.uuid] ?: Long.MIN_VALUE / 2) < GREET_GAP) return
        greeted[player.uuid] = now
        say(lines)
    }

    fun isPanicking(): Boolean = now < panicUntil

    private companion object {
        val HASTE_ID = modifierId("creature", "haste")
        val ENRAGE_ID = modifierId("creature", "enrage")
        const val HASTE_STEP = 0.2

        const val SECOND = 20L
        const val BAR_EVERY = 10L
        const val TELEGRAPH_EVERY = 4L
        const val DODGE = 2.5
        const val PROVOKE = 3.0
        const val PROVOKED_TICKS = 80L
        const val PANIC_TICKS = 200L
        const val CALM_TICKS = 200L
        const val GRUDGE_FADE = 0.9
        const val ABANDON_TICKS = 600L
        const val ENRAGED_POWER = 1.5f

        const val BEYONDER_PULL = 8.0
        const val PLAYER_PULL = 6.0
        const val LOSE_INTEREST = 1.5
        const val FEAR_RADIUS = 12.0
        const val DANGER_NOTICE = 10.0
        const val PACK_RADIUS = 12.0
        const val TOWN_RADIUS = 32.0
        const val GUARD_CALL = 50.0

        const val BAR_RANGE = 48.0
        const val HOME_REACHED = 3.0
        const val STALK_RADIUS = 4.0
        const val STALK_TICKS = 30
        const val DESPAWN_CHECK = 100L
        const val DESPAWN_RADIUS = 128.0

        const val SPEECH_GAP = 100L
        const val GREET_GAP = 1200L
    }
}
