package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.platform.NativeImage
import me.vangoo.mysteries.effect.EyeGlow
import me.vangoo.mysteries.modId
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.PlayerSkin

/**
 * The glowing eyes of a power ([EyeGlow]) laid over the eyes a skin actually has.
 *
 * A downloaded skin is a [DynamicTexture] that keeps its pixels in memory, so the client reads the face straight off it,
 * once per skin, and [EyeFinder] says where the eyes are. Each glow then gets a mask of its own for that skin: the eye's
 * innermost pixel in each row is the bright core, the rest its coloured rim, as in the stock textures the colours come
 * from. Where the skin's second layer is drawn over an eye (glasses, a mask), the same pixels are lit on that layer too,
 * and the eyes burn through the glasses instead of vanishing behind them.
 *
 * The stock textures stay for everything else: the built-in skins (all eighteen have Steve's eyes, `EyeFinderTest`), a
 * skin still downloading, and a face whose eyes cannot be told. A worn face (the Fool's Shapeshifting) is a skin of its
 * own, so its eyes are found on it.
 */
internal object SkinEyes {

    private sealed interface Read
    private data object Stock : Read
    private class Own(val types: Map<EyeGlow, RenderType>, val textures: List<Identifier>) : Read

    private val read = HashMap<Identifier, Read>()
    private var serial = 0

    /** The render type that lights [glow] in the eyes of [skin]. Called on the render thread. */
    fun renderType(skin: PlayerSkin, glow: EyeGlow): RenderType {
        val path = skin.body().texturePath()
        val known = read.getOrPut(path) { readSkin(path) }
        return (known as? Own)?.types?.get(glow) ?: STOCK.getValue(glow)
    }

    /** Steve's eyes in [glow]: the stock texture. */
    fun stock(glow: EyeGlow): RenderType = STOCK.getValue(glow)

    /**
     * Lets go of every mask: a world left. Leaving is told on the network thread, and a texture may only be freed on
     * the render thread, so the freeing is handed over to it.
     */
    fun clear() {
        val client = Minecraft.getInstance()
        client.execute {
            for (entry in read.values) if (entry is Own) entry.textures.forEach(client.textureManager::release)
            read.clear()
        }
    }

    private fun readSkin(path: Identifier): Read {
        val texture = Minecraft.getInstance().textureManager.getTexture(path) as? DynamicTexture ?: return Stock
        val pixels = texture.pixels ?: return Stock
        if (pixels.width < SKIN || pixels.height < SKIN) return Stock
        val found = EyeFinder.find { x, y -> pixels.getPixel(x, y) }
        val eyes = found ?: EyeFinder.STEVE
        val covered = eyes.filter { opaque(pixels.getPixel(OVER_X + it.c, FACE_Y + it.r)) }
        // Steve's eyes and nothing drawn over them: the stock textures say exactly that already.
        if (eyes == EyeFinder.STEVE && covered.isEmpty()) return Stock

        val textures = ArrayList<Identifier>()
        val types = EyeGlow.entries.associateWith { glow ->
            val (rim, core) = colours.getOrPut(glow) { stockColours(glow) }
            val mask = NativeImage(SKIN, SKIN, true)
            for (cell in eyes) {
                val colour = if (innermost(cell, eyes)) core else rim
                mask.setPixel(BASE_X + cell.c, FACE_Y + cell.r, colour)
                if (cell in covered) mask.setPixel(OVER_X + cell.c, FACE_Y + cell.r, colour)
            }
            val id = modId("dynamic/eyes_${serial++}_${glow.name.lowercase()}")
            Minecraft.getInstance().textureManager.register(id, DynamicTexture({ id.toString() }, mask))
            textures += id
            RenderTypes.eyes(id)
        }
        return Own(types, textures)
    }

    /** The cell nearest the middle of its eye in its row: the eye's bright core, as in the stock textures. */
    private fun innermost(cell: EyeFinder.Cell, eyes: Set<EyeFinder.Cell>): Boolean {
        val inward = if (cell.c <= 3) 1 else -1
        return EyeFinder.Cell(cell.c + inward, cell.r) !in eyes
    }

    private fun opaque(argb: Int): Boolean = argb ushr 24 != 0

    /** The rim and the core of [glow], as its stock texture paints Steve's left eye (x 9 rim, x 10 core, row 12). */
    private fun stockColours(glow: EyeGlow): Pair<Int, Int> {
        val manager = Minecraft.getInstance().resourceManager
        return manager.open(textureOf(glow)).use { stream ->
            NativeImage.read(stream).use { image -> image.getPixel(9, 12) to image.getPixel(10, 12) }
        }
    }

    private val colours = HashMap<EyeGlow, Pair<Int, Int>>()

    private fun textureOf(glow: EyeGlow): Identifier = modId("textures/entity/${glow.texture}.png")

    /** The stock render types: Steve's eyes. */
    private val STOCK: Map<EyeGlow, RenderType> = EyeGlow.entries.associateWith { RenderTypes.eyes(textureOf(it)) }

    private const val SKIN = 64
    private const val BASE_X = 8
    private const val OVER_X = 40
    private const val FACE_Y = 8
}
