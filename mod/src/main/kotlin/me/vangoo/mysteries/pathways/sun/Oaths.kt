package me.vangoo.mysteries.pathways.sun

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.vangoo.domain.contracts.ContractTerm
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.item.ModComponents
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.UUIDUtil
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.Filterable
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.WrittenBookContent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random
import me.vangoo.domain.contracts.Contract as DomainContract

/**
 * Sun, Seq. 6: the Oath — a debt the Notary witnessed, and the world now holds.
 *
 * ## The scroll is the debt
 *
 * The plugin kept debts in `contracts.json` and showed them nowhere. Here signing hands the Notary
 * a **Golden Scroll** — a real written book, readable, with the terms on its page — and the debt is
 * owed to whoever holds the original ([holdsScroll]). It can be given away, sold or stolen: a debt
 * became property. A copy made at a crafting table is only a copy (generation above zero) and
 * collects nothing. Whoever the debtor owes, the Notary who signed can always collect.
 *
 * ## Paid by hand, on the wall clock
 *
 * The debtor sneaks and right-clicks the holder with the goods somewhere in their inventory — the
 * plugin's own gesture. The deadline is three real days, measured like a theft: absolute stamps,
 * so leaving the game does not stop it ([DEADLINE_MILLIS]).
 *
 * ## The breach finds you
 *
 * A debtor who is away when the deadline passes is not let off: the breached record stays here,
 * saved with the world, and Divine Retribution falls on their next join ([punish]).
 */
object Oaths {

    private val debts = ArrayList<DomainContract>()

