package me.vangoo.mysteries.creature

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.creature.ai.Minds
import me.vangoo.mysteries.entity.HumanEntity
import me.vangoo.mysteries.item.ModItems
import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.registry.DynamicRegistries
import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.PoiTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.village.poi.PoiManager
import net.minecraft.world.entity.ai.village.poi.PoiTypes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import java.util.Optional

/**
 * Who a human is: rolled once when they are made, kept on the entity for good.
 *
 * The face is not here — the client derives it from the entity's id, so a hundred citizens cost
 * nothing to sync and no one's face ever changes.
 */
class Persona(val name: String, val pathway: String?, val sequence: Int?) {
    companion object {
        val CODEC: Codec<Persona> = RecordCodecBuilder.create { row ->
            row.group(
                Codec.STRING.fieldOf("name").forGetter(Persona::name),
                Codec.STRING.optionalFieldOf("pathway").forGetter { Optional.ofNullable(it.pathway) },
                Codec.INT.optionalFieldOf("sequence").forGetter { Optional.ofNullable(it.sequence) },
            ).apply(row) { name, pathway, sequence -> Persona(name, pathway.orElse(null), sequence.orElse(null)) }
        }
    }
}

/**
 * The places a creature's life is tied to: where it rose (a boss's arena, a guard's post), where
 * it sleeps, where it works, where it meets people. Saved on the entity — a town remembers its
 * people's homes across restarts because each person does.
 */
class Anchors(val home: BlockPos, val bed: BlockPos?, val work: BlockPos?, val gather: BlockPos?) {
    val homeVec: Vec3 get() = Vec3.atBottomCenterOf(home)

    companion object {
        val CODEC: Codec<Anchors> = RecordCodecBuilder.create { row ->
            row.group(
                BlockPos.CODEC.fieldOf("home").forGetter(Anchors::home),
                BlockPos.CODEC.optionalFieldOf("bed").forGetter { Optional.ofNullable(it.bed) },
                BlockPos.CODEC.optionalFieldOf("work").forGetter { Optional.ofNullable(it.work) },
                BlockPos.CODEC.optionalFieldOf("gather").forGetter { Optional.ofNullable(it.gather) },
            ).apply(row) { home, bed, work, gather -> Anchors(home, bed.orElse(null), work.orElse(null), gather.orElse(null)) }
        }
    }
}

/**
 * The creature engine: what replaced MythicMobs.
 *
 * ## A creature is a body with a mind laid over it
 *
 * A creature is any `Mob` carrying [CREATURE] — the key of its [CreatureSpec]. The body is a
 * vanilla entity or the mod's own [HumanEntity]; the mind ([Mind] plus the goals of
 * [me.vangoo.mysteries.creature.ai]) is rebuilt every time the body enters a level, because vanilla
 * saves a mob but never its goals. Nothing is described elsewhere and respawned: the world keeps the
 * creature, as it keeps the Retinue's servants.
 *
 * ## The order of the load hooks matters
 *
 * [install] runs before `Death` and `Marionettes` hang their own `ENTITY_LOAD`: a servant or a
 * puppet that is also a creature must have its will stripped **after** the mind is rebuilt, not
 * before. [Mind.isOwned] guards the same thing from the other side.
 */
object Creatures {

    private val log = LoggerFactory.getLogger("$MOD_ID/creatures")

    /** A scoreboard tag on every creature, so that `/kill @e[tag=…]` and friends reach them. */
    const val TAG: String = "mysteries_above.creature"

    val CREATURE: AttachmentType<ResourceKey<CreatureSpec>> by lazy {
        AttachmentRegistry.create(modId("creature")) { it.persistent(ResourceKey.codec(CreatureRegistries.CREATURE)) }
    }

    val PERSONA: AttachmentType<Persona> by lazy {
        AttachmentRegistry.create(modId("persona")) { it.persistent(Persona.CODEC) }
    }

    val ANCHORS: AttachmentType<Anchors> by lazy {
        AttachmentRegistry.create(modId("anchors")) { it.persistent(Anchors.CODEC) }
    }

    /** The live mind. Not persistent: it is rebuilt on every load. */
    private val MIND: AttachmentType<Mind> by lazy { AttachmentRegistry.create(modId("mind")) }

