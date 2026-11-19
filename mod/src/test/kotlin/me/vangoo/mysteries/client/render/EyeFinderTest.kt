package me.vangoo.mysteries.client.render

import me.vangoo.mysteries.client.render.EyeFinder.Cell
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import javax.imageio.ImageIO

class EyeFinderTest {

    /** Every default skin of the game, wide and slim, has its eyes where Steve's are — and is found there. */
    @ParameterizedTest
    @MethodSource("defaults")
    fun `default skins are found where steve's eyes are`(path: String) {
        val stream = javaClass.getResourceAsStream(path)
        assertNotNull(stream, "немає $path на класпасі")
        val image = ImageIO.read(stream)
        assertEquals(EyeFinder.STEVE, EyeFinder.find { x, y -> image.getRGB(x, y) }, path)
    }

    @Test
    fun `tall narrow eyes come out tall and narrow`() = assertEquals(
        cells(2 to 2, 2 to 3, 2 to 4, 5 to 2, 5 to 3, 5 to 4),
        find("hhhhhhhh hsssssss sswsswss sswsswss ssbssbss ssssssss sssmmsss ssssssss"),
    )

    @Test
    fun `big eyes of two colours are found whole`() = assertEquals(
        cells(1 to 2, 2 to 2, 1 to 3, 2 to 3, 1 to 4, 2 to 4, 5 to 2, 6 to 2, 5 to 3, 6 to 3, 5 to 4, 6 to 4),
        find("hhhhhhhh hhhhhhhh swbssbws swbssbws swwsswws ssssssss sssmmsss ssssssss"),
    )

    @Test
    fun `eyes high or low on the face are found there`() {
        assertEquals(cells(1 to 2, 6 to 2), find("hhhhhhhh hsssssss sbssssbs ssssssss ssssssss ssssssss sssmmsss ssssssss"))
        assertEquals(cells(1 to 5, 6 to 5), find("hhhhhhhh hsssssss ssssssss ssssssss ssssssss sbssssbs ssssssss ssssssss"))
    }

    @Test
    fun `brows above the eyes are left out`() = assertEquals(
        cells(1 to 4, 2 to 4, 5 to 4, 6 to 4),
        find("hhhhhhhh ssssssss shhsshhs ssssssss swbssbws ssssssss sssmmsss ssssssss"),
    )

    @Test
    fun `what cannot be told falls back to steve`() {
        assertNull(find("kkkkkkkk kkkkkkkk kkkkkkkk kggggggk kkkkkkkk kkkkkkkk kkkkkkkk kkkkkkkk"), "візор")
        assertNull(find("hhhhhhhh hsssssss ssssssss ssbsssss ssssssss ssssssss sssmmsss ssssssss"), "одне око")
        assertNull(find("hhhhhhhh ssssssss ssssssss ssssssss sssbbsss ssssssss sssmmsss ssssssss"), "очі злиплись посередині")
    }

    private fun find(art: String): Set<Cell>? =
        EyeFinder.find(Array(8) { r -> IntArray(8) { c -> PALETTE.getValue(art.split(' ')[r][c]) } })

    private fun cells(vararg at: Pair<Int, Int>): Set<Cell> = at.mapTo(HashSet()) { (c, r) -> Cell(c, r) }

    companion object {
        private val PALETTE = mapOf(
            's' to 0xE6B496, 'h' to 0x3C2814, 'w' to 0xFAFAFA, 'b' to 0x285AC8, 'k' to 0x0A0A0A, 'm' to 0x963C3C, 'g' to 0x141414,
        )

        @JvmStatic
        fun defaults(): List<String> = listOf("wide", "slim").flatMap { model ->
            listOf("alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri")
                .map { "/assets/minecraft/textures/entity/player/$model/$it.png" }
        }
    }
}
