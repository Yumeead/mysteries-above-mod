package me.vangoo.mysteries.pathways.death.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.SpiritGuideLore
import me.vangoo.domain.valueobjects.SpiritMediumLore
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.pathways.common.SoulWard
import me.vangoo.mysteries.pathways.death.DEATH_COLOR
import me.vangoo.mysteries.pathways.death.Retinue
import me.vangoo.mysteries.pathways.death.Spirits
import me.vangoo.mysteries.pathways.death.isUndead
import me.vangoo.mysteries.pathways.death.payBranch
import me.vangoo.mysteries.pathways.justiciar.rootInPlace
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import me.vangoo.mysteries.schedule.TaskHandle
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Death, Seq. 7 (Spirit Medium): **Spirit Channeling** — and, from Seq. 6, the Spirit Usher's
 * dealings with the dead.
 *
 * "They can use different Spirits to actualize different kinds of effects… a Spirit Channeling
 * done by a Spirit Medium can involve only 1 Spirit." One power, one spirit at a time.
 *
 * ## The offering chooses the spirit, not a chest menu
 *
 * The plugin opened a chest with three icons. Here the medium holds out what the spirit answers to
 * — the same move that took the Door's divination out of a menu and into the hand:
 *
 * | in the other hand | who comes | what changed against the plugin |
 * |---|---|---|
 * | eye of ender, spyglass | **Illusory Eye** | it watches for its whole duration — newcomers are outlined too — instead of one snapshot |
 * | ice, snow | **Frost Shadow** | water around really freezes (vanilla frosted ice, which melts by itself) and victims freeze by vanilla's own frost |
 * | mud, dirt, clay | **Earth Spirit** | the ground under the target really turns to mud and holds it, then turns back |
 * | bone, a skull *(Seq. 6)* | **Subjugation** | — |
 * | echo shard *(Seq. 6)* | **Spirit Swap** | the ward is a deadline in memory, not a tag that a restart would make eternal |
 * | feather, phantom membrane *(Seq. 6)* | **Soar** | — |
 *
 * Each spirit pays its own price and leaves its own cooldown ([payBranch]); the plugin charged
 * them all the harshest cooldown because its framework could not see which spirit came.
 */
class SpiritChanneling : ActiveAbility(AbilityIdentity.of("death_spirit_channeling")) {

    override val displayName: Component = Component.literal("Допомога середніх духів")

    /** The ticket covers the cheapest spirit; the rest is paid by the branch itself. */
    override val spiritualityCost: Int = SpiritGuideLore.SOAR_COST

    override fun cooldownSeconds(sequence: Sequence): Int = SpiritMediumLore.illusoryEyeCooldownSeconds(sequence)

