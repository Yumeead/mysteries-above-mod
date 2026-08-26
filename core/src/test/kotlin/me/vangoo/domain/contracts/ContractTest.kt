package me.vangoo.domain.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

internal class ContractTest {

    private val partyA: UUID = UUID.randomUUID()
    private val partyB: UUID = UUID.randomUUID()

    private fun peace() = Contract.propose(partyA, partyB, ContractTerm.PEACE, emptyMap())

    @Test
    fun peaceRequiresNoParams() {
        assertEquals(Contract.ContractState.PROPOSED, peace().state)
    }

    @Test
    fun debtRequiresAmountAndDeadline() {
        assertThrows(IllegalArgumentException::class.java) {
            Contract.propose(partyA, partyB, ContractTerm.DEBT, mapOf("amount" to "100"))
        }

        val contract = Contract.propose(
            partyA, partyB, ContractTerm.DEBT,
            mapOf("amount" to "100", "deadlineEpochMillis" to "999999999999"),
        )
        assertEquals(ContractTerm.DEBT, contract.term)
    }

    @Test
    fun partiesMustDiffer() {
        assertThrows(IllegalArgumentException::class.java) {
            Contract.propose(partyA, partyA, ContractTerm.PEACE, emptyMap())
        }
    }

    @Test
    fun signMovesProposedToActive() {
        val contract = peace()
        contract.sign()
        assertEquals(Contract.ContractState.ACTIVE, contract.state)
    }

    @Test
    fun cannotSignTwice() {
        val contract = peace()
        contract.sign()
        assertThrows(IllegalStateException::class.java) { contract.sign() }
    }

    @Test
    fun cannotSettleOrBreachBeforeSigning() {
        val contract = peace()
        assertThrows(IllegalStateException::class.java) { contract.settle() }
        assertThrows(IllegalStateException::class.java) { contract.breach() }
    }

    @Test
    fun breachIsTerminal() {
        val contract = peace()
        contract.sign()
        contract.breach()
        assertEquals(Contract.ContractState.BREACHED, contract.state)
        assertThrows(IllegalStateException::class.java) { contract.settle() }
        assertThrows(IllegalStateException::class.java) { contract.breach() }
    }

    @Test
    fun settleIsTerminal() {
        val contract = peace()
        contract.sign()
        contract.settle()
        assertEquals(Contract.ContractState.SETTLED, contract.state)
        assertThrows(IllegalStateException::class.java) { contract.breach() }
    }

    @Test
    fun involvesAndOther() {
        val contract = peace()
        assertTrue(contract.involves(partyA))
        assertTrue(contract.involves(partyB))
        assertFalse(contract.involves(UUID.randomUUID()))
        assertEquals(partyB, contract.other(partyA))
        assertEquals(partyA, contract.other(partyB))
        assertThrows(IllegalArgumentException::class.java) { contract.other(UUID.randomUUID()) }
    }

    @Test
    fun restoreRehydratesWithoutRegeneratingId() {
        val id = UUID.randomUUID()
        val restored = Contract.restore(
            id, partyA, partyB, ContractTerm.DEBT,
            mapOf("amount" to "50", "deadlineEpochMillis" to "123"),
            111L, Contract.ContractState.ACTIVE,
        )
        assertEquals(id, restored.id)
        assertEquals(Contract.ContractState.ACTIVE, restored.state)
        assertEquals(111L, restored.createdAtEpochMillis)
    }
}