    /** The three datapack registries. Called from the mod's initializer, before any world loads. */
    fun registerData() {
        DynamicRegistries.register(CreatureRegistries.CREATURE, CreatureSpec.CODEC)
        DynamicRegistries.register(CreatureRegistries.KIT, CreatureKit.CODEC)
        DynamicRegistries.register(CreatureRegistries.SKILL, CreatureSkill.CODEC)
    }

    fun install() {
        // Attachment types register lazily — at mod init, here, never in a unit test without Fabric's mixins.
        CREATURE
        PERSONA
        ANCHORS
        MIND

        ServerEntityEvents.ENTITY_LOAD.register { entity, level -> if (entity is Mob) awaken(entity, level) }
        ServerEntityEvents.ENTITY_UNLOAD.register { entity, _ -> mindOf(entity)?.dispose() }
        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, _, taken, _ ->
            if (taken > 0f) mindOf(entity)?.onHurt(source, taken)
        }
        // A boss turning to its next act cannot be touched for the moment it takes.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ ->
            val mind = mindOf(entity) ?: return@register true
            mind.now >= mind.pausedUntil
        }
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
            val mind = mindOf(entity) ?: return@register
            mind.dispose()
            leaveCharacteristic(mind)
            dropOwnLoot(entity, mind.level, source)
        }
        // A creature drops only what the mod gives it — the plugin's `PreventOtherDrops`. A goat
        // that is a Grey Mountain Goat of Hornacis does not drop mutton, only its own table.
        LootTableEvents.MODIFY_DROPS.register { table, context, drops ->
            val dead = context.getOptionalParameter(LootContextParams.THIS_ENTITY)
            if (dead == null || !context.hasParameter(LootContextParams.DAMAGE_SOURCE) || !isCreature(dead)) return@register
            if (table.unwrapKey().orElse(null) != lootOf(dead)) drops.clear()
        }
        CreatureSpawner.install()
    }

    // ── Lookups ──────────────────────────────────────────────────────────────────

    fun isCreature(entity: Entity): Boolean = entity.hasAttached(CREATURE)

    fun keyOf(entity: Entity): ResourceKey<CreatureSpec>? = entity.getAttached(CREATURE)

    fun mindOf(entity: Entity): Mind? = entity.getAttached(MIND)

    fun spec(level: ServerLevel, key: ResourceKey<CreatureSpec>): CreatureSpec? =
        level.registryAccess().lookupOrThrow(CreatureRegistries.CREATURE).getValue(key)

    fun kit(level: ServerLevel, key: ResourceKey<CreatureKit>): CreatureKit? =
        level.registryAccess().lookupOrThrow(CreatureRegistries.KIT).getValue(key)
            ?: null.also { log.warn("Немає кіта {}", key.identifier()) }

    fun skill(level: ServerLevel, key: ResourceKey<CreatureSkill>): CreatureSkill? =
        level.registryAccess().lookupOrThrow(CreatureRegistries.SKILL).getValue(key)
            ?: null.also { log.warn("Немає прийому {}", key.identifier()) }

    /**
     * A wild Beyonder's kit is their pathway's at their Sequence — `mysteries_above:<pathway>_<seq>`,
     * the same kit the pathway's creatures fight with. No kit for that pair: a Beyonder who fights
     * with their fists.
     */
    fun kitFor(level: ServerLevel, pathway: String?, sequence: Int?): ResourceKey<CreatureKit>? {
        if (pathway == null || sequence == null) return null
        val key = ResourceKey.create(CreatureRegistries.KIT, modId("${pathway.lowercase()}_$sequence"))
        return key.takeIf { level.registryAccess().lookupOrThrow(CreatureRegistries.KIT).containsKey(it) }
    }

    fun creatureKey(id: Identifier): ResourceKey<CreatureSpec> = ResourceKey.create(CreatureRegistries.CREATURE, id)

    /** Whose Sequence presses on others: a Beyonder player's, or a creature's own. */
    fun sequenceOf(entity: LivingEntity): Int? =
        if (entity is Player) AbilityServices.beyonders.of(entity.uuid)?.sequence?.level else mindOf(entity)?.sequence

    // ── Sides ────────────────────────────────────────────────────────────────────

    private enum class Side { WILD, TOWN, LONE }

    private fun sideOf(entity: Entity): Side? = when (mindOf(entity)?.role) {
        Role.BEAST -> Side.WILD
        Role.CITIZEN, Role.GUARD -> Side.TOWN
        Role.BEYONDER -> Side.LONE
        null -> null
    }

    /** Two creatures of one side: the wild do not trample the wild, the town does not strike the town. */
    fun areAllies(a: Entity, b: Entity): Boolean {
        val side = sideOf(a) ?: return false
        return side != Side.LONE && side == sideOf(b)
    }

    /**
     * Whether a technique of [caster]'s may land on [other] in passing — an area, a stray
     * projectile. Its target and its grudges always may; beyond them the wild strike any player, a
     * hostile Beyonder too, and the town only monsters. A guard's volley never hurts a bystander.
     */
    fun isFoe(caster: Mob, other: LivingEntity): Boolean {
        if (other === caster || !other.isAlive) return false
        if (other is Player && (other.isCreative || other.isSpectator)) return false
        if (caster.target === other) return true
        val mind = mindOf(caster) ?: return other is Player
        if (mind.threat.of(other.uuid) > 0.0) return true
        if (areAllies(caster, other)) return false
        val side = sideOf(caster)
        return when {
            isCreature(other) -> side != sideOf(other) && (side != Side.TOWN || mindOf(other)?.isHostileBeast == true)
            other is Player -> side == Side.WILD || mind.temperament == Temperament.HOSTILE
            other is net.minecraft.world.entity.monster.Enemy -> side == Side.TOWN
            else -> false
        }
    }

    /**
     * Sets a target the way the body reads it. A few bodies (the zoglin, the frog, the warden) read
     * their target from the brain, not the field; for them the brain is told too. The vanilla
     * `setTarget` goes through `MobTargeting`, so Error's charm still refuses a creature its victim.
     */
    fun aim(mob: Mob, target: LivingEntity?) {
        mob.target = target
        val brain = mob.brain
        if (!brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) return
        if (target != null && mob.target === target) {
            brain.setMemory(MemoryModuleType.ATTACK_TARGET, target)
        } else if (target == null) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
        }
    }

    // ── Bringing a creature into the world ──────────────────────────────────────

    /**
     * Makes a creature of [key] at [at] and puts it into [level]. Its mind wakes through the same
     * load hook that wakes a creature coming back from disk — one path, not two.
     *
     * @return `null` when the key names no creature or the body could not be made
     */
    fun spawn(level: ServerLevel, key: ResourceKey<CreatureSpec>, at: Vec3, reason: EntitySpawnReason = EntitySpawnReason.COMMAND): Mob? {
        val spec = spec(level, key) ?: return null.also { log.warn("Невідома істота {}", key.identifier()) }
        val mob = spec.body.entity.create(level, reason) as? Mob
            ?: return null.also { log.warn("Тіло {} істоти {} — не моб", spec.body.entity, key.identifier()) }
        mob.snapTo(at.x, at.y, at.z, mob.random.nextFloat() * 360f, 0f)
        mob.setAttached(CREATURE, key)
        spec.persona?.let { mob.setAttached(PERSONA, roll(it, mob)) }
        mob.setAttached(ANCHORS, settle(level, spec, BlockPos.containing(at)))
        dress(mob, spec, mob.getAttached(PERSONA))
        mob.health = mob.maxHealth
        // The town and bosses stay where they are put; the wild comes and goes like any beast.
        if (spec.mind.role == Role.CITIZEN || spec.mind.role == Role.GUARD || spec.boss != null) mob.setPersistenceRequired()
        mob.addTag(TAG)
        level.addFreshEntity(mob)
        return mob
    }

    private fun roll(spec: PersonaSpec, mob: Mob): Persona {
        val random = mob.random
        fun <T> List<T>.pick(): T? = if (isEmpty()) null else this[random.nextInt(size)]
        val name = spec.names.pick() ?: "Незнайомець"
        val pathway = spec.pathways.pick()
        val strongest = minOf(spec.sequenceMin, spec.sequenceMax)
        val weakest = maxOf(spec.sequenceMin, spec.sequenceMax)
        val sequence = pathway?.let { strongest + random.nextInt(weakest - strongest + 1) }
        return Persona(name, pathway, sequence)
    }

    /**
     * Where a town-dweller's life is: the nearest bed, workstation and bell around the spot they
     * were made on. The vanilla point-of-interest index already knows them all — no scan of blocks.
     *
     * ponytail: the points are looked up, not claimed; two citizens may share a bed with each other
     * or with a villager until someone claims beds properly.
     */
    private fun settle(level: ServerLevel, spec: CreatureSpec, at: BlockPos): Anchors {
        if (spec.mind.role != Role.CITIZEN && spec.mind.role != Role.GUARD) return Anchors(at, null, null, null)
        val poi = level.poiManager
        fun nearest(radius: Int, predicate: (net.minecraft.core.Holder<net.minecraft.world.entity.ai.village.poi.PoiType>) -> Boolean) =
            poi.findClosest(predicate, at, radius, PoiManager.Occupancy.ANY).orElse(null)
        val bed = nearest(SETTLE_RADIUS) { it.`is`(PoiTypes.HOME) }
        val work = if (spec.mind.role == Role.GUARD) at else nearest(SETTLE_RADIUS) { it.`is`(PoiTypeTags.ACQUIRABLE_JOB_SITE) }
        val gather = nearest(SETTLE_RADIUS * 2) { it.`is`(PoiTypes.MEETING) }
        return Anchors(bed ?: at, bed, work, gather)
    }

    /** Lays the spec's numbers onto the body. Safe to repeat: a changed datapack re-balances old creatures on load. */
    private fun dress(mob: Mob, spec: CreatureSpec, persona: Persona?) {
        val stats = spec.stats
        fun base(attribute: net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>, value: Double) {
            mob.getAttribute(attribute)?.baseValue = value
        }
        base(Attributes.MAX_HEALTH, stats.health.toDouble())
        base(Attributes.ATTACK_DAMAGE, stats.damage.toDouble())
        stats.speed?.let { base(Attributes.MOVEMENT_SPEED, it) }
        base(Attributes.ARMOR, stats.armor)
        base(Attributes.FOLLOW_RANGE, stats.followRange)
        base(Attributes.KNOCKBACK_RESISTANCE, stats.knockbackResistance)
        base(Attributes.SCALE, spec.body.scale.toDouble())
        if (mob.health > mob.maxHealth) mob.health = mob.maxHealth

        val name = persona?.let { net.minecraft.network.chat.Component.literal(it.name) } ?: spec.body.name
        if (name != null && mob.customName == null) mob.customName = name
        spec.body.equipment.forEach { (slot, item) ->
            if (mob.getItemBySlot(slot).isEmpty) mob.setItemSlot(slot, ItemStack(item))
            mob.setDropChance(slot, 0f)
        }
    }

    private fun awaken(mob: Mob, level: ServerLevel) {
        val key = keyOf(mob) ?: return
        val spec = spec(level, key) ?: return log.warn("Істота {} без опису в датапаку — лишається тілом", key.identifier())
        dress(mob, spec, mob.getAttached(PERSONA))
        val anchors = mob.getAttached(ANCHORS) ?: Anchors(mob.blockPosition(), null, null, null).also { mob.setAttached(ANCHORS, it) }
        val mind = Mind(mob, key, spec, mob.getAttached(PERSONA), anchors)
        mob.setAttached(MIND, mind)
        Minds.install(mind)
    }

    /**
     * The Law of Conservation: a Beyonder's characteristic does not die with them. A wild
     * Beyonder leaves theirs where they fell, exactly as a marionette made from a Beyonder does.
     */
    /**
     * The creature's own table, `<namespace>:creature/<id>` — the plugin's `creatures.yml` loot. A
     * convention rather than a field: a creature without a table simply drops nothing, and a datapack
     * gives one to its creature by placing the file.
     */
    private fun lootOf(entity: Entity): ResourceKey<LootTable>? =
        keyOf(entity)?.identifier()?.let { ResourceKey.create(Registries.LOOT_TABLE, it.withPrefix("creature/")) }

    private fun dropOwnLoot(entity: LivingEntity, level: ServerLevel, source: DamageSource) {
        if (!level.gameRules.get(GameRules.MOB_DROPS)) return
        val table = lootOf(entity) ?: return
        entity.dropFromLootTable(level, source, source.entity is Player, table)
    }

    private fun leaveCharacteristic(mind: Mind) {
        if (mind.role != Role.BEYONDER) return
        val pathway = mind.pathway ?: return
        val sequence = mind.sequence ?: return
        mind.mob.spawnAtLocation(mind.level, ModItems.characteristicOf(pathway, sequence))
    }

    private const val SETTLE_RADIUS = 48
}
