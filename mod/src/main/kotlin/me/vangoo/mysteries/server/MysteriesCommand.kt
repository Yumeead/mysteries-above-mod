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
 * `/mysteries` — уся взаємодія з модом до фази 11.
 *
 * Половина гілок — тимчасові: `cast` і `select` замінить колесо здібностей, `potion` і
 * `mastery` — варіння та справжнє засвоєння. Але поки їх немає, цикл «видав шлях →
 * випив зілля → скастував» треба чимось замкнути **в грі**, а не в тестах — це і є вся
 * суть фази 05.
 *
 * `status` і `abilities` лишаться: команда, що друкує стан, переживає будь-який UI.
 */
object MysteriesCommand {

    /** Рівень оператора: гілки, що роздають силу, гравцям не належать. */
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

    // ── Стан ────────────────────────────────────────────────────────────────────

    private fun status(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        BeyonderStatus.push(player)
        // Повне перечитування: і рідкісний знімок, і частий. Дешевше надіслати зайвий
        // пакет на явне прохання, ніж лишити клієнт із застарілим списком.
        BeyonderStatus.pushRoster(player)
        AbilityServices.statePump.pushNow(player.uuid)
        context.source.sendSuccess({ Component.literal("Стан надіслано на клієнт.") }, false)
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
            remaining > 0 -> "перепочинок ${remaining} с"
            spec.type == AbilityType.ACTIVE -> "готова · ${spec.spiritualityCost} духовності"
            else -> "пасивна"
        }
        return Component.literal("$marker$name [${spec.identity}] — $state")
    }

    // ── Каст ────────────────────────────────────────────────────────────────────

    private fun cast(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        AbilityRequests.cast(player, identityArgument(context))
        // Про наслідки касту гравцеві вже сказав виконавець — дублювати в чат нема потреби.
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

    // ── Оператор ────────────────────────────────────────────────────────────────

    /**
     * Видає шлях і Послідовність одразу, без ланцюга зілль.
     *
     * Існує з тієї самої причини, що й `mastery`: пройти драбину чесно — це десяток
     * команд на кожну Послідовність, а перевіряти здібність доводиться після кожної
     * правки. Гравцеві цей шлях недоступний — гілка операторська.
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
     * Виставляє засвоєння напряму.
     *
     * Існує рівно тому, що засвоєння росте з кастів (`MasteryProgressionCalculator`), і
     * добити його до 100 % ради перевірки піднесення — це десятки хвилин каста в порожнечу.
     */
    private fun mastery(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val beyonder = AbilityServices.beyonders.of(player.uuid)
            ?: return fail(context, "Ви не Потойбічний")

        beyonder.setMastery(Mastery.of(DoubleArgumentType.getDouble(context, "percent")))
        // Засвоєння рухає стелю духовності — це вже число зі знімка стану.
        BeyonderStatus.push(player)
        AbilityServices.statePump.pushNow(player.uuid)
        context.source.sendSuccess({ Component.literal("Засвоєння: ${beyonder.mastery}") }, false)
        return 1
    }

    /**
     * Виставляє розхитаність розуму напряму.
     *
     * Існує з тієї ж причини, що й `mastery`: сама вона росте по два пункти за каст на
     * порожньому баку, тож дійти до шепоту й екранного ланцюга «чесно» — це сотні кастів.
     */
    private fun sanity(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val beyonder = AbilityServices.beyonders.of(player.uuid)
            ?: return fail(context, "Ви не Потойбічний")

        val wanted = SanityLoss.of(IntegerArgumentType.getInteger(context, "scale"))
        // Прямого сеттера в домені немає навмисно: глузд рухається подіями, не присвоєнням.
        val delta = wanted.scale - beyonder.sanityLoss.scale
        if (delta > 0) beyonder.increaseSanityLoss(delta) else beyonder.decreaseSanityLoss(-delta)

        AbilityServices.statePump.pushNow(player.uuid)
        context.source.sendSuccess({ Component.literal("Глузд: ${beyonder.sanityLoss}") }, false)
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

    // ── Аргументи ───────────────────────────────────────────────────────────────

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
