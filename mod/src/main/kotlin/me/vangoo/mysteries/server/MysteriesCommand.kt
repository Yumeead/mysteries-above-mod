package me.vangoo.mysteries.server

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.abilities.AbilityType
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
import me.vangoo.mysteries.item.ModItems
import me.vangoo.mysteries.pathways.ModPathways
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * `/mysteries` — all interaction with the mod until phase 11.
 *
 * Half the branches are temporary: `cast` and `select` will be replaced by the ability wheel,
 * `potion` and `mastery` by brewing and real mastery. But until those exist, the cycle "grant a
 * pathway → drink a potion → cast" has to be closed with something **in the game**, not in the
 * tests — and that is the whole point of phase 05.
 *
 * `status` and `abilities` will stay: a command that prints the state outlives any UI.
 */
object MysteriesCommand {

    /** Operator level: the branches that hand out power do not belong to players. */
    private val OPERATOR = Commands.hasPermission<CommandSourceStack>(Commands.LEVEL_GAMEMASTERS)

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
                    Commands.literal("choose").then(
                        Commands.argument("option", IntegerArgumentType.integer(1))
                            .executes { context -> choose(context) },
                    ),
                )
                .then(
                    Commands.literal("become").requires(OPERATOR).then(
                        pathwayArgument().then(
                            Commands.argument("sequence", IntegerArgumentType.integer(0, Pathway.SEQUENCE_COUNT - 1))
                                .executes { context -> become(context) },
                        ),
                    ),
                )
                .then(
                    Commands.literal("potion").requires(OPERATOR).then(
                        pathwayArgument().then(
                            Commands.argument("sequence", IntegerArgumentType.integer(0, Pathway.SEQUENCE_COUNT - 1))
                                .executes { context -> potion(context) },
                        ),
                    ),
                )
                .then(
                    Commands.literal("mastery").requires(OPERATOR).then(
                        Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                            .executes { context -> mastery(context) },
                    ),
                )
                .then(
                    Commands.literal("sanity").requires(OPERATOR).then(
                        Commands.argument("scale", IntegerArgumentType.integer(0, 100))
                            .executes { context -> sanity(context) },
                    ),
                )
                .then(Commands.literal("forget").requires(OPERATOR).executes(::forget)),
        )
    }

    // ── State ───────────────────────────────────────────────────────────────────

    private fun status(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        BeyonderStatus.push(player)
        // A full re-read: both the rare snapshot and the frequent one. Sending one extra packet
        // on an explicit request is cheaper than leaving the client with a stale list.
        BeyonderStatus.pushRoster(player)
        AbilityServices.statePump.pushNow(player.uuid)
        context.source.sendSuccess({ Component.literal("Стан надіслано клієнту.") }, false)
        return 1
    }

    private fun abilities(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val beyonder = AbilityServices.beyonders.of(player.uuid)
            ?: return fail(context, "Ви не Потойбічний")

        context.source.sendSuccess(
            {
                Component.literal(
                    "${beyonder.pathway.name} · Посл. ${beyonder.sequenceLevel} " +
                        "(${beyonder.pathway.sequenceName(beyonder.sequenceLevel)}) · " +
                        "духовність ${beyonder.spirituality} · засвоєння ${beyonder.mastery}",
                )
            },
            false,
        )

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

    /**
     * Answers whatever choice is pending for the player — the stand-in for a menu click until
     * phase 11. `option` is 1-based (as printed in chat), [PendingChoices.choose] takes the
     * 0-based index.
     */
    private fun choose(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val option = IntegerArgumentType.getInteger(context, "option")
        if (!AbilityServices.pendingChoices.choose(player.uuid, option - 1)) {
            return fail(context, "Немає на що відповідати")
        }
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
    private fun become(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val name = StringArgumentType.getString(context, "pathway")
        val pathway = ModPathways.byName(name)
            ?: return fail(context, "Невідомий шлях: $name")
        val sequence = Sequence(IntegerArgumentType.getInteger(context, "sequence"))

        Ascension.bestow(player, pathway, sequence)
        context.source.sendSuccess(
            {
                Component.literal(
                    "Ви — ${pathway.name} · Посл. ${sequence.level} " +
                        "(${pathway.sequenceName(sequence.level)})",
                )
            },
            false,
        )
        return 1
    }

    private fun potion(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val name = StringArgumentType.getString(context, "pathway")
        val pathway = ModPathways.byName(name)
            ?: return fail(context, "Невідомий шлях: $name")
        val sequence = Sequence(IntegerArgumentType.getInteger(context, "sequence"))

        val stack = ModItems.potionOf(pathway, sequence)
        if (!player.addItem(stack)) {
            player.drop(stack, false)
        }
        context.source.sendSuccess(
            { Component.literal("Видано зілля: ${pathway.name} · Посл. ${sequence.level}") },
            false,
        )
        return 1
    }

    /**
     * Sets the mastery directly.
     *
     * It exists exactly because mastery grows from casts (`MasteryProgressionCalculator`), and
     * topping it up to 100 % just to test an ascension is tens of minutes of casting into the void.
     */
    private fun mastery(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val beyonder = AbilityServices.beyonders.of(player.uuid)
            ?: return fail(context, "Ви не Потойбічний")

        beyonder.setMastery(Mastery.of(DoubleArgumentType.getDouble(context, "percent")))
        // Mastery moves the spirituality ceiling — that is already a number from the state snapshot.
        BeyonderStatus.push(player)
        AbilityServices.statePump.pushNow(player.uuid)
        context.source.sendSuccess({ Component.literal("Засвоєння: ${beyonder.mastery}") }, false)
        return 1
    }

    /**
     * Sets the sanity loss directly.
     *
     * It exists for the same reason as `mastery`: on its own it grows by two points per cast on
     * an empty tank, so reaching the whisper and the screen chain "honestly" is hundreds of casts.
     */
    private fun sanity(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val beyonder = AbilityServices.beyonders.of(player.uuid)
            ?: return fail(context, "Ви не Потойбічний")

        val wanted = SanityLoss.of(IntegerArgumentType.getInteger(context, "scale"))
        // There is deliberately no direct setter in the domain: sanity moves through events, not assignment.
        val delta = wanted.scale - beyonder.sanityLoss.scale
        if (delta > 0) beyonder.increaseSanityLoss(delta) else beyonder.decreaseSanityLoss(-delta)

        AbilityServices.statePump.pushNow(player.uuid)
        context.source.sendSuccess({ Component.literal("Втрата глузду: ${beyonder.sanityLoss}") }, false)
        return 1
    }

    private fun forget(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        if (AbilityServices.beyonders.of(player.uuid) == null) {
            return fail(context, "Ви й так не Потойбічний")
        }
        Ascension.forget(player)
        context.source.sendSuccess({ Component.literal("Силу забуто.") }, false)
        return 1
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

    private fun identityArgument(context: CommandContext<CommandSourceStack>): AbilityIdentity =
        AbilityIdentity.of(StringArgumentType.getString(context, "ability"))

    private fun fail(context: CommandContext<CommandSourceStack>, message: String): Int {
        context.source.sendFailure(Component.literal(message))
        return 0
    }
}
