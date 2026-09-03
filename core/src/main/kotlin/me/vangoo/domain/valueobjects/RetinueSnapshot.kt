package me.vangoo.domain.valueobjects

/**
 * A persistent snapshot of one owner's undead retinue (Death, Seq. 6): written on leaving the
 * server, read on joining. [guardPost] is non-null only if the last order was "guard this
 * place" — `FOLLOW`/`ATTACK` never reach the disk, because the order's target is invalid
 * after a relog/restart anyway.
 */
data class RetinueSnapshot(
    val servants: List<RetinueServant>,
    val hidden: Boolean,
    val guardPost: RetinuePoint?,
)
