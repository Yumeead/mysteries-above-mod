package me.vangoo.domain.forage

/** Один кандидат фореджу: id допоміжного інгредієнта + вага у таблиці біому. */
data class ForageEntry(val ingredientId: String, val weight: Int)
