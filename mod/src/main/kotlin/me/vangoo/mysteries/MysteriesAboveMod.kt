package me.vangoo.mysteries

import me.vangoo.domain.valueobjects.Sequence
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object MysteriesAboveMod : ModInitializer {

    private val log = LoggerFactory.getLogger("mysteries-above")

    override fun onInitialize() {
        // Каркас: доводить, що модуль core (чисті правила) вантажиться з мода
        // й не тягне за собою нічого платформного.
        log.info("Mysteries Above: ядро на місці, стартова послідовність — {}", Sequence.starter())
    }
}
