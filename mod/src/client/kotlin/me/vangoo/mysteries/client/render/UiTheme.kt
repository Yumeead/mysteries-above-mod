package me.vangoo.mysteries.client.render

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB

/**
 * Палітра мода й чотири примітиви, з яких складені всі його поверхні.
 *
 * Існує заради того, щоб HUD (M3), колесо (M4) і книга (M6) виглядали одним модом, а
 * не трьома. Тричі скопійована палітра — це те, з чого UI починає розповзатись:
 * перший же підбір відтінку робиться в одному файлі, і два інші лишаються з давнім.
 *
 * Тло майже чорне з прозорістю, рамки волосяні, кольору потьмянілого золота. Колір
 * шляху сюди не входить навмисно — він приходить ззовні й з'являється рівно у двох
 * місцях на весь UI: рамка картки під курсором і волосок під HUD. Окультне читається
 * зі стриманості, а не з кількості ефектів.
 *
 * Стану тут немає: усе — чисті функції від переданих чисел.
 */
object UiTheme {

    /** Тло панелей. */
    const val PANEL: Int = 0x0A0A0F

    /** Потьмяніле золото: рамки, заповнена частина барів, акценти. */
    const val ACCENT: Int = 0xC8A44B

    /** Те саме золото, приглушене: ділянка бару, яку ось-ось витратять. */
    const val ACCENT_DEEP: Int = 0x6E5824

    const val TEXT: Int = 0xEDE8DC

    const val MUTED: Int = 0xB0AAA0

    /** Бракує духовності, зірваний каст — усе, що вимагає уваги. */
    const val CRITICAL: Int = 0xC46A55

    /** Незаповнена частина бару й тло бейджа. */
    const val TRACK: Int = 0x2A2C39

    /** Висота бейджа: рядок плюс піксель зверху й знизу. */
    const val BADGE_HEIGHT: Int = 11

    private const val BADGE_PADDING = 3
    private const val PANEL_ALPHA = 1.0f
    private const val FRAME_ALPHA = 0.55f
    private const val TRACK_ALPHA = 0.85f
    private const val VEIL_ALPHA = 0.62f

    /** RGB плюс окрема прозорість. Кольори тут — без альфи, щоб її множили в місці малювання. */
    fun argb(rgb: Int, alpha: Float): Int =
        ARGB.color(
            (alpha.coerceIn(0f, 1f) * 255f).toInt(),
            (rgb shr 16) and 0xFF,
            (rgb shr 8) and 0xFF,
            rgb and 0xFF,
        )

    /** Панель: темне тло й волосяна золота рамка. */
    fun panel(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, alpha: Float) {
        graphics.fill(x, y, x + width, y + height, argb(PANEL, PANEL_ALPHA * alpha))
        graphics.outline(x, y, width, height, argb(ACCENT, FRAME_ALPHA * alpha))
    }

    /** Волосок в один піксель — єдине місце, куди пускається колір шляху. */
    fun hairline(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, rgb: Int, alpha: Float) {
        graphics.fill(x, y, x + width, y + 1, argb(rgb, alpha))
    }

    /** Горизонтальний бар: доріжка й заповнення зліва направо. */
    fun bar(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        progress: Float,
        rgb: Int,
        alpha: Float,
    ) {
        graphics.fill(x, y, x + width, y + height, argb(TRACK, TRACK_ALPHA * alpha))
        val filled = (width * progress.coerceIn(0f, 1f)).toInt()
        if (filled > 0) graphics.fill(x, y, x + filled, y + height, argb(rgb, alpha))
    }

    /**
     * Шматок бару між двома частками — щоб позначити ділянку, а не рівень.
     *
     * Духовність малює ним ціну обраної здібності: видно наперед, що лишиться.
     */
    fun barSlice(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        from: Float,
        to: Float,
        rgb: Int,
        alpha: Float,
    ) {
        val left = x + (width * from.coerceIn(0f, 1f)).toInt()
        val right = x + (width * to.coerceIn(0f, 1f)).toInt()
        if (right <= left) return
        graphics.fill(left, y, right, y + height, argb(rgb, alpha))
    }

    /**
     * Перепочинок поверх іконки: тінь знизу вгору, що спадає в міру готовності.
     *
     * [remaining] — частка перепочинку, що ще лишилась: `1` — щойно почався, `0` — готова.
     */
    fun cooldownVeil(graphics: GuiGraphicsExtractor, x: Int, y: Int, size: Int, remaining: Float, alpha: Float) {
        val covered = (size * remaining.coerceIn(0f, 1f)).toInt()
        if (covered <= 0) return
        graphics.fill(x, y + size - covered, x + size, y + size, argb(PANEL, VEIL_ALPHA * alpha))
    }

    fun badgeWidth(font: Font, text: Component): Int = font.width(text) + BADGE_PADDING * 2

    /** Бейдж: підкладка й текст. Ціна, підпис клавіші слота, лічильник сторінок. */
    fun badge(
        graphics: GuiGraphicsExtractor,
        font: Font,
        text: Component,
        x: Int,
        y: Int,
        rgb: Int,
        alpha: Float,
    ) {
        graphics.fill(x, y, x + badgeWidth(font, text), y + BADGE_HEIGHT, argb(TRACK, TRACK_ALPHA * alpha))
        graphics.text(font, text, x + BADGE_PADDING, y + 2, argb(rgb, alpha))
    }
}
