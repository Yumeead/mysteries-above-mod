package me.vangoo.domain.contracts

/** A term of a Sun contract (Seq 6, "Magic Notarization"). The parameters of each term — see [Contract]. */
enum class ContractTerm {
    /** The parties do not attack each other. A breaker is punished by Divine Retribution. */
    PEACE,

    /**
     * `partyB` undertakes to perform an action (e.g. deliver items) before a deadline.
     * In the UI it is "Клятва"; the parameters are `amount`/`deadlineEpochMillis` (+ an
     * optional `item` description). Detecting fulfilment is a future quest integration; for
     * now a breach only happens when the deadline passes.
     */
    DEBT,
}
