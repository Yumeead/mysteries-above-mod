package me.vangoo.mysteries.entity

import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation
import net.minecraft.world.level.Level

/**
 * A person: a citizen, a guard, a wild Beyonder. The plugin had Citizens NPCs for this; here it is
 * a real mob with real pathfinding — doors, stairs, ladders — that the creature engine gives a mind.
 *
 * ## Why not an `Avatar`, like the marionette
 *
 * `Avatar` carries the player model for free, but it is not a `Mob`: no navigation, no goals. A
 * marionette is steered by its owner and can afford that ceiling; a town of people walking to work
 * cannot. So this is a `PathfinderMob`, and the client draws it with the player model itself.
 *
 * ## Its face is its id
 *
 * The client picks one of vanilla's default skins from the entity's UUID — the same rule vanilla
 * uses for a player whose skin has not loaded. No skin is stored, synced or chosen; a person keeps
 * their face for life because they keep their id.
 *
 * The body has no will of its own ([registerGoals] is empty): everything it does comes from the
 * creature spec it was made from.
 */
class HumanEntity(type: EntityType<out HumanEntity>, level: Level) : PathfinderMob(type, level) {

    init {
        (navigation as? GroundPathNavigation)?.setCanOpenDoors(true)
        setCanPickUpLoot(false)
    }

    override fun registerGoals() = Unit

    companion object {
        fun attributes(): AttributeSupplier.Builder = createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.ATTACK_DAMAGE, 2.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.FOLLOW_RANGE, 32.0)
    }
}
