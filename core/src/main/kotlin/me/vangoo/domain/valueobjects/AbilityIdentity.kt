package me.vangoo.domain.valueobjects

data class AbilityIdentity(val id: String) {

    init {
        require(id.isNotBlank()) { "Ability ID cannot be empty" }
    }

    /**
     * Check if this ability can replace another based on identity
     *
     * @param other Other ability identity
     * @return true if they represent the same conceptual ability
     */
    fun canReplace(other: AbilityIdentity): Boolean = this == other

    override fun toString(): String = id

    companion object {
        /**
         * Create identity from string ID
         *
         * @param id Unique identifier (e.g., "scan_gaze")
         */
        fun of(id: String): AbilityIdentity = AbilityIdentity(id.lowercase().trim())
    }
}
