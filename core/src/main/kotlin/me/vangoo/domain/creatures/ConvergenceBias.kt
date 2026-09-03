package me.vangoo.domain.creatures

/**
 * The Law of Convergence: biases the CHOICE of creature on a natural spawn towards the
 * player's pathway and the sequence he will need soon. It does not change the overall spawn
 * probability — it only redistributes it.
 *
 * @property pathway the player's pathway name (compared case-insensitively with [CreatureDefinition.pathway])
 * @property sequenceLevel the player's current sequence level (9 = weakest, 0 = strongest)
 */
data class ConvergenceBias(val pathway: String?, val sequenceLevel: Int)
