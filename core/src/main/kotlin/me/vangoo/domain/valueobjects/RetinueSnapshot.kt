package me.vangoo.domain.valueobjects

/**
 * Персистентний знімок почту нежиті одного власника (Death, Посл. 6): пишеться при виході з
 * серверу, читається при вході. [guardPost] ненульовий лише якщо останній наказ був
 * «стерегти це місце» — `FOLLOW`/`ATTACK` до диску не доїжджають, бо ціль наказу все
 * одно вже невалідна після relog/рестарту.
 */
data class RetinueSnapshot(
    val servants: List<RetinueServant>,
    val hidden: Boolean,
    val guardPost: RetinuePoint?,
)
