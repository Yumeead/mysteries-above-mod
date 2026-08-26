package me.vangoo.mysteries.item

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.modId
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.component.Consumables
import net.minecraft.world.item.component.ItemLore
import java.util.Optional

/** Предмети мода. Оголошення тут — і є реєстрацією, як у `ModChannels`. */
object ModItems {

    /**
     * Потойбічне зілля. Один предмет на всі 220 варіантів: що саме налито, каже
     * компонент [ModComponents.POTION_BREW], а не окремий зареєстрований предмет.
     */
    val BEYONDER_POTION: Item = register("beyonder_potion") { properties ->
        BeyonderPotionItem(
            properties
                .stacksTo(1)
                .rarity(Rarity.UNCOMMON)
                .component(DataComponents.CONSUMABLE, Consumables.defaultDrink().build())
                .usingConvertsTo(Items.GLASS_BOTTLE),
        )
    }

    /** Змушує клас ініціалізуватись до того, як реєстри закриються. */
    fun register() = Unit

    /**
     * Пляшка конкретного шляху й Послідовності.
     *
     * Колір рідини береться з `PathwayBranding` через ванільний `POTION_CONTENTS`:
     * модель предмета — вана ж пляшка зілля з тінтом, тож жодної нової текстури мод не
     * несе, а всі 22 шляхи вже мають свій відтінок.
     */
    fun potionOf(pathway: Pathway, sequence: Sequence): ItemStack {
        val branding = PathwayBranding.of(pathway.name)
        val stack = ItemStack(BEYONDER_POTION)

        stack.set(ModComponents.POTION_BREW, PotionBrew(pathway.name, sequence.level))
        stack.set(
            DataComponents.POTION_CONTENTS,
            PotionContents(Optional.empty(), Optional.of(branding.liquid.packed), listOf(), Optional.empty()),
        )
        stack.set(
            DataComponents.ITEM_NAME,
            Component.literal("Зілля «${pathway.sequenceName(sequence.level)}»")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(branding.text.packed))),
        )
        stack.set(
            DataComponents.LORE,
            ItemLore(
                listOf(
                    Component.literal("Шлях: ${pathway.name}"),
                    Component.literal("Послідовність: ${sequence.level}"),
                    Component.literal("Група: ${pathway.group.displayName}"),
                ),
            ),
        )
        return stack
    }

    private fun register(path: String, factory: (Item.Properties) -> Item): Item {
        val key = ResourceKey.create(Registries.ITEM, modId(path))
        return Registry.register(BuiltInRegistries.ITEM, key, factory(Item.Properties().setId(key)))
    }
}
