package me.vangoo.mysteries.server

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Mastery
import me.vangoo.domain.valueobjects.SanityLoss
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.Ability
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.beyonder.AbilityRequests
import me.vangoo.mysteries.beyonder.Ascension
import me.vangoo.mysteries.beyonder.BeyonderStatus
import me.vangoo.mysteries.creature.CreatureRegistries
import me.vangoo.mysteries.creature.Creatures
import net.minecraft.commands.arguments.IdentifierArgument
import me.vangoo.mysteries.item.ModItems
import me.vangoo.mysteries.pathways.ModPathways
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/**
 * `/mysteries` — the operator's tools and a few debugging branches.
 *
 * Half the branches are temporary: `cast` and `select` stay only for debugging (the wheel and
 * the book are the real way), `potion` and `mastery` will be replaced by brewing and real mastery. But until those exist, the cycle "grant a
 * pathway → drink a potion → cast" has to be closed with something **in the game**, not in the
 * tests — and that is the whole point of phase 05.
 *
 * `status` and `abilities` will stay: a command that prints the state outlives any UI.
 */
object MysteriesCommand {

    /** Operator level: the branches that hand out power do not belong to players. */
    private val OPERATOR = Commands.hasPermission<CommandSourceStack>(Commands.LEVEL_GAMEMASTERS)

