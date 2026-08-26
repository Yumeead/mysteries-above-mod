package me.vangoo.domain.contracts

import java.util.UUID

/**
 * Обопільний контракт (Sun, Seq 6): [partyA] пропонує, [partyB] засвідчує згодою
 * (ефект-шар збирає її через стеження за присіданням, як Telepathy). Persisted лише
 * ПІСЛЯ підпису ([sign]) — незасвідчена пропозиція живе тільки в пам'яті сервісу
 * контрактів і не переживає рестарт.
 */
class Contract private constructor(
    val id: UUID,
    val partyA: UUID,
    val partyB: UUID,
    val term: ContractTerm,
    params: Map<String, String>,
    val createdAtEpochMillis: Long,
    state: ContractState,
) {
    enum class ContractState { PROPOSED, ACTIVE, SETTLED, BREACHED }

    val params: Map<String, String> = params.toMap()

    var state: ContractState = state
        private set

    init {
        require(partyA != partyB) { "Contract parties must differ" }
        validateParams(term, params)
    }

    fun sign() {
        requireState(ContractState.PROPOSED)
        state = ContractState.ACTIVE
    }

    fun settle() {
        requireState(ContractState.ACTIVE)
        state = ContractState.SETTLED
    }

    fun breach() {
        requireState(ContractState.ACTIVE)
        state = ContractState.BREACHED
    }

    private fun requireState(expected: ContractState) {
        check(state == expected) { "Contract $id must be $expected but is $state" }
    }

    fun involves(player: UUID): Boolean = partyA == player || partyB == player

    fun other(player: UUID): UUID = when (player) {
        partyA -> partyB
        partyB -> partyA
        else -> throw IllegalArgumentException("Player $player is not a party of contract $id")
    }

    companion object {
        fun propose(
            partyA: UUID,
            partyB: UUID,
            term: ContractTerm,
            params: Map<String, String>,
        ): Contract = Contract(
            UUID.randomUUID(), partyA, partyB, term, params,
            System.currentTimeMillis(), ContractState.PROPOSED,
        )

        /** Гідрація з persisted-стану. */
        fun restore(
            id: UUID,
            partyA: UUID,
            partyB: UUID,
            term: ContractTerm,
            params: Map<String, String>,
            createdAtEpochMillis: Long,
            state: ContractState,
        ): Contract = Contract(id, partyA, partyB, term, params, createdAtEpochMillis, state)

        private fun validateParams(term: ContractTerm, params: Map<String, String>) {
            when (term) {
                ContractTerm.PEACE -> Unit // без додаткових параметрів
                ContractTerm.DEBT -> requireKeys(term, params, "amount", "deadlineEpochMillis")
            }
        }

        private fun requireKeys(term: ContractTerm, params: Map<String, String>, vararg keys: String) {
            for (key in keys) {
                require(params.containsKey(key)) { "Contract term $term requires param \"$key\"" }
            }
        }
    }
}
