package me.vangoo.mysteries.pathways.death.abilities

import com.mojang.serialization.Codec
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.GatekeeperLore
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.creature.Creatures
import me.vangoo.mysteries.ability.grantAttribute
import me.vangoo.mysteries.ability.modifierId
import me.vangoo.mysteries.ability.revokeAttribute
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.pathways.death.DEATH_COLOR
import me.vangoo.mysteries.pathways.death.Retinue
import me.vangoo.mysteries.pathways.death.Spirits
import me.vangoo.mysteries.pathways.death.isUndead
import me.vangoo.mysteries.pathways.death.payBranch
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.EntityTypeTags
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Death, Seq. 5 (Gatekeeper): **Internal Underworld**.
 *
 * The Gatekeeper's body is a cage for one of the dead. Crouch + cast at one's own servant (or at
 * any spirit) up close — it is taken inside. Crouch + cast again — it walks back out. A plain cast
 * calls the power of whoever lives inside. The price is the body: while someone is inside, the
 * Gatekeeper's health is eroded, their sanity slowly drains, and withering no longer takes them.
 *
 * ## The occupant's nature is vanilla's
 *
 * The plugin's occupants were mostly its Spirit World creatures — MythicMobs monsters the mod does
 * not have until phase 12's creature engine. What the mod's world does have is a whole family of
 * the dead, so the power comes from **what the occupant was**:
 *
 * | taken inside | power |
 * |---|---|
 * | a spirit (vex) | scouting: everything alive within 60 bl. outlined |
 * | a zombie and its kin | a burst of dead flesh around the Gatekeeper |
 * | a drowned | four waves of dead water rolling onto the target |
 * | a skeleton, stray, bogged | a volley of real arrows fanned out along the gaze |
 * | a wither skeleton | a ghostly hand at range: damage, withering, slowness, blindness |
 * | a phantom | a step into the shadow behind the target |
 * | a zombie villager | passive: once every five minutes, cheats death — it came back once itself |
 *
 * The Spirit World's own beings (phase 12) are read by **who** they are, not by the body the engine
 * lent them: the Living Shadow steps through shadows though it flies on a vex's wings, the Death
 * Knight looses a volley, the Envoy reaches with its illusory arms, the Lake Goddess and the Sea
 * Beasts roll their waves, the Pus of Man cheats death, the Shadow-Swallowing Python drags hiding
 * things into the light. Released, a being walks out as itself — [INNER_CREATURE] remembers which.
 *
 * ## Nothing here is lost on a restart
 *
 * Who lives inside is a persistent attachment on the player; the erosion is a transient attribute
 * modifier re-applied on every join and respawn. The plugin kept the occupant in memory and wrote
 * the erosion into the base health — a restart lost the occupant and kept the wound for good.
 */
class InternalUnderworld : ActiveAbility(AbilityIdentity.of("death_internal_underworld")) {

    override val displayName: Component = Component.literal("Внутрішній Загробний Світ")

    /** The ticket covers opening the inner gate for the cheapest occupant; dearer ones pay the rest. */
    override val spiritualityCost: Int = GatekeeperLore.UNDERWORLD_CAST_BASE_COST + Occupant.entries.filter { it.castCost > 0 }.minOf { it.castCost }

