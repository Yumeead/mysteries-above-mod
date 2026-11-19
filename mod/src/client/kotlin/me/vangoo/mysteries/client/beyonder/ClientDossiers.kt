package me.vangoo.mysteries.client.beyonder

import me.vangoo.mysteries.net.payload.DossiersPayload
import java.util.UUID

/**
 * The Sheriff's files as the server last sent them (`DOSSIERS`): the footprints follow those in it, and the book shows
 * «Справи» while it is not empty. Nobody else ever receives the packet, so for them it stays empty.
 */
object ClientDossiers {

    var entries: List<DossiersPayload.Entry> = emptyList()
        private set

    /** Those whose footprints are drawn: the living ones. */
    var followed: Set<UUID> = emptySet()
        private set

    fun accept(payload: DossiersPayload) {
        entries = payload.entries
        followed = payload.entries.filter { !it.file.dead }.mapTo(HashSet()) { it.file.id }
    }

    fun clear() {
        entries = emptyList()
        followed = emptySet()
    }
}
