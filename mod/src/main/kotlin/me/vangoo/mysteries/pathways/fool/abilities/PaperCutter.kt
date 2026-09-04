package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.PaperThrowDamage
import me.vangoo.domain.valueobjects.PaperWeaponType
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.item.ModComponents
import me.vangoo.mysteries.item.PaperWeapon
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Fool, Seq. 8 (Clown), evolving into **Drawing Paper as Weapons** at Seq. 7 (Magician): **Paper
 * Cutter**.
 *
 * ## One class, not two — [IDENTITY] cannot carry a second one
 *
 * The plugin had `PaperCutter` (passive) evolve into `PaperWeaponry` (active) as two classes
 * sharing one identity. This mod holds a stricter rule than the plugin ever did: a shared
 * identity must carry a shared class — the same reasoning Tyrant's `Balance`/`AirCushion` already
 * settled on (one parameterized body, not a subclass). A `PermanentPassiveAbility` and an
 * `ActiveAbility` cannot be the same object, so the merge goes the other way: this is an
 * `ActiveAbility` from the moment it is granted, and casting below Seq. 7 simply has nothing to
 * do yet — the same "not unlocked" refusal `PowerProhibition`'s `Punishment` gate already uses,
 * just spelled out for the caster instead of silently swallowed.
 *
 * The passive throw itself needs no cast at all: [PaperThrowTrigger] catches the right-click
 * directly and works identically at every Sequence this identity is held.
 */
class PaperCutter : ActiveAbility(IDENTITY), PaperThrower {

    override val displayName: Component = Component.literal("Паперовий різак")

    override val spiritualityCost: Int = 0

    override fun cooldownSeconds(sequence: Sequence): Int = CRAFT_COOLDOWN_SECONDS

    private val lastThrowTick = ConcurrentHashMap<UUID, Long>()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Тримаючи звичайний папір, кидайте його (ПКМ) — паперовий кинджал завдає " +
            "${PaperThrowDamage.damageFor(sequence)} шкоди. Безкоштовно, відкат 0.5 с.\n" +
            if (sequence.level <= WEAPON_UNLOCK_SEQUENCE) {
                "▪ Каст із бляз-рудом/палицею/цеглою в руці плете з паперу " +
                    PaperWeaponType.entries.joinToString(", ") { "${it.displayName.lowercase()} (${it.paperCost})" }
            } else {
                "▪ Плетіння зброї з паперу відкриється на Посл. $WEAPON_UNLOCK_SEQUENCE"
            },
    )

    override fun throwPaper(context: AbilityContext): Boolean = PaperThrows.throwOnce(context, lastThrowTick)

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sequence.level > WEAPON_UNLOCK_SEQUENCE) {
            return AbilityOutcome.failed("Плетіння зброї з паперу відкриється на Посл. $WEAPON_UNLOCK_SEQUENCE")
        }
        val type = typeFor(context.caster.mainHandItem.item)
            ?: return AbilityOutcome.failed("Тримайте бляз-руд, палицю або цеглу, щоб сплести зброю")

        if (PaperInventory.count(context.caster) < type.paperCost) {
            return AbilityOutcome.failed("Потрібно ${type.paperCost} паперу")
        }
        PaperInventory.consume(context.caster, type.paperCost)

        val weapon = ItemStack(materialFor(type))
        weapon.set(DataComponents.CUSTOM_NAME, Component.literal(type.displayName))
        weapon.set(ModComponents.PAPER_WEAPON, PaperWeapon(type.name, type.uses))
        if (!context.caster.addItem(weapon)) context.caster.drop(weapon, false)

        val at = context.origin
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.4f)

        return AbilityOutcome.done("Створено: ${type.displayName}")
    }

    private fun typeFor(item: Item): PaperWeaponType? = when (item) {
        Items.BLAZE_ROD -> PaperWeaponType.BAT
        Items.STICK -> PaperWeaponType.CANE
        Items.BRICK -> PaperWeaponType.BRICK
        else -> null
    }

    private fun materialFor(type: PaperWeaponType): Item = when (type) {
        PaperWeaponType.BAT -> Items.BLAZE_ROD
        PaperWeaponType.CANE -> Items.STICK
        PaperWeaponType.BRICK -> Items.BRICK
    }

    companion object {
        val IDENTITY: AbilityIdentity = AbilityIdentity.of("fool_paper_cutter")
        private const val WEAPON_UNLOCK_SEQUENCE = 7
        private const val CRAFT_COOLDOWN_SECONDS = 3
    }
}