    override fun cooldownSeconds(sequence: Sequence): Int = GatekeeperLore.underworldCastCooldownSeconds(sequence)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ваше тіло — клітка для одного мерця чи духа. Сила залежить від того, ким він був.\n" +
            "▪ Присід + застосування на свого слугу чи духа (${ABSORB_RANGE.toInt()} бл.) — поглинути; ще раз — випустити\n" +
            "▪ Застосування — сила того, хто всередині (${GatekeeperLore.UNDERWORLD_CAST_BASE_COST}+ духовності)\n" +
            "▪ Поки хтось усередині: менше здоров'я, повільна втрата глузду, Тління на вас не діє",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val inside = occupantOf(caster)
        if (caster.isShiftKeyDown) return if (inside != null) release(context, caster, inside) else absorb(context, caster)
        inside ?: return AbilityOutcome.failed("Усередині нікого немає — поглиньте слугу (присід + застосування)")
        val occupant = Occupant.of(inside, caster.getAttached(INNER_CREATURE))
        if (occupant.castCost <= 0) return AbilityOutcome.failed("${occupant.title} діє сам собою — кликати нема чого")
        // A target first: nobody pays to aim at empty air.
        if (occupant.aim > 0 && context.targeting.lookingAt(occupant.aim) == null) return AbilityOutcome.failed("Немає цілі — гляньте на когось")
        val price = GatekeeperLore.UNDERWORLD_CAST_BASE_COST + occupant.castCost
        if (!context.payBranch(price, cooldownSeconds(context.sequence))) return AbilityOutcome.failed("Недостатньо духовності: потрібно $price")
        return occupant.power(context)
    }

    // ── Taking in, letting out ──────────────────────────────────────────────────

    private fun absorb(context: CastContext, caster: ServerPlayer): AbilityOutcome {
        val target = context.targeting.lookingAt(ABSORB_RANGE) as? Mob
            ?: return AbilityOutcome.failed("Поглинути нікого: гляньте впритул на свого слугу чи духа")
        val takeable = Spirits.isSpirit(target) || Retinue.isOwnedBy(caster.uuid, target)
        if (!takeable || !target.isDeadSoul()) return AbilityOutcome.failed("Ця істота не годиться: лише ваш мертвий слуга або дух")
        val type = target.type
        val creature = Creatures.keyOf(target)?.identifier()
        if (Retinue.isServant(target)) Retinue.absorb(caster.uuid, target) else target.discard()

        caster.setAttached(OCCUPANT, BuiltInRegistries.ENTITY_TYPE.getKey(type))
        caster.setAttached(INNER_CREATURE, creature)
        erode(caster)
        val level = context.level
        level.sendParticles(ParticleTypes.SQUID_INK, caster.x, caster.y + 1.0, caster.z, 20, 0.4, 0.8, 0.4, 0.02)
        level.sendParticles(ParticleTypes.SCULK_SOUL, target.x, target.y + 0.5, target.z, 14, 0.3, 0.5, 0.3, 0.05)
        level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 0.8f, 0.7f)
        val occupant = Occupant.of(type, creature)
        return AbilityOutcome.Awaiting(Component.literal("☠ ${occupant.title} оселився у вас: ${occupant.summary}"))
    }

    /** Out it walks — the same creature, back in the retinue if there is room for it. */
    private fun release(context: CastContext, caster: ServerPlayer, inside: EntityType<*>): AbilityOutcome {
        val nature = Occupant.of(inside, caster.getAttached(INNER_CREATURE))
        val creature = caster.removeAttached(INNER_CREATURE)
        caster.removeAttached(OCCUPANT)
        erode(caster)
        val level = context.level
        val at = caster.position().add(context.look.x * 1.5, 0.0, context.look.z * 1.5)
        // A being of the engine walks out as itself, not as the vanilla body it was lent.
        val mob = creature?.let { Creatures.spawn(level, Creatures.creatureKey(it), at, EntitySpawnReason.MOB_SUMMONED) }
            ?: (inside.create(level, EntitySpawnReason.MOB_SUMMONED) as? Mob)?.also {
                it.snapTo(at.x, at.y, at.z, caster.yRot, 0f)
                level.addFreshEntity(it)
            }
        if (mob != null) {
            // A plain spirit simply disperses; one of the dead or a Spirit World being comes back to heel, if there is room.
            val disperses = Spirits.isSpirit(mob) && !Spirits.isSpiritWorldBeing(mob)
            if (!disperses && Retinue.room(caster, context.sequence) > 0) Retinue.enroll(caster, mob) else mob.discard()
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, caster.x, caster.y + 1.0, caster.z, 16, 0.4, 0.6, 0.4, 0.03)
        level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.ALLAY_DEATH, SoundSource.PLAYERS, 0.6f, 1.3f)
        return AbilityOutcome.Awaiting(Component.literal("☠ ${nature.title} покинув ваше тіло"))
    }

    private fun LivingEntity.isDeadSoul(): Boolean = isUndead() || Spirits.isSpirit(this) || Spirits.isSpiritWorldBeing(this)

    // ── The occupants ───────────────────────────────────────────────────────────

    /** [aim] — the range a targeted power needs a creature in sight at; `0` — it needs none. */
    private enum class Occupant(val title: String, val summary: String, val castCost: Int, val erosionHp: Double, val aim: Double = 0.0) {
        SPIRIT("Дух", "розвідка — усе живе довкола обведене", GatekeeperLore.WANDERING_SPIRIT_CAST_COST, GatekeeperLore.WANDERING_SPIRIT_EROSION_HP),
        CORPSE("Мрець", "вибух мертвої плоті довкола вас", GatekeeperLore.RESURRECTED_SERVANT_CAST_COST, GatekeeperLore.RESURRECTED_SERVANT_EROSION_HP),
        DROWNED("Утоплений", "хвилі мертвої води на ціль", GatekeeperLore.SEA_BEASTS_CAST_COST, GatekeeperLore.SEA_BEASTS_EROSION_HP, SEA_RANGE),
        ARCHER("Кістяк", "залп стріл уздовж погляду", GatekeeperLore.DEATH_KNIGHT_CAST_COST, GatekeeperLore.DEATH_KNIGHT_EROSION_HP),
        WITHER_SKELETON("Кістяк-візер", "примарна рука здалеку", GatekeeperLore.DEATH_ENVOY_CAST_COST, GatekeeperLore.DEATH_ENVOY_EROSION_HP, ENVOY_RANGE),
        PHANTOM("Фантом", "крок у тінь за спину цілі", GatekeeperLore.LIVING_SHADOW_CAST_COST, GatekeeperLore.LIVING_SHADOW_EROSION_HP, SHADOW_RANGE),
        ZOMBIE_VILLAGER("Зомбі-житель", "раз на 5 хв рятує від смерті", GatekeeperLore.PUS_OF_MAN_CAST_COST, GatekeeperLore.PUS_OF_MAN_EROSION_HP);

        fun power(context: CastContext): AbilityOutcome = when (this) {
            SPIRIT -> scout(context)
            CORPSE -> burst(context)
            DROWNED -> waves(context)
            ARCHER -> volley(context)
            WITHER_SKELETON -> envoy(context)
            PHANTOM -> shadowStep(context)
            ZOMBIE_VILLAGER -> AbilityOutcome.failed("Діє сам собою")
        }

        companion object {
            /** A Spirit World being by its own name; anything else by its vanilla body. */
            fun of(type: EntityType<*>, creature: Identifier?): Occupant = when (creature?.path) {
                "living_shadow" -> PHANTOM
                "death_knight" -> ARCHER
                "death_envoy" -> WITHER_SKELETON
                "lake_goddess", "sea_beasts", "tyrant_siren_5" -> DROWNED
                "tyrant_sea_condor_6" -> PHANTOM
                "pus_of_man" -> ZOMBIE_VILLAGER
                "shadow_swallowing_python" -> SPIRIT
                else -> of(type)
            }

            fun of(type: EntityType<*>): Occupant = when {
                type == EntityTypes.VEX -> SPIRIT
                type == EntityTypes.DROWNED -> DROWNED
                type == EntityTypes.WITHER_SKELETON -> WITHER_SKELETON
                type == EntityTypes.PHANTOM -> PHANTOM
                type == EntityTypes.ZOMBIE_VILLAGER -> ZOMBIE_VILLAGER
                type.builtInRegistryHolder().`is`(EntityTypeTags.SKELETONS) -> ARCHER
                else -> CORPSE
            }
        }
    }

    companion object {

        private val OCCUPANT: AttachmentType<Identifier> by lazy {
            AttachmentRegistry.create(modId("inner_occupant")) { it.persistent(Identifier.CODEC) }
        }

        /** The creature of the engine inside, if it was one — so that it walks back out as itself. */
        private val INNER_CREATURE: AttachmentType<Identifier> by lazy {
            AttachmentRegistry.create(modId("inner_creature")) { it.persistent(Identifier.CODEC) }
        }

        private val EROSION: Identifier = modifierId("death_internal_underworld", "erosion")

        /** When a zombie villager inside may cheat death again — the mod's tick. */
        private val wardReadyAt = HashMap<java.util.UUID, Long>()

        private const val ABSORB_RANGE = 4.0

        private fun occupantOf(player: ServerPlayer): EntityType<*>? =
            player.getAttached(OCCUPANT)?.let { BuiltInRegistries.ENTITY_TYPE.getOptional(it).orElse(null) }

        /** The erosion follows whoever is inside — set, changed or removed in one place. */
        private fun erode(player: ServerPlayer) {
            val inside = occupantOf(player)
            if (inside == null) {
                player.revokeAttribute(Attributes.MAX_HEALTH, EROSION)
            } else {
                player.grantAttribute(Attributes.MAX_HEALTH, EROSION, -Occupant.of(inside, player.getAttached(INNER_CREATURE)).erosionHp)
            }
            if (player.health > player.maxHealth) player.health = player.maxHealth
        }

        /**
         * The occupant lives in the body, not in a session, so its marks hang on the body's own
         * hooks: erosion on every join and respawn, withering refused, a zombie villager's second
         * chance, the slow drain of sanity.
         */
        fun install() {
            // Attachment types register lazily — at mod init, here, never in a unit test that has no Fabric mixins.
            OCCUPANT
            INNER_CREATURE
            ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> erode(handler.player) }
            // The occupant does not survive the body's death: the attachment stays with the corpse.
            ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ -> erode(newPlayer) }

            ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, _ ->
                !(entity is ServerPlayer && occupantOf(entity) != null && source.`is`(DamageTypes.WITHER))
            }
            ServerLivingEntityEvents.ALLOW_DEATH.register { entity, _, _ ->
                val inside = (entity as? ServerPlayer)?.let(::occupantOf) ?: return@register true
                if (Occupant.of(inside, entity.getAttached(INNER_CREATURE)) != Occupant.ZOMBIE_VILLAGER) return@register true
                val now = AbilityServices.scheduler.tick
                if (now < (wardReadyAt[entity.uuid] ?: 0L)) return@register true
                wardReadyAt[entity.uuid] = now + GatekeeperLore.PUS_OF_MAN_WARD_COOLDOWN_SECONDS * TICKS_PER_SECOND
                entity.health = 1f
                entity.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, GatekeeperLore.PUS_OF_MAN_INVISIBILITY_SECONDS * TICKS_PER_SECOND, 0))
                entity.addEffect(MobEffectInstance(MobEffects.WEAKNESS, GatekeeperLore.PUS_OF_MAN_WEAKNESS_SECONDS * TICKS_PER_SECOND, 0))
                entity.level().playSound(null, entity.x, entity.y, entity.z, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 1.0f, 0.6f)
                AbilityServices.messaging.tell(entity, Component.literal("☠ Мрець усередині не відпустив вас на той світ"))
                false
            }
            ServerTickEvents.END_SERVER_TICK.register { server ->
                if (server.tickCount % (GatekeeperLore.UNDERWORLD_SANITY_DRAIN_PERIOD_SECONDS * TICKS_PER_SECOND) != 0) return@register
                server.playerList.players.filter { occupantOf(it) != null }.forEach { player ->
                    AbilityServices.beyonders.of(player.uuid)?.increaseSanityLoss(GatekeeperLore.UNDERWORLD_SANITY_DRAIN_AMOUNT)
                }
            }
        }

        // ── Powers ──────────────────────────────────────────────────────────────

        private fun scout(context: CastContext): AbilityOutcome {
            context.targeting.livingNear(SCOUT_RADIUS).forEach {
                it.addEffect(MobEffectInstance(MobEffects.GLOWING, SCOUT_TICKS, 0, true, false, false))
            }
            context.level.playSound(null, context.caster.x, context.caster.y, context.caster.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.6f, 1.2f)
            return AbilityOutcome.done("☠ Дух розлетівся довкола й повернувся з новинами")
        }

        private fun burst(context: CastContext): AbilityOutcome {
            val caster = context.caster
            val level = context.level
            for (victim in context.targeting.livingNear(BURST_RADIUS)) {
                if (Retinue.isOwnedBy(caster.uuid, victim)) continue
                victim.hurtServer(level, level.damageSources().indirectMagic(caster, caster), BURST_DAMAGE)
                val away = victim.position().subtract(caster.position()).normalize()
                victim.push(away.x * 0.6, 0.3, away.z * 0.6)
                victim.hurtMarked = true
            }
            level.sendParticles(ModParticles.mote(DEATH_COLOR), caster.x, caster.y + 1.0, caster.z, 40, BURST_RADIUS / 2, 0.6, BURST_RADIUS / 2, 0.02)
            level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.ZOMBIE_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.5f)
            return AbilityOutcome.done("☠ Мертва плоть вибухнула довкола")
        }

        private fun waves(context: CastContext): AbilityOutcome {
            val caster = context.caster
            val target = context.targeting.lookingAt(SEA_RANGE) ?: return refundless("Немає цілі для хвиль")
            val level = context.level
            val from = caster.position()
            val toward = target.position().subtract(from)
            val direction = Vec3(toward.x, 0.0, toward.z).normalize()
            val lateral = Vec3(-direction.z, 0.0, direction.x)
            val distance = toward.length()
            for (lane in 0 until SEA_LANES) {
                val offset = (lane - (SEA_LANES - 1) / 2.0) * 0.7
                var d = 0.0
                while (d < distance) {
                    val p = from.add(direction.scale(d)).add(lateral.scale(offset))
                    level.sendParticles(ParticleTypes.SPLASH, p.x, p.y + 0.3, p.z, 3, 0.1, 0.1, 0.1, 0.0)
                    d += 0.6
                }
            }
            target.hurtServer(level, level.damageSources().indirectMagic(caster, caster), SEA_DAMAGE)
            target.push(direction.x * 1.2, 0.4, direction.z * 1.2)
            target.hurtMarked = true
            level.playSound(null, target.x, target.y, target.z, SoundEvents.DROWNED_HURT, SoundSource.PLAYERS, 1.0f, 0.7f)
            return AbilityOutcome.done("☠ Мертва вода накрила ${target.name.string}")
        }

        private fun volley(context: CastContext): AbilityOutcome {
            val caster = context.caster
            val level = context.level
            val look = context.look
            for (i in 0 until VOLLEY) {
                val yaw = Math.toRadians((i - (VOLLEY - 1) / 2.0) * VOLLEY_SPREAD)
                val dir = Vec3(look.x * cos(yaw) - look.z * sin(yaw), look.y, look.x * sin(yaw) + look.z * cos(yaw))
                val arrow = Arrow(level, caster, net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null)
                arrow.pickup = net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.DISALLOWED
                arrow.shoot(dir.x, dir.y, dir.z, ARROW_SPEED, 1.0f)
                level.addFreshEntity(arrow)
            }
            level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.SKELETON_SHOOT, SoundSource.PLAYERS, 1.0f, 0.7f)
            return AbilityOutcome.done("☠ Кістяк усередині випустив залп")
        }

        private fun envoy(context: CastContext): AbilityOutcome {
            val caster = context.caster
            val target = context.targeting.lookingAt(ENVOY_RANGE) ?: return refundless("Немає цілі для примарної руки")
            val level = context.level
            val from = caster.eyePosition
            val to = target.eyePosition
            val steps = (from.distanceTo(to) * 3).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val p = from.lerp(to, i.toDouble() / steps)
                level.sendParticles(ModParticles.glyph(DEATH_COLOR.shade(-0.3)), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0)
            }
            target.hurtServer(level, level.damageSources().indirectMagic(caster, caster), ENVOY_DAMAGE)
            target.addEffect(MobEffectInstance(MobEffects.WITHER, ENVOY_TICKS, 1))
            target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, ENVOY_TICKS, 3))
            target.addEffect(MobEffectInstance(MobEffects.BLINDNESS, ENVOY_TICKS, 0))
            level.playSound(null, target.x, target.y, target.z, SoundEvents.ALLAY_HURT, SoundSource.PLAYERS, 1.0f, 0.4f)
            return AbilityOutcome.done("☠ Примарна рука схопила ${target.name.string}")
        }

        private fun shadowStep(context: CastContext): AbilityOutcome {
            val caster = context.caster
            val target = context.targeting.lookingAt(SHADOW_RANGE) ?: return refundless("Немає цілі для кроку в тінь")
            val back = Vec3(target.lookAngle.x, 0.0, target.lookAngle.z).normalize().scale(-1.5)
            val spot = target.position().add(back)
            val level = context.level
            level.sendParticles(ParticleTypes.SQUID_INK, caster.x, caster.y + 1.0, caster.z, 16, 0.3, 0.6, 0.3, 0.02)
            caster.teleportTo(spot.x, spot.y, spot.z)
            level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.8f)
            return AbilityOutcome.done("☠ Ви вийшли з тіні за спиною ${target.name.string}")
        }

        /** Only reachable if the target vanished between the check in `cast` and the power itself. */
        private fun refundless(text: String): AbilityOutcome = AbilityOutcome.done(text)

        private const val SCOUT_RADIUS = 60.0
        private const val SCOUT_TICKS = 15 * TICKS_PER_SECOND
        private const val BURST_RADIUS = 5.0
        private const val BURST_DAMAGE = 6f
        private const val SEA_RANGE = 20.0
        private const val SEA_LANES = 4
        private const val SEA_DAMAGE = 6f
        private const val VOLLEY = 5
        private const val VOLLEY_SPREAD = 8.0
        private const val ARROW_SPEED = 2.6f
        private const val ENVOY_RANGE = 25.0
        private const val ENVOY_DAMAGE = 10f
        private const val ENVOY_TICKS = 4 * TICKS_PER_SECOND
        private const val SHADOW_RANGE = 15.0
    }
}