    private enum class Spirit { EYE, FROST, EARTH, SUBJUGATION, SWAP, SOAR }

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви кличете одного духа — і кого саме, вирішує те, що тримаєте в другій руці.\n" +
            "▪ Око ендера чи підзорна труба — Ілюзорне око: усе живе на ${SpiritMediumLore.illusoryEyeRadius(sequence).toInt()} бл. " +
            "обведене ${SpiritMediumLore.illusoryEyeDurationSeconds(sequence)} с (${SpiritMediumLore.ILLUSORY_EYE_COST} духовності)\n" +
            "▪ Лід чи сніг — Морозна тінь: холод на ${SpiritMediumLore.frostRadius(sequence).toInt()} бл. ${SpiritMediumLore.frostDurationSeconds(sequence)} с, " +
            "вам — крижаний обладунок і коса ×${"%.1f".format(SpiritMediumLore.scytheDamageMultiplier(sequence))} (${SpiritMediumLore.FROST_SHADOW_COST} духовності)\n" +
            "▪ Бруд чи глина — Земляний дух: земля під ціллю стає болотом і тримає ${SpiritMediumLore.earthHoldSeconds(sequence)} с " +
            "(${SpiritMediumLore.EARTH_SPIRIT_COST} духовності і ${SpiritMediumLore.EARTH_SPIRIT_BLOOD_HP / 2} ❤ вашої крові)" +
            if (sequence.level > USHER) "" else "\n" +
                "▪ Кістка чи череп — Підкорення: безхазяйна нежить на ${SpiritGuideLore.subjugationRadius(sequence).toInt()} бл. стає вашими слугами (${SpiritGuideLore.SUBJUGATION_COST})\n" +
                "▪ Луна-уламок — Обмін духом: слуга гине, а вашу душу ${SpiritGuideLore.spiritSwapWardSeconds(sequence)} с не дістати (${SpiritGuideLore.SPIRIT_SWAP_COST})\n" +
                "▪ Перо чи мембрана фантома — Зліт: невидима сила підкидає вас (${SpiritGuideLore.SOAR_COST})",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val spirit = offeringOf(caster.offhandItem, context.sequence) ?: offeringOf(caster.mainHandItem, context.sequence)
            ?: return AbilityOutcome.failed(
                "Духові нема на що відгукнутись — тримайте в другій руці око ендера, лід або бруд" +
                    if (context.sequence.level <= USHER) ", кістку, луна-уламок чи перо" else "",
            )
        return when (spirit) {
            Spirit.EYE -> illusoryEye(context)
            Spirit.FROST -> frostShadow(context)
            Spirit.EARTH -> earthSpirit(context)
            Spirit.SUBJUGATION -> subjugate(context)
            Spirit.SWAP -> spiritSwap(context)
            Spirit.SOAR -> soar(context)
        }
    }

    private fun offeringOf(stack: ItemStack, sequence: Sequence): Spirit? {
        val item = stack.item
        val usher = sequence.level <= USHER
        return when {
            item in EYE_OFFERINGS -> Spirit.EYE
            item in FROST_OFFERINGS -> Spirit.FROST
            item in EARTH_OFFERINGS -> Spirit.EARTH
            usher && item in BONE_OFFERINGS -> Spirit.SUBJUGATION
            usher && item == Items.ECHO_SHARD -> Spirit.SWAP
            usher && item in WING_OFFERINGS -> Spirit.SOAR
            else -> null
        }
    }

    private fun short(price: Int) = AbilityOutcome.failed("Недостатньо духовності, щоб покликати духа: потрібно $price")

    // ── Illusory Eye ────────────────────────────────────────────────────────────

    /**
     * "A Spirit World creature with a nearly transparent eye, looking down from above without
     * blinking." The eye hangs above the medium for the whole duration and keeps looking: anything
     * living that walks into its sight is outlined as well.
     */
    private fun illusoryEye(context: CastContext): AbilityOutcome {
        val sequence = context.sequence
        if (!context.payBranch(SpiritMediumLore.ILLUSORY_EYE_COST, SpiritMediumLore.illusoryEyeCooldownSeconds(sequence))) {
            return short(SpiritMediumLore.ILLUSORY_EYE_COST)
        }
        val caster = context.caster
        val radius = SpiritMediumLore.illusoryEyeRadius(sequence)
        val total = SpiritMediumLore.illusoryEyeDurationSeconds(sequence) * TICKS_PER_SECOND
        val iris = ModParticles.glyph(DEATH_COLOR.shade(0.35))
        val pupil = ModParticles.glyph(DEATH_COLOR.shade(-0.45))
        var elapsed = 0
        lateinit var handle: TaskHandle
        handle = context.scheduling.every(EYE_PERIOD, delayTicks = 0) {
            if (elapsed >= total || caster.isRemoved) return@every handle.cancel()
            val level = caster.level()
            val centre = caster.position().add(0.0, EYE_HEIGHT, 0.0)
            repeat(IRIS_POINTS) { i ->
                val angle = 2 * PI * i / IRIS_POINTS
                level.sendParticles(iris, centre.x + cos(angle) * IRIS, centre.y, centre.z + sin(angle) * IRIS, 1, 0.0, 0.0, 0.0, 0.0)
            }
            level.sendParticles(pupil, centre.x, centre.y, centre.z, 3, 0.15, 0.0, 0.15, 0.0)
            if (elapsed % TICKS_PER_SECOND == 0) {
                level.getEntitiesOfClass(LivingEntity::class.java, caster.boundingBox.inflate(radius)) { it !== caster && it.isAlive }
                    .forEach { it.addEffect(MobEffectInstance(MobEffects.GLOWING, TICKS_PER_SECOND + 10, 0, true, false, false)) }
            }
            elapsed += EYE_PERIOD
        }
        context.level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.5f)
        return AbilityOutcome.done("👁 Око-споглядач розплющилось над вами")
    }

    // ── Frost Shadow ────────────────────────────────────────────────────────────

    /**
     * "A frozen domain within 10 to 20 meters, armor-like ice and a colossal crystalline frost
     * scythe." The domain walks with the medium. Its cold is vanilla's own: water under it turns to
     * frosted ice — the very block Frost Walker lays, which melts back by itself — and its victims
     * freeze the way powder snow freezes them, frost vignette and all.
     */
    private fun frostShadow(context: CastContext): AbilityOutcome {
        val sequence = context.sequence
        if (!context.payBranch(SpiritMediumLore.FROST_SHADOW_COST, SpiritMediumLore.frostCooldownSeconds(sequence))) {
            return short(SpiritMediumLore.FROST_SHADOW_COST)
        }
        val caster = context.caster
        val casterId = caster.uuid
        val radius = SpiritMediumLore.frostRadius(sequence)
        val tickDamage = SpiritMediumLore.frostTickDamage(sequence).toFloat()
        val scythe = SpiritMediumLore.scytheDamageMultiplier(sequence)
        val total = SpiritMediumLore.frostDurationSeconds(sequence) * TICKS_PER_SECOND

        // The scythe: the medium's own melee blows land harder while the shadow stands with them.
        context.events.on<IncomingDamage>(total) { damage ->
            if (damage.attacker?.uuid != casterId || damage.directCause?.uuid != casterId) return@on
            damage.scale(scythe)
            val level = damage.victim.level() as ServerLevel
            level.sendParticles(ParticleTypes.SNOWFLAKE, damage.victim.x, damage.victim.y + 1.0, damage.victim.z, 14, 0.6, 0.4, 0.6, 0.05)
            level.playSound(null, damage.victim.x, damage.victim.y, damage.victim.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7f, 0.7f)
        }

        var elapsed = 0
        lateinit var handle: TaskHandle
        handle = context.scheduling.every(TICKS_PER_SECOND, delayTicks = 0) {
            if (elapsed >= total || caster.isRemoved || !caster.isAlive) {
                handle.cancel()
                caster.level().playSound(null, caster.x, caster.y, caster.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 1.4f)
                return@every
            }
            frostTick(caster, radius, tickDamage)
            elapsed += TICKS_PER_SECOND
        }
        context.level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.0f, 0.5f)
        return AbilityOutcome.done("❄ Морозна тінь стала поруч: холод, обладунок і коса")
    }

    private fun frostTick(caster: ServerPlayer, radius: Double, tickDamage: Float) {
        val level = caster.level()
        caster.addEffect(MobEffectInstance(MobEffects.RESISTANCE, ARMOUR_TICKS, 1, false, false, true))
        caster.addEffect(MobEffectInstance(MobEffects.ABSORPTION, ARMOUR_TICKS, 1, false, false, true))

        val reach = radius * radius
        level.getEntitiesOfClass(LivingEntity::class.java, caster.boundingBox.inflate(radius)) {
            it !== caster && it.isAlive && !Spirits.isSpirit(it) && !Retinue.isOwnedBy(caster.uuid, it) && it.distanceToSqr(caster) <= reach
        }.forEach { victim ->
            victim.ticksFrozen = victim.ticksRequiredToFreeze + FREEZE_SURPLUS
            victim.addEffect(MobEffectInstance(MobEffects.SLOWNESS, ARMOUR_TICKS, 1, false, true, true))
            victim.hurtServer(level, caster.damageSources().freeze(), tickDamage)
        }

        // Frost Walker's own move: still water under the domain becomes frosted ice that melts back.
        val centre = caster.blockPosition().below()
        val r = radius.toInt()
        val cursor = BlockPos.MutableBlockPos()
        for (dx in -r..r) for (dz in -r..r) {
            if (dx * dx + dz * dz > r * r) continue
            cursor.set(centre.x + dx, centre.y, centre.z + dz)
            if (level.getBlockState(cursor) != WATER || !level.getBlockState(cursor.above()).isAir) continue
            level.setBlockAndUpdate(cursor, Blocks.FROSTED_ICE.defaultBlockState())
            level.scheduleTick(cursor.immutable(), Blocks.FROSTED_ICE, 60 + caster.random.nextInt(60))
        }

        repeat(EDGE_FLAKES) {
            val angle = caster.random.nextDouble() * 2 * PI
            level.sendParticles(ParticleTypes.SNOWFLAKE, caster.x + cos(angle) * radius, caster.y + 0.3, caster.z + sin(angle) * radius, 1, 0.0, 0.2, 0.0, 0.0)
        }
    }

    // ── Earth Spirit ────────────────────────────────────────────────────────────

    /**
     * "A Natural Spirit with weathered, stone-like hands that softens the ground like a swamp and
     * drags a target underground — it needs their blood to be Channeled." The plugin's own note
     * called its version visual only. Here the ground under the target really turns to mud and
     * holds them, then turns back into what it was — only natural ground, never someone's floor.
     */
    private fun earthSpirit(context: CastContext): AbilityOutcome {
        val sequence = context.sequence
        // The target first: nobody pays — in spirituality or in blood — to grab at empty air.
        val target = context.targeting.lookingAt(SpiritMediumLore.earthRange(sequence))
            ?: return AbilityOutcome.failed("Земляний дух не бачить, кого хапати — дивіться на ціль")
        if (!context.payBranch(SpiritMediumLore.EARTH_SPIRIT_COST, SpiritMediumLore.earthCooldownSeconds(sequence))) {
            return short(SpiritMediumLore.EARTH_SPIRIT_COST)
        }
        val caster = context.caster
        caster.health = (caster.health - SpiritMediumLore.EARTH_SPIRIT_BLOOD_HP).coerceAtLeast(1f)

        val level = context.level
        val hold = SpiritMediumLore.earthHoldSeconds(sequence) * TICKS_PER_SECOND
        val softened = soften(level, target.blockPosition().below())
        context.scheduling.after(hold) {
            softened.forEach { (pos, was) -> if (level.getBlockState(pos).`is`(Blocks.MUD)) level.setBlockAndUpdate(pos, was) }
        }
        context.rootInPlace(target, hold, boundaryParticle = ParticleTypes.MYCELIUM)
        target.addEffect(MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_TICKS, 0))

        val swamp = SpiritMediumLore.EARTH_SWAMP_RADIUS
        level.getEntitiesOfClass(LivingEntity::class.java, target.boundingBox.inflate(swamp)) {
            it !== caster && it !== target && !Spirits.isSpirit(it)
        }.forEach { it.addEffect(MobEffectInstance(MobEffects.SLOWNESS, hold, 1)) }

        level.sendParticles(ParticleTypes.SCULK_SOUL, target.x, target.y + 0.1, target.z, 10, 0.4, 0.1, 0.4, 0.01)
        level.playSound(null, target.x, target.y, target.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f)
        level.playSound(null, target.x, target.y, target.z, SoundEvents.MUD_PLACE, SoundSource.PLAYERS, 1.0f, 0.5f)
        context.messaging.tell(target.uuid, Component.literal("Земля розм'якла під ногами й зімкнулась — ви не можете рушити!"))
        return AbilityOutcome.done("🖐 Земляний дух схопив ${target.name.string} на ${hold / TICKS_PER_SECOND} с")
    }

    /** Natural ground under and around [centre] becomes mud; returns what it was. */
    private fun soften(level: ServerLevel, centre: BlockPos): List<Pair<BlockPos, BlockState>> {
        val changed = mutableListOf<Pair<BlockPos, BlockState>>()
        for (dx in -1..1) for (dz in -1..1) {
            val pos = centre.offset(dx, 0, dz)
            val state = level.getBlockState(pos)
            val natural = state.`is`(BlockTags.DIRT) || state.`is`(BlockTags.SAND) || state.`is`(Blocks.GRAVEL)
            if (!natural || level.getBlockState(pos.above()).blocksMotion()) continue
            changed += pos to state
            level.setBlockAndUpdate(pos, Blocks.MUD.defaultBlockState())
        }
        return changed
    }

    // ── Seq. 6: the retinue ─────────────────────────────────────────────────────

    /** All the ownerless undead around bow their heads. Bosses never kneel. */
    private fun subjugate(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val sequence = context.sequence
        val room = Retinue.room(caster, sequence)
        if (room == 0) return AbilityOutcome.failed("Почет уже повний")
        val radius = SpiritGuideLore.subjugationRadius(sequence)
        val candidates = context.level.getEntitiesOfClass(Mob::class.java, caster.boundingBox.inflate(radius)) {
            it.isAlive && it.isUndead() && !Retinue.isServant(it) && it.type != EntityTypes.WITHER
        }.take(room)
        if (candidates.isEmpty()) return AbilityOutcome.failed("Довкола немає безхазяйної нежиті")
        if (!context.payBranch(SpiritGuideLore.SUBJUGATION_COST, SpiritGuideLore.subjugationCooldownSeconds(sequence))) {
            return short(SpiritGuideLore.SUBJUGATION_COST)
        }
        candidates.forEach { Retinue.enroll(caster, it) }
        context.level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.SOUL_SAND_BREAK, SoundSource.PLAYERS, 1.0f, 0.5f)
        return AbilityOutcome.done("☠ Нежить схилилась: ${candidates.size} (${Retinue.size(caster)}/${Retinue.cap(sequence)})")
    }

    /**
     * "Effectively immune to attacks that target the Soul Body": the Usher trades places with a
     * servant, the servant crumbles, and for a while there is no soul where the Usher stands.
     */
    private fun spiritSwap(context: CastContext): AbilityOutcome {
        val caster = context.caster
        if (Retinue.of(caster.uuid).isEmpty()) return AbilityOutcome.failed("Мінятись нема з ким — поруч жодного слуги")
        val sequence = context.sequence
        // Spirituality before the servant: otherwise the servant dies and no ward rises.
        if (!context.payBranch(SpiritGuideLore.SPIRIT_SWAP_COST, SpiritGuideLore.spiritSwapCooldownSeconds(sequence))) {
            return short(SpiritGuideLore.SPIRIT_SWAP_COST)
        }
        Retinue.sacrificeOne(caster.uuid)
        val ticks = SpiritGuideLore.spiritSwapWardSeconds(sequence) * TICKS_PER_SECOND
        SoulWard.protect(caster.uuid, context.scheduling.tick + ticks)
        context.effects.bodyAura(caster, ticks, counterFlow = ParticleTypes.SCULK_SOUL)
        context.level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0f, 0.5f)
        return AbilityOutcome.done("✦ Ви помінялись духом зі слугою: ${ticks / TICKS_PER_SECOND} с душа недосяжна")
    }

    /** "Swiftly soar, as if dragged by an unseen force" — and the same force does not let them fall. */
    private fun soar(context: CastContext): AbilityOutcome {
        if (!context.payBranch(SpiritGuideLore.SOAR_COST, SpiritGuideLore.SOAR_COOLDOWN_SECONDS)) return short(SpiritGuideLore.SOAR_COST)
        val caster = context.caster
        val look = context.look
        val flat = net.minecraft.world.phys.Vec3(look.x, 0.0, look.z).normalize()
        caster.deltaMovement = flat.scale(SOAR_FORWARD).add(0.0, SOAR_UPWARD, 0.0)
        caster.hurtMarked = true
        caster.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, SOAR_SLOW_FALL_TICKS, 0, false, false, true))
        context.level.sendParticles(ParticleTypes.SCULK_SOUL, caster.x, caster.y + 0.5, caster.z, 20, 0.4, 0.8, 0.4, 0.05)
        context.level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.0f, 0.6f)
        return AbilityOutcome.done("✦ Невидима сила підкинула вас угору")
    }

    private companion object {
        const val USHER = 6

        val EYE_OFFERINGS: Set<Item> = setOf(Items.ENDER_EYE, Items.SPYGLASS)
        val FROST_OFFERINGS: Set<Item> = setOf(
            Items.ICE, Items.PACKED_ICE, Items.BLUE_ICE, Items.SNOWBALL, Items.SNOW_BLOCK, Items.POWDER_SNOW_BUCKET,
        )
        val EARTH_OFFERINGS: Set<Item> = setOf(
            Items.MUD, Items.DIRT, Items.COARSE_DIRT, Items.ROOTED_DIRT, Items.CLAY_BALL, Items.CLAY, Items.PACKED_MUD,
        )
        val BONE_OFFERINGS: Set<Item> = setOf(Items.BONE, Items.BONE_BLOCK, Items.SKELETON_SKULL, Items.ZOMBIE_HEAD, Items.WITHER_SKELETON_SKULL)
        val WING_OFFERINGS: Set<Item> = setOf(Items.FEATHER, Items.PHANTOM_MEMBRANE)

        val WATER: BlockState = Blocks.WATER.defaultBlockState()

        const val EYE_PERIOD = 4
        const val EYE_HEIGHT = 12.0
        const val IRIS = 1.6
        const val IRIS_POINTS = 18

        const val ARMOUR_TICKS = 45
        const val FREEZE_SURPLUS = 60
        const val EDGE_FLAKES = 10

        const val BLINDNESS_TICKS = 30

        const val SOAR_FORWARD = 0.9
        const val SOAR_UPWARD = 1.3
        const val SOAR_SLOW_FALL_TICKS = 160
    }
}
