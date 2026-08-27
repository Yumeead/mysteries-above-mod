package me.vangoo.mysteries.beyonder

import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.item.PotionBrew
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Зілля випите — що з цього виходить.
 *
 * Спадкоємець плагінного `PathwayPotionListener`, але розділений навпіл: [refusal]
 * відповідає **до** того, як пляшку піднесено до рота, [ascend] діє **після**. У плагіні
 * це був один хендлер, який скасовував подію на півдорозі; тут скасовувати нема чого —
 * предмет, який не можна випити, і не починає пити.
 *
 * Правила «чи можна» живуть у `core` ([Beyonder.canConsumePotion]); тут лише
 * формулювання відмови й наслідки у світі.
 */
object Ascension {

    /** `null` — зілля можна пити. Інакше — чому ні, українською. */
    fun refusal(player: ServerPlayer, brew: PotionBrew): Component? {
        val pathway = brew.resolvePathway()
            ?: return Component.literal("Шлях «${brew.pathway}» ще не існує в цьому світі")

        val beyonder = AbilityServices.beyonders.of(player.uuid)
            ?: return firstPotionRefusal(brew)

        return advancementRefusal(beyonder, pathway, brew.asSequence())
    }

    /**
     * Робить те, на що зілля дає право. Кличеться лише після [refusal] `== null` —
     * інакше падає, бо це вже помилка виклику, а не відмова гравцеві.
     */
    fun ascend(player: ServerPlayer, brew: PotionBrew) {
        val pathway = checkNotNull(brew.resolvePathway()) { "Зілля невідомого шляху: ${brew.pathway}" }
        val existing = AbilityServices.beyonders.of(player.uuid)

        if (existing == null) {
            initiate(player, pathway)
        } else {
            advance(player, existing, pathway)
        }

        aftertaste(player)
        BeyonderStatus.push(player)
        // Список здібностей став іншим — ростер летить саме тут і більше ніде.
        BeyonderStatus.pushRoster(player)
        AbilityServices.statePump.pushNow(player.uuid)
    }

    /** Гравець більше не Потойбічний: усе, що тримав мод на нього, знімається. */
    fun forget(player: ServerPlayer) {
        AbilityServices.passives.deactivate(player)
        AbilityServices.passives.forget(player.uuid)
        // `passives.deactivate` знімає підписки лише пасивок; те, на що підписалась
        // активна здібність у `cast`, лишилось би жити на вже-не-Потойбічному.
        AbilityServices.events.forget(player.uuid)
        AbilityServices.scheduling.stopAll(player.uuid)
        AbilityServices.cooldowns.forget(player.uuid)
        AbilityServices.selection.forget(player.uuid)
        AbilityServices.beyonders.forget(player.uuid)
        AbilityServices.statePump.forget(player.uuid)
        BeyonderStatus.push(player)
        // Порожній ростер — і є повідомлення «ти більше не Потойбічний»: стану після
        // нього не летить нічого, бо стан має сенс лише в того, хто ним є.
        BeyonderStatus.pushRoster(player)
    }

    // ── Перше зілля ─────────────────────────────────────────────────────────────

    private fun firstPotionRefusal(brew: PotionBrew): Component? =
        if (brew.sequence == STARTING_SEQUENCE) {
            null
        } else {
            Component.literal("Краще почати з Послідовності $STARTING_SEQUENCE…")
        }

    private fun initiate(player: ServerPlayer, pathway: Pathway) {
        val beyonder = Beyonder(player.uuid, Sequence.starter(), pathway)
        AbilityServices.beyonders.register(beyonder)
        AbilityServices.passives.activate(player)

        player.sendSystemMessage(
            Component.literal("Вітаємо у світі Потойбічних, ${player.name.string}"),
        )
        player.sendSystemMessage(
            Component.literal(
                "Шлях: ${pathway.name} · Посл. ${beyonder.sequenceLevel} " +
                    "(${pathway.sequenceName(beyonder.sequenceLevel)})",
            ),
        )
    }

    // ── Піднесення ──────────────────────────────────────────────────────────────

    /**
     * Розкладає `canConsumePotion` на три окремі причини.
     *
     * У плагіні відмова була одна на всі випадки — «Ви не можете споживати цей еліксір!»
     * — і гравець не дізнавався, що саме не так: чужа група, не та Послідовність чи
     * недобране засвоєння.
     */
    private fun advancementRefusal(
        beyonder: Beyonder,
        pathway: Pathway,
        sequence: Sequence,
    ): Component? {
        if (pathway.group != beyonder.pathway.group) {
            return Component.literal(
                "Це зілля групи «${pathway.group.displayName}», а ваш шлях — " +
                    "«${beyonder.pathway.group.displayName}»",
            )
        }
        if (beyonder.sequence.level != sequence.level + 1) {
            return Component.literal(
                "Наступна ваша Послідовність — ${beyonder.sequence.level - 1}, а не ${sequence.level}",
            )
        }
        if (!beyonder.mastery.canAdvance()) {
            return Component.literal("Засвоєння ще не повне: ${beyonder.mastery}")
        }
        // `canConsumePotion` цього не знає, а `advanceWithPathwayChange` — вимагає, і
        // без перевірки тут ковток чужого шляху з Посл. 8 упав би `check()` усередині
        // домену. Особистість складається на першому ж піднесенні, далі шлях уже свій.
        if (pathway.name != beyonder.pathway.name &&
            beyonder.sequence.level < Beyonder.PATHWAY_CHANGE_MIN_SEQUENCE
        ) {
            return Component.literal(
                "Змінити шлях можна лише з Послідовності ${Beyonder.PATHWAY_CHANGE_MIN_SEQUENCE}",
            )
        }
        return null
    }

    private fun advance(player: ServerPlayer, beyonder: Beyonder, pathway: Pathway) {
        val changingPathway = pathway.name != beyonder.pathway.name
        if (changingPathway) beyonder.advanceWithPathwayChange(pathway) else beyonder.advance()

        // Список здібностей став іншим — постійні пасивки нової Послідовності треба
        // активувати, втрачені — попрощатись, а тоглі згасити.
        AbilityServices.passives.refresh(player)
        AbilityServices.selection.forget(player.uuid)

        player.sendSystemMessage(
            Component.literal(
                "Ви піднеслись до Послідовності ${beyonder.sequenceLevel} — " +
                    "${pathway.sequenceName(beyonder.sequenceLevel)}",
            ),
        )
        if (changingPathway) {
            player.sendSystemMessage(Component.literal("Ваш шлях відтепер — ${pathway.name}"))
        }
    }

    // ── Наслідки у світі ────────────────────────────────────────────────────────

    /** Зілля ламає тіло, перш ніж дати силу — сліпота, нудота й спалах порталу. */
    private fun aftertaste(player: ServerPlayer) {
        player.addEffect(MobEffectInstance(MobEffects.BLINDNESS, 100, 0))
        player.addEffect(MobEffectInstance(MobEffects.NAUSEA, 200, 1))

        val level = player.level()
        level.playSound(
            null, player.x, player.y, player.z,
            SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.8f,
        )
        level.sendParticles(
            ParticleTypes.PORTAL,
            player.x, player.y + 1.0, player.z,
            50, 0.5, 1.0, 0.5, 0.3,
        )
    }

    /** Перше зілля — завжди Посл. 9: сильніші тіло без підготовки просто не втримає. */
    private const val STARTING_SEQUENCE = 9
}
