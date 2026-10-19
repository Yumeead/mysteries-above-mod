package me.vangoo.mysteries.pathways.death

import me.vangoo.mysteries.creature.CreatureRegistries
import me.vangoo.mysteries.creature.CreatureSpec
import me.vangoo.mysteries.creature.Creatures
import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.minecraft.tags.TagKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Vex
import net.minecraft.world.phys.Vec3

/**
 * What a spirit is in this mod.
 *
 * ## Two kinds of spirit
 *
 * | | what | how it is told |
 * |---|---|---|
 * | a spirit | any vex | [isSpirit] |
 * | a being of the Spirit World | a creature of the engine in the datapack tag `#mysteries_above:spirit_world` — the Living Shadow, the Death Knight, the Lake Goddess… | [isSpiritWorldBeing] |
 *
 * The plugin told the second kind by a scoreboard tag the MythicMobs pack put on each creature,
 * which had to match a literal in the code. Here it is a registry tag: a datapack adds a being of
 * the Spirit World by naming it in the tag, and nothing in the code changes.
 *
 * ## A vex, because vanilla already has spirits
 *
 * The plugin's wandering spirit was a MythicMobs creature on a vex base. Vanilla's vex **is** a
 * spirit — the one an evoker calls out of nowhere: it flies, it passes through walls, it has a
 * limited life. So every vex counts, including an evoker's: a Gravedigger can talk a hostile
 * evoker's spirit into following them instead, which the plugin's tagged creature could not offer.
 *
 * ## Spirits never outlive what bound them
 *
 * A bound spirit is held with [Vex.setLimitedLife], refreshed every tick by whoever holds it. If
 * that holder stops — a crash, a session cut short — the spirit fades by itself within seconds
 * rather than standing in the world forever. The [EPHEMERAL] tag covers the other hole: a spirit
 * saved with its chunk would come back from disk with its vanilla goals restored, i.e. hostile.
 */
object Spirits {

    /**
     * Entities the mod made for a moment and that must never come back from disk: bound spirits,
     * the corpse guise's shell. The tag is added **after** the entity enters the level, so the
     * load event of its own spawn does not see it; only a reload from a saved chunk does.
     */
    const val EPHEMERAL: String = "mysteries_above.ephemeral"

    fun isSpirit(entity: Entity): Boolean = entity is Vex

    val SPIRIT_WORLD: TagKey<CreatureSpec> = TagKey.create(CreatureRegistries.CREATURE, modId("spirit_world"))

    fun isSpiritWorldBeing(entity: Entity): Boolean {
        val key = Creatures.keyOf(entity) ?: return false
        return entity.level().registryAccess().lookupOrThrow(CreatureRegistries.CREATURE).get(key)
            .map { it.`is`(SPIRIT_WORLD) }
            .orElse(false)
    }

    /** A spirit called out of the Spirit World at [at], already bound. */
    fun summon(level: ServerLevel, at: Vec3): Vex {
        val vex = Vex(EntityTypes.VEX, level)
        vex.snapTo(at.x, at.y, at.z, 0f, 0f)
        level.addFreshEntity(vex)
        bind(vex)
        return vex
    }

    /**
     * Takes the spirit's will away: no goals at all (its own random flight would fight every
     * steer), no target, no gravity, no strength in its hands. Whoever bound it moves it.
     */
    fun bind(vex: Vex) {
        vex.getGoalSelector().removeAllGoals { true }
        vex.targetSelector.removeAllGoals { true }
        vex.target = null
        vex.isNoGravity = true
        vex.setPersistenceRequired()
        vex.getAttribute(Attributes.ATTACK_DAMAGE)?.baseValue = 0.0
        vex.setLimitedLife(LEASH_TICKS)
        vex.addTag(EPHEMERAL)
    }

    /** Called every tick by the holder: the spirit stays exactly as long as it is held. */
    fun hold(vex: Vex) {
        vex.setLimitedLife(LEASH_TICKS)
    }

    fun install() {
        ServerEntityEvents.ENTITY_LOAD.register { entity, _ ->
            if (EPHEMERAL in entity.entityTags()) entity.discard()
        }
    }

    /** Ten seconds without a holder and a bound spirit fades. */
    private const val LEASH_TICKS = 200
}