    private val CONTRACT_CODEC: Codec<DomainContract> = RecordCodecBuilder.create { row ->
        row.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(DomainContract::id),
            UUIDUtil.CODEC.fieldOf("party_a").forGetter(DomainContract::partyA),
            UUIDUtil.CODEC.fieldOf("party_b").forGetter(DomainContract::partyB),
            Codec.STRING.fieldOf("term").forGetter { it.term.name },
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("params").forGetter(DomainContract::params),
            Codec.LONG.fieldOf("created_at").forGetter(DomainContract::createdAtEpochMillis),
            Codec.STRING.fieldOf("state").forGetter { it.state.name },
        ).apply(row) { id, a, b, term, params, created, state ->
            DomainContract.restore(
                id, a, b, ContractTerm.valueOf(term), params, created, DomainContract.ContractState.valueOf(state),
            )
        }
    }

    val CODEC: Codec<List<DomainContract>> = CONTRACT_CODEC.listOf()

    // Lazy: Fabric registers attachments through a mixin, and unit tests have none.
    private val SAVED: AttachmentType<List<DomainContract>> by lazy {
        AttachmentRegistry.create(modId("oaths")) { it.persistent(CODEC) }
    }

    /** The terms the Notary set, read back off a signed contract. */
    class Terms(val item: Item, val amount: Int, val deadline: Long)

    fun termsOf(debt: DomainContract): Terms? {
        val item = Identifier.tryParse(debt.params["item"] ?: return null)?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) }
            ?: return null
        val amount = debt.params["amount"]?.toIntOrNull() ?: return null
        val deadline = debt.params["deadlineEpochMillis"]?.toLongOrNull() ?: return null
        return Terms(item, amount, deadline)
    }

    /**
     * Signs an Oath and returns the scroll the creditor keeps.
     *
     * @param goods what is owed, as the Notary held it: its item and its count are the terms
     */
    fun swear(notary: ServerPlayer, debtor: ServerPlayer, goods: ItemStack, now: Long = System.currentTimeMillis()): ItemStack {
        val deadline = now + DEADLINE_MILLIS
        val debt = DomainContract.propose(
            notary.uuid, debtor.uuid, ContractTerm.DEBT,
            mapOf(
                "item" to BuiltInRegistries.ITEM.getKey(goods.item).toString(),
                "amount" to goods.count.toString(),
                "deadlineEpochMillis" to deadline.toString(),
            ),
        )
        debt.sign()
        debts += debt
        return scroll(debt.id, notary, debtor, goods, deadline)
    }

    private fun scroll(id: UUID, notary: ServerPlayer, debtor: ServerPlayer, goods: ItemStack, deadline: Long): ItemStack {
        val page = Component.literal(
            "☀ Золотий Сувій ☀\n\n" +
                "${debtor.name.string} клянеться віддати власникові цього сувою:\n\n" +
                "${goods.count} × ${goods.hoverName.string}\n\n" +
                "до ${DATE.format(Instant.ofEpochMilli(deadline))}.\n\n" +
                "Засвідчив ${notary.name.string}. Порушника покарає Сонце.",
        )
        return ItemStack(Items.WRITTEN_BOOK).apply {
            set(
                DataComponents.WRITTEN_BOOK_CONTENT,
                WrittenBookContent(
                    Filterable.passThrough("Клятва ${debtor.name.string}"),
                    notary.name.string,
                    0,
                    listOf(Filterable.passThrough(page)),
                    true,
                ),
            )
            set(ModComponents.OATH, id)
        }
    }

    /** The original scroll of [debtId] somewhere on [holder] — a crafted copy does not count. */
    private fun scrollSlot(holder: ServerPlayer, debtId: UUID): Int? {
        val inventory = holder.inventory
        return (0 until inventory.containerSize).firstOrNull { slot ->
            val stack = inventory.getItem(slot)
            stack.get(ModComponents.OATH) == debtId &&
                stack.get(DataComponents.WRITTEN_BOOK_CONTENT)?.generation() == 0
        }
    }

    // ── Paying ──────────────────────────────────────────────────────────────────

    private fun pay(debtor: ServerPlayer, holder: ServerPlayer): Boolean {
        val debt = debts.firstOrNull {
            it.state == DomainContract.ContractState.ACTIVE && it.partyB == debtor.uuid &&
                (it.partyA == holder.uuid || scrollSlot(holder, it.id) != null)
        } ?: return false
        val terms = termsOf(debt) ?: return false
        val messaging = AbilityServices.messaging

        val inventory = debtor.inventory
        val slots = (0 until inventory.containerSize).filter { inventory.getItem(it).item === terms.item }
        if (slots.sumOf { inventory.getItem(it).count } < terms.amount) {
            messaging.actionBar(
                debtor,
                Component.literal("Для Клятви потрібно ${terms.amount} × ${ItemStack(terms.item).hoverName.string}"),
            )
            return true
        }

        var left = terms.amount
        for (slot in slots) {
            if (left == 0) break
            val taken = inventory.getItem(slot).split(left)
            left -= taken.count
            if (!holder.inventory.add(taken)) holder.drop(taken, false)
        }

        // The original scroll burns on payment: a paid debt is not owed twice.
        scrollSlot(holder, debt.id)?.let { holder.inventory.setItem(it, ItemStack.EMPTY) }
        debt.settle()
        debts.remove(debt)

        val level = holder.level()
        level.sendParticles(ParticleTypes.END_ROD, holder.x, holder.y + 1.2, holder.z, 24, 0.4, 0.5, 0.4, 0.03)
        level.playSound(null, holder.x, holder.y, holder.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0f, 1.4f)
        messaging.tell(debtor, Component.literal("☀ Клятву виконано — сувій згорів"))
        messaging.tell(
            holder,
            Component.literal("☀ ${debtor.name.string} виконав Клятву: ${terms.amount} × ${ItemStack(terms.item).hoverName.string}"),
        )
        return true
    }

    // ── Breaking ────────────────────────────────────────────────────────────────

    private fun sweep(server: MinecraftServer, now: Long = System.currentTimeMillis()) {
        debts.filter { it.state == DomainContract.ContractState.ACTIVE }
            .filter { (termsOf(it)?.deadline ?: 0L) <= now }
            .forEach { debt ->
                debt.breach()
                AbilityServices.messaging.tell(
                    debt.partyA,
                    Component.literal("☀ Строк Клятви минув — Сонце карає боржника"),
                )
            }
        debts.filter { it.state == DomainContract.ContractState.BREACHED }
            .forEach { debt -> server.playerList.getPlayer(debt.partyB)?.let { punish(it, debt) } }
    }

    /**
     * Divine Retribution, the plugin's measure: holy lightning, a wound armour does not stop, and
     * every power sealed for ten to fifteen minutes. The lightning is light only — it sets nothing
     * on fire; the wound is real magic damage, so a totem still answers it as a totem would.
     */
    private fun punish(debtor: ServerPlayer, debt: DomainContract) {
        debts.remove(debt)
        val level = debtor.level()
        EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED)?.let { bolt ->
            bolt.snapTo(debtor.x, debtor.y, debtor.z)
            bolt.setVisualOnly(true)
            level.addFreshEntity(bolt)
        }
        debtor.hurtServer(level, level.damageSources().magic(), RETRIBUTION_DAMAGE)

        val minutes = Random.nextInt(SEAL_MIN_MINUTES, SEAL_MAX_MINUTES + 1)
        AbilityServices.cooldowns.lockAll(debtor.uuid, minutes * 60)
        AbilityServices.statePump.pushNow(debtor.uuid)
        AbilityServices.messaging.tell(
            debtor,
            Component.literal("☀ Ви порушили Клятву. Сонце запечатало ваші сили на $minutes хв"),
        )
    }

    // ── Wiring ──────────────────────────────────────────────────────────────────

    private fun save(server: MinecraftServer) {
        server.overworld().setAttached(SAVED, debts.toList())
    }

    fun install() {
        SAVED

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            debts.clear()
            debts += server.overworld().getAttached(SAVED).orEmpty()
        }
        ServerLifecycleEvents.SERVER_STOPPING.register(::save)
        ServerLifecycleEvents.SERVER_STOPPED.register { debts.clear() }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (server.tickCount % SWEEP_TICKS == 0) {
                sweep(server)
                save(server)
            }
        }

        // A breach waits for its debtor; an open Oath reminds them it is still open.
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            debts.filter { it.partyB == player.uuid && it.state == DomainContract.ContractState.BREACHED }
                .forEach { punish(player, it) }
            debts.filter { it.partyB == player.uuid && it.state == DomainContract.ContractState.ACTIVE }
                .mapNotNull(::termsOf)
                .forEach { terms ->
                    val hours = ((terms.deadline - System.currentTimeMillis()) / HOUR_MILLIS).coerceAtLeast(0)
                    AbilityServices.messaging.tell(
                        player,
                        Component.literal("☀ Ваша Клятва: ${terms.amount} × ${ItemStack(terms.item).hoverName.string}, лишилось $hours год"),
                    )
                }
        }

        UseEntityCallback.EVENT.register { player, level, hand, entity, _ ->
            if (level.isClientSide || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown) {
                return@register InteractionResult.PASS
            }
            val debtor = player as? ServerPlayer ?: return@register InteractionResult.PASS
            val holder = entity as? ServerPlayer ?: return@register InteractionResult.PASS
            if (pay(debtor, holder)) InteractionResult.SUCCESS else InteractionResult.PASS
        }
    }

    /** Three real days — the plugin's `OATH_DAYS`. */
    private const val DEADLINE_MILLIS = 3L * 24 * 60 * 60 * 1000
    private const val HOUR_MILLIS = 60L * 60 * 1000
    private const val SWEEP_TICKS = 10 * TICKS_PER_SECOND
    private const val RETRIBUTION_DAMAGE = 12f
    private const val SEAL_MIN_MINUTES = 10
    private const val SEAL_MAX_MINUTES = 15

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault())
}
