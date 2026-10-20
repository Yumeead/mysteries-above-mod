package me.vangoo.mysteries.creature

import me.vangoo.domain.creatures.ApexGate
import me.vangoo.domain.creatures.ConvergenceBias
import me.vangoo.domain.creatures.CreatureDefinition
import me.vangoo.domain.creatures.CreatureSelector
import me.vangoo.domain.creatures.CreatureTier
import me.vangoo.domain.creatures.SpawnDistanceGate
import me.vangoo.domain.creatures.SpawnRule
import me.vangoo.domain.valueobjects.LootTableData
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder
import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.gamerules.GameRule
import net.minecraft.world.level.gamerules.GameRuleCategory
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3

/**
 * Where creatures come from when nobody calls them — the plugin's two spawn paths, with its numbers
 * and both of its gates.
 *
 * | path | when | who |
 * |---|---|---|
 * | ambient | once a minute per Beyonder, a small chance | a creature of the biome, leaning to the player's pathway |
 * | structure | a chest's loot is rolled | a creature one Sequence around the one who opened it |
 *
 * Both pass the same two gates: **place** ([SpawnDistanceGate] — nothing otherworldly near the
 * world's spawn) and **strength** ([ApexGate] / the structure window — a Sequence 5 horror only comes
 * to someone close to Sequence 5). The rules are `core`'s, untouched since the plugin.
 *
 * The plugin also swapped vanilla mobs for creatures at the moment they spawned; Fabric has no hook
 * for that moment, and the ambient path already brings the same creatures to the same biomes.
 *
 * The plugin's `config.yml` numbers are game rules (`/gamerule mysteries_above:…`): they live with the
 * world and change without a restart. The rest stays constant — geometry, not balance.
 */
object CreatureSpawner {

    /** The gate of place: nothing otherworldly within this many blocks of the world's spawn. */
    val MIN_SPAWN_DISTANCE: GameRule<Int> = GameRuleBuilder.forInteger(3000).minValue(0)
        .category(GameRuleCategory.SPAWNING).buildAndRegister(modId("creature_min_spawn_distance"))

    /** How often each Beyonder rolls for an ambient creature. */
    val AMBIENT_INTERVAL_SECONDS: GameRule<Int> = GameRuleBuilder.forInteger(60).minValue(1)
        .category(GameRuleCategory.SPAWNING).buildAndRegister(modId("creature_ambient_interval"))

    /** The chance of that roll — about one creature per 45 minutes. `0` turns ambient creatures off. */
    val AMBIENT_CHANCE: GameRule<Double> = GameRuleBuilder.forDouble(0.022).range(0.0, 1.0)
        .category(GameRuleCategory.SPAWNING).buildAndRegister(modId("creature_ambient_chance"))

    /** No ambient creature while this many already roam within 64 blocks. */
    val MAX_NEARBY: GameRule<Int> = GameRuleBuilder.forInteger(3).minValue(0)
        .category(GameRuleCategory.SPAWNING).buildAndRegister(modId("creature_max_nearby"))

    /** The chance an opened chest brings a creature. `0` turns structure creatures off. */
    val STRUCTURE_CHANCE: GameRule<Double> = GameRuleBuilder.forDouble(0.10).range(0.0, 1.0)
        .category(GameRuleCategory.SPAWNING).buildAndRegister(modId("creature_structure_chance"))

