package me.vangoo.mysteries.entity

import me.vangoo.mysteries.modId
// `object` is a Kotlin keyword; the package still has to be spelled out in full.
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.Avatar
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.ai.attributes.DefaultAttributes
import net.minecraft.world.entity.monster.Phantom
import net.minecraft.world.entity.monster.Witch
import net.minecraft.world.entity.monster.illager.Evoker
import net.minecraft.world.entity.monster.zombie.Drowned

/** The mod's entities. Declaring one here **is** registering it, as in `ModChannels` and `ModItems`. */
object ModEntities {

    /**
     * Fool, Seq. 5: a body someone else walks in.
     *
     * Saved with its chunk, but only as a real puppet — see [MarionetteEntity]. `noSummon()` keeps it out of `/summon` and
     * out of spawn eggs: a puppet with no owner and no face is not a thing the game should be
     * able to make on its own.
     */
    val MARIONETTE: EntityType<MarionetteEntity> = register("marionette") { key ->
        EntityType.Builder.of(::MarionetteEntity, MobCategory.MISC)
            .sized(0.6f, 1.8f)
            .eyeHeight(Avatar.DEFAULT_EYE_HEIGHT)
            .noSummon()
            .clientTrackingRange(TRACKING_CHUNKS)
            .build(key)
    }

    /**
     * A person of the creature engine: citizen, guard, wild Beyonder. `noSummon()` for the same
     * reason as the marionette — a human without a creature spec has no mind; they are made by
     * `/mysteries creature spawn` or by the engine itself.
     */
    val HUMAN: EntityType<HumanEntity> = register("human") { key ->
        EntityType.Builder.of(::HumanEntity, MobCategory.CREATURE)
            .sized(0.6f, 1.8f)
            .eyeHeight(Avatar.DEFAULT_EYE_HEIGHT)
            .noSummon()
            .clientTrackingRange(TRACKING_CHUNKS)
            .build(key)
    }

    /**
     * Bodies with a model of their own (phase 14) — the plugin's four BetterModel creatures.
     *
     * Each is the very vanilla mob it was in the plugin, under the mod's own type: the phantom still
     * swoops, the drowned still swims, the evoker still casts — the creature engine overlays its mind
     * exactly as it did on `minecraft:phantom`. The type exists for one reason: the client draws by type,
     * and this one is drawn with the model instead of vanilla's. Size, category and attributes are copied
     * from the vanilla type, so nothing about the body changes but the picture.
     */
    val SEA_CONDOR: EntityType<Phantom> = body("sea_condor", ::Phantom, EntityTypes.PHANTOM)
    val SIREN: EntityType<Drowned> = body("siren", ::Drowned, EntityTypes.DROWNED)
    val ANCIENT_WRAITH: EntityType<Evoker> = body("ancient_wraith", ::Evoker, EntityTypes.EVOKER)
    val THOUSAND_FACED_HUNTER: EntityType<Witch> = body("thousand_faced_hunter", ::Witch, EntityTypes.WITCH)

    private val bodies: List<Pair<EntityType<out Mob>, EntityType<out Mob>>> = listOf(
        SEA_CONDOR to EntityTypes.PHANTOM,
        SIREN to EntityTypes.DROWNED,
        ANCIENT_WRAITH to EntityTypes.EVOKER,
        THOUSAND_FACED_HUNTER to EntityTypes.WITCH,
    )

    /** Forces the class to initialise before the registries close. */
    fun register() {
        FabricDefaultAttributeRegistry.register(MARIONETTE, MarionetteEntity.attributes())
        FabricDefaultAttributeRegistry.register(HUMAN, HumanEntity.attributes())
        bodies.forEach { (body, like) -> FabricDefaultAttributeRegistry.register(body, DefaultAttributes.getSupplier(like)) }
    }

    /** `noSummon()` for the reason [HUMAN] gives: without a creature spec the body has no mind. */
    private fun <E : Mob> body(path: String, factory: EntityType.EntityFactory<E>, like: EntityType<*>): EntityType<E> =
        register(path) { key ->
            EntityType.Builder.of(factory, like.category)
                .sized(like.dimensions.width(), like.dimensions.height())
                .eyeHeight(like.dimensions.eyeHeight())
                .clientTrackingRange(like.clientTrackingRange())
                .noSummon()
                .build(key)
        }

    /**
     * The same range vanilla gives players. A puppet is watched from as far as its owner can
     * command it, and the control radius (100–200 blocks) outruns even this — which is exactly
     * why a marionette out of range goes on the strand clock instead of quietly desyncing.
     */
    private const val TRACKING_CHUNKS = 10

    private fun <E : net.minecraft.world.entity.Entity> register(
        path: String,
        factory: (ResourceKey<EntityType<*>>) -> EntityType<E>,
    ): EntityType<E> {
        val key = ResourceKey.create(Registries.ENTITY_TYPE, modId(path))
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, factory(key))
    }
}