    private const val NOT_BEYONDER = "не Потойбічний"

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("mysteries")
                .then(Commands.literal("status").executes(::status))
                .then(Commands.literal("abilities").executes(::abilities))
                .then(
                    Commands.literal("cast").then(
                        abilityArgument().executes { context -> cast(context) },
                    ),
                )
                .then(
                    Commands.literal("select").then(
                        abilityArgument().executes { context -> select(context) },
                    ),
                )
                .then(
                    Commands.literal("become").requires(OPERATOR).then(
                        pathwayArgument().then(sequenceArgument().forPlayers(::become)),
                    ),
                )
                .then(
                    Commands.literal("potion").requires(OPERATOR).then(
                        pathwayArgument().then(sequenceArgument().forPlayers(::potion)),
                    ),
                )
                .then(
                    Commands.literal("characteristic").requires(OPERATOR).then(
                        pathwayArgument().then(sequenceArgument().forPlayers(::characteristic)),
                    ),
                )
                .then(
                    Commands.literal("mastery").requires(OPERATOR).then(
                        Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0)).forPlayers(::mastery),
                    ),
                )
                .then(
                    Commands.literal("sanity").requires(OPERATOR).then(
                        Commands.argument("scale", IntegerArgumentType.integer(0, 100)).forPlayers(::sanity),
                    ),
                )
                .then(Commands.literal("forget").requires(OPERATOR).forPlayers(::forget))
                .then(
                    Commands.literal("creature").requires(OPERATOR).then(
                        Commands.literal("spawn").then(
                            creatureArgument()
                                .executes { spawnCreature(it, 1) }
                                .then(
                                    Commands.argument("count", IntegerArgumentType.integer(1, MAX_CREATURES))
                                        .executes { spawnCreature(it, IntegerArgumentType.getInteger(it, "count")) },
                                ),
                        ),
                    ),
                ),
        )
    }

    // ── Creatures ───────────────────────────────────────────────────────────────

    private const val MAX_CREATURES = 32

    /**
     * The creature's registry id. Suggested by the server (`SharedSuggestionProvider` from a custom
     * provider): the creature registry is not synced, so a client could not suggest it on its own.
     */
    private fun creatureArgument() =
        Commands.argument("creature", IdentifierArgument.id()).suggests { context, builder ->
            val keys = context.source.server.registryAccess().lookupOrThrow(CreatureRegistries.CREATURE).listElementIds()
            SharedSuggestionProvider.suggestResource(keys.map { it.identifier() }, builder)
        }

    /** Creatures at the speaker's feet — the way to meet one without walking three thousand blocks. */
    private fun spawnCreature(context: CommandContext<CommandSourceStack>, count: Int): Int {
        val source = context.source
        val key = Creatures.creatureKey(IdentifierArgument.getId(context, "creature"))
        val made = (1..count).count { Creatures.spawn(source.level, key, source.position) != null }
        if (made == 0) throw SimpleCommandExceptionType(Component.literal("Невідома істота: ${key.identifier()}")).create()
        source.sendSuccess({ Component.literal("Викликано: ${key.identifier()} ×$made") }, true)
        return made
    }

    // ── Targets ─────────────────────────────────────────────────────────────────

    /** What one target came to: the line for whoever ran the command, and whether it counts. */
    private class Reply(val text: String, val ok: Boolean)

    private fun done(text: String) = Reply(text, true)
    private fun refused(text: String) = Reply(text, false)

    /**
     * Ends an operator branch with an optional trailing `[targets]`, as vanilla `/clear` and `/kill`
     * do: without it the branch acts on whoever typed it, with it on any selector — `@a`, a name,
     * `@p` from a command block. The plugin took a bare name and only an online one; the selector
     * also makes the branch usable from the console.
     */
    private fun <T : ArgumentBuilder<CommandSourceStack, T>> T.forPlayers(
        action: (CommandContext<CommandSourceStack>, ServerPlayer) -> Reply,
    ): T = executes { context -> perform(context, listOf(context.source.playerOrException), action) }
        .then(
            Commands.argument("targets", EntityArgument.players())
                .executes { context -> perform(context, EntityArgument.getPlayers(context, "targets"), action) },
        )

    private fun perform(
        context: CommandContext<CommandSourceStack>,
        targets: Collection<ServerPlayer>,
        action: (CommandContext<CommandSourceStack>, ServerPlayer) -> Reply,
    ): Int {
        val self = context.source.player
        var count = 0
        for (target in targets) {
            val reply = action(context, target)
            val text = Component.literal(if (target == self) reply.text else "${target.name.string}: ${reply.text}")
            if (reply.ok) {
                count++
                context.source.sendSuccess({ text }, false)
            } else {
                context.source.sendFailure(text)
            }
        }
        return count
    }

    // ── State ───────────────────────────────────────────────────────────────────

    private fun status(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        // A full re-read: both the rare snapshot and the frequent one. Sending one extra packet
        // on an explicit request is cheaper than leaving the client with a stale list.
        BeyonderStatus.pushRoster(player)
        AbilityServices.statePump.pushNow(player.uuid)
        // Printed as well as sent: a command that shows the state outlives any UI and is the
        // first thing to reach for when the network is what is being debugged.
        val beyonder = AbilityServices.beyonders.of(player.uuid)
        context.source.sendSuccess({ beyonder?.let(::summary) ?: Component.literal("Ви не Потойбічний") }, false)
        return 1
    }

    private fun summary(beyonder: Beyonder): Component = Component.literal(
        "${beyonder.pathway.name} · Посл. ${beyonder.sequenceLevel} " +
            "(${beyonder.pathway.sequenceName(beyonder.sequenceLevel)}) · " +
            "духовність ${beyonder.spirituality} · засвоєння ${beyonder.mastery}",
    )

    private fun abilities(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val beyonder = AbilityServices.beyonders.of(player.uuid)
            ?: return fail(context, "Ви не Потойбічний")

        context.source.sendSuccess({ summary(beyonder) }, false)

        val selected = AbilityServices.selection.of(player.uuid)
        beyonder.abilities.forEach { spec ->
            context.source.sendSuccess({ line(player, spec, selected) }, false)
        }
        return beyonder.abilities.size
    }

    private fun line(player: ServerPlayer, spec: AbilitySpec, selected: AbilityIdentity?): Component {
        val name = (spec as? Ability)?.displayName?.string ?: spec.identity.id
        val marker = if (spec.identity == selected) "▶ " else "  "
        val remaining = AbilityServices.cooldowns.remainingSeconds(player.uuid, spec.identity)
        val state = when {
            remaining > 0 -> "відкат ${remaining} с"
            spec.type == AbilityType.ACTIVE -> "готова · ${spec.spiritualityCost} духовності"
            else -> "пасивна"
        }
        return Component.literal("$marker$name [${spec.identity}] — $state")
    }

    // ── Cast ────────────────────────────────────────────────────────────────────

    private fun cast(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        AbilityRequests.cast(player, identityArgument(context))
        // The executor has already told the player what came of the cast — no need to repeat it in chat.
        return 1
    }

    private fun select(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val identity = identityArgument(context)
        if (!AbilityRequests.select(player, identity)) {
            return fail(context, "У вас немає здібності «$identity»")
        }
        context.source.sendSuccess({ Component.literal("Обрано: $identity") }, false)
        return 1
    }

    // ── Operator ────────────────────────────────────────────────────────────────

    /**
     * Grants a pathway and a Sequence at once, without the potion chain.
     *
     * It exists for the same reason as `mastery`: walking the ladder honestly is a dozen
     * commands per Sequence, and an ability has to be tested after every edit. This path is not
     * available to a player — the branch is operator-only.
     */
    private fun become(context: CommandContext<CommandSourceStack>, player: ServerPlayer): Reply {
        val pathway = pathwayArgument(context)
        val sequence = sequenceArgument(context)

        Ascension.bestow(player, pathway, sequence)
        return done("${pathway.name} · Посл. ${sequence.level} (${pathway.sequenceName(sequence.level)})")
    }

    private fun potion(context: CommandContext<CommandSourceStack>, player: ServerPlayer): Reply {
        val pathway = pathwayArgument(context)
        val sequence = sequenceArgument(context)

        give(player, ModItems.potionOf(pathway, sequence))
        return done("видано зілля: ${pathway.name} · Посл. ${sequence.level}")
    }

    /**
     * Hands out a Characteristic — the plugin's `/characteristic give`.
     *
     * Until brewing lands the only honest source is a marionette's death, and a recipe cannot be
     * tested by killing a puppet each time. No amount argument: the item stacks to one.
     */
    private fun characteristic(context: CommandContext<CommandSourceStack>, player: ServerPlayer): Reply {
        val pathway = pathwayArgument(context)
        val sequence = sequenceArgument(context)

        give(player, ModItems.characteristicOf(pathway.name, sequence.level))
        return done("видано Характеристику: ${pathway.name} · Посл. ${sequence.level}")
    }

    /** Into the inventory, or at the feet when it is full — the item is never lost. */
    private fun give(player: ServerPlayer, stack: ItemStack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false)
        }
    }

    /**
     * Sets the mastery directly.
     *
     * It exists exactly because mastery grows from casts (`MasteryProgressionCalculator`), and
     * topping it up to 100 % just to test an ascension is tens of minutes of casting into the void.
     */
    private fun mastery(context: CommandContext<CommandSourceStack>, player: ServerPlayer): Reply {
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: return refused(NOT_BEYONDER)

        beyonder.setMastery(Mastery.of(DoubleArgumentType.getDouble(context, "percent")))
        // Mastery moves the spirituality ceiling — that is already a number from the state snapshot.
        AbilityServices.statePump.pushNow(player.uuid)
        return done("засвоєння ${beyonder.mastery}")
    }

    /**
     * Sets the sanity loss directly.
     *
     * It exists for the same reason as `mastery`: on its own it grows by two points per cast on
     * an empty tank, so reaching the whisper and the screen chain "honestly" is hundreds of casts.
     */
    private fun sanity(context: CommandContext<CommandSourceStack>, player: ServerPlayer): Reply {
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: return refused(NOT_BEYONDER)

        val wanted = SanityLoss.of(IntegerArgumentType.getInteger(context, "scale"))
        // There is deliberately no direct setter in the domain: sanity moves through events, not assignment.
        val delta = wanted.scale - beyonder.sanityLoss.scale
        if (delta > 0) beyonder.increaseSanityLoss(delta) else beyonder.decreaseSanityLoss(-delta)

        AbilityServices.statePump.pushNow(player.uuid)
        return done("втрата глузду ${beyonder.sanityLoss}")
    }

    @Suppress("UNUSED_PARAMETER")
    private fun forget(context: CommandContext<CommandSourceStack>, player: ServerPlayer): Reply {
        if (AbilityServices.beyonders.of(player.uuid) == null) return refused(NOT_BEYONDER)
        Ascension.forget(player)
        return done("силу забуто")
    }

    // ── Arguments ───────────────────────────────────────────────────────────────

    private fun abilityArgument() =
        Commands.argument("ability", StringArgumentType.word()).suggests { context, builder ->
            val player = context.source.player
            val owned = player?.let { AbilityServices.beyonders.of(it.uuid) }
                ?.abilities.orEmpty()
                .map { it.identity.id }
            SharedSuggestionProvider.suggest(owned, builder)
        }

    private fun pathwayArgument() =
        Commands.argument("pathway", StringArgumentType.word()).suggests { _, builder ->
            SharedSuggestionProvider.suggest(ModPathways.names, builder)
        }

    /** Thrown rather than replied: an unknown pathway is the same mistake for every target. */
    private fun pathwayArgument(context: CommandContext<CommandSourceStack>): Pathway {
        val name = StringArgumentType.getString(context, "pathway")
        return ModPathways.byName(name)
            ?: throw SimpleCommandExceptionType(Component.literal("Невідомий шлях: $name")).create()
    }

    private fun sequenceArgument() =
        Commands.argument("sequence", IntegerArgumentType.integer(0, Pathway.SEQUENCE_COUNT - 1))

    private fun sequenceArgument(context: CommandContext<CommandSourceStack>) =
        Sequence(IntegerArgumentType.getInteger(context, "sequence"))

    private fun identityArgument(context: CommandContext<CommandSourceStack>): AbilityIdentity =
        AbilityIdentity.of(StringArgumentType.getString(context, "ability"))

    private fun fail(context: CommandContext<CommandSourceStack>, message: String): Int {
        context.source.sendFailure(Component.literal(message))
        return 0
    }
}