    fun install() {
        // The cooldowns count server ticks, and the next world's server starts counting from zero.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register { chunkCooldown.clear() }
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (server.tickCount % (server.overworld().gameRules.get(AMBIENT_INTERVAL_SECONDS) * 20) == 0) server.playerList.players.forEach(::tryAmbient)
        }
        // A chest's loot is rolled with its opener as THIS_ENTITY and no tool: that is "a chest was
        // opened" in any structure — vanilla's, a datapack's, another mod's.
        LootTableEvents.MODIFY_DROPS.register { _, context, _ ->
            val opener = context.getOptionalParameter(LootContextParams.THIS_ENTITY) as? ServerPlayer ?: return@register
            if (!context.hasParameter(LootContextParams.ORIGIN) || context.hasParameter(LootContextParams.TOOL) ||
                context.hasParameter(LootContextParams.DAMAGE_SOURCE) || context.hasParameter(LootContextParams.BLOCK_STATE)
            ) {
                return@register
            }
            onChestOpened(opener)
        }
    }

    private class Candidate(val key: ResourceKey<CreatureSpec>, val spec: CreatureSpec, val definition: CreatureDefinition)

    /** The registry seen through `core`'s eyes. Rebuilt per roll: a few dozen entries, once a minute. */
    private fun candidates(level: ServerLevel): List<Candidate> =
        level.registryAccess().lookupOrThrow(CreatureRegistries.CREATURE).listElements().toList().mapNotNull { holder ->
            val spec = holder.value()
            val spawn = spec.spawn ?: return@mapNotNull null
            val key = holder.key()
            Candidate(
                key, spec,
                CreatureDefinition(
                    id = key.identifier().toString(),
                    baseEntityType = BuiltInRegistries.ENTITY_TYPE.getKey(spec.body.entity).toString(),
                    tier = if (spec.apex) CreatureTier.APEX else CreatureTier.COMMON,
                    loot = LootTableData(emptyList(), 0, 0),
                    spawn = SpawnRule(spawn.biomes.map { it.toString() }, emptyList(), spawn.chance, spawn.structures),
                    pathway = spec.pathway,
                    sequence = spec.sequence ?: 9,
                ),
            )
        }

    private fun tryAmbient(player: ServerPlayer) {
        if (player.isSpectator || player.isCreative) return
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: return
        val level = player.level()
        if (level.difficulty == Difficulty.PEACEFUL || !farEnough(level, player.position())) return
        if (player.random.nextDouble() >= level.gameRules.get(AMBIENT_CHANCE)) return
        val near = level.getEntitiesOfClass(net.minecraft.world.entity.Mob::class.java, player.boundingBox.inflate(NEARBY_RADIUS)) {
            Creatures.isCreature(it)
        }
        if (near.size >= level.gameRules.get(MAX_NEARBY)) return

        val pool = candidates(level)
        val biome = level.getBiome(player.blockPosition()).unwrapKey().map { it.identifier().toString() }.orElse(null)
        val bias = ConvergenceBias(beyonder.pathway.name, beyonder.sequence.level)
        val pick = CreatureSelector(pool.map { it.definition }).pickForAmbient(biome, bias, player.random.nextDouble()) ?: return
        val chosen = pool.first { it.definition === pick }
        if (!ApexGate.allows(pick.tier, beyonder.sequence.level)) return
        val spot = spotAround(level, player, AMBIENT_MIN, AMBIENT_MAX, aquatic(chosen.spec.body.entity)) ?: return
        Creatures.spawn(level, chosen.key, spot, EntitySpawnReason.NATURAL)
    }

    /** One structure spawn per chunk per five minutes: a dungeon with ten chests is not ten creatures. */
    private val chunkCooldown = HashMap<Long, Int>()

    private fun onChestOpened(player: ServerPlayer) {
        if (player.isSpectator) return
        val level = player.level()
        if (level.difficulty == Difficulty.PEACEFUL || !farEnough(level, player.position())) return
        val server = level.server
        val chunk = ChunkPos.containing(player.blockPosition()).pack()
        if (server.tickCount < (chunkCooldown[chunk] ?: Int.MIN_VALUE)) return
        if (player.random.nextDouble() >= level.gameRules.get(STRUCTURE_CHANCE)) return
        chunkCooldown[chunk] = server.tickCount + STRUCTURE_COOLDOWN_TICKS

        val beyonder = AbilityServices.beyonders.of(player.uuid)
        // Not a Beyonder counts as Sequence 9: structures are dangerous for everyone.
        val sequence = beyonder?.sequence?.level ?: 9
        val bias = beyonder?.let { ConvergenceBias(it.pathway.name, it.sequence.level) }
        val pool = candidates(level).filter { !aquatic(it.spec.body.entity) }
        val pick = CreatureSelector(pool.map { it.definition }).pickForStructure(sequence, bias, player.random.nextDouble()) ?: return
        val chosen = pool.first { it.definition === pick }
        val spot = spotAround(level, player, STRUCTURE_MIN, STRUCTURE_MAX, aquatic = false) ?: return

        // A warning before the thing arrives — a breath to step back from the chest.
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.8f, 0.6f)
        AbilityServices.messaging.tell(player, Component.literal("Щось ворушиться неподалік — скриню відчинили не лише ви"))
        AbilityServices.scheduler.after(STRUCTURE_DELAY_TICKS) {
            if (!player.isRemoved) Creatures.spawn(level, chosen.key, spot, EntitySpawnReason.STRUCTURE)
        }
    }

    /**
     * The gate of place, measured in overworld blocks: the Nether's eight-fold scale divides the
     * threshold by eight there, exactly as the plugin did.
     */
    private fun farEnough(level: ServerLevel, at: Vec3): Boolean {
        val spawn = level.server.overworld().respawnData.pos()
        val scale = level.dimensionType().coordinateScale()
        return SpawnDistanceGate.isFarEnough(at.x * scale - spawn.x, at.z * scale - spawn.z, level.gameRules.get(MIN_SPAWN_DISTANCE).toDouble())
    }

    private fun aquatic(type: EntityType<*>): Boolean =
        type == EntityTypes.GUARDIAN || type == EntityTypes.ELDER_GUARDIAN ||
            type.category == MobCategory.WATER_CREATURE || type.category == MobCategory.UNDERGROUND_WATER_CREATURE

    /** A spot in a ring around the player at about their height — caves count, the sky does not. */
    private fun spotAround(level: ServerLevel, player: ServerPlayer, min: Double, max: Double, aquatic: Boolean): Vec3? {
        val random = player.random
        repeat(SPOT_TRIES) {
            val angle = random.nextDouble() * Math.PI * 2
            val reach = min + random.nextDouble() * (max - min)
            val x = player.x + Math.cos(angle) * reach
            val z = player.z + Math.sin(angle) * reach
            for (dy in VERTICAL_SEARCH) {
                val pos = BlockPos.containing(x, player.y + dy, z)
                val spot = if (aquatic) SafeSpots.swimmable(level, pos) else SafeSpots.standable(level, pos)
                if (spot != null) return spot
            }
        }
        return null
    }

    private const val NEARBY_RADIUS = 64.0
    private const val AMBIENT_MIN = 24.0
    private const val AMBIENT_MAX = 48.0

    private const val STRUCTURE_COOLDOWN_TICKS = 5 * 60 * 20
    private const val STRUCTURE_DELAY_TICKS = 80
    private const val STRUCTURE_MIN = 5.0
    private const val STRUCTURE_MAX = 10.0

    private const val SPOT_TRIES = 20
    private val VERTICAL_SEARCH = (0..6).flatMap { listOf(it, -it) }.distinct()
}
