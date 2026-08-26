package me.vangoo.domain.abilities

enum class AbilityType {
    /** Вимагає явної активації гравцем; коштує духовності й лишає кулдаун. */
    ACTIVE,

    /** Пасивка з перемикачем — гравець вмикає й вимикає сам. */
    TOGGLEABLE_PASSIVE,

    /** Пасивка, що працює завжди, поки здібність є в Потойбічного. */
    PERMANENT_PASSIVE,
    ;

    val isActive: Boolean get() = this == ACTIVE
}
