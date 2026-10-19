package me.vangoo.domain.hud

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BottomPanelTest {
    @Test fun lowValuesOverrideAllNumberModesAndChangesExpireAfterFiftyTicks() {
        val display = ResourceNumberDisplay()
        display.tick(20,20)
        assertFalse(display.visible(SpiritualityNumbers.ON_CHANGE,false))
        for (mode in SpiritualityNumbers.entries) assertTrue(display.visible(mode,true))
        display.tick(19,20)
        assertTrue(display.visible(SpiritualityNumbers.ON_CHANGE,false))
        repeat(49) { display.tick(19,20) }
        assertTrue(display.visible(SpiritualityNumbers.ON_CHANGE,false))
        display.tick(19,20)
        assertFalse(display.visible(SpiritualityNumbers.ON_CHANGE,false))
        assertFalse(display.visible(SpiritualityNumbers.NEVER,false))
        display.tick(19,40)
        assertTrue(display.visible(SpiritualityNumbers.ON_CHANGE,false))
        display.reset()
        assertFalse(display.visible(SpiritualityNumbers.ON_CHANGE,false))
    }

    @Test fun compactHealthIsOptInAndOnlyAboveForty() {
        val defaults = HudSettings()
        assertFalse(BottomPanel.healthBar(defaults,60f))
        val automatic = defaults.copy(compactHealthAbove40=true)
        assertFalse(BottomPanel.healthBar(automatic,40f))
        assertTrue(BottomPanel.healthBar(automatic,41f))
        assertTrue(BottomPanel.healthBar(defaults.copy(health=HealthStyle.BAR),20f))
        assertEquals(HungerStyle.ICONS,defaults.copy(health=HealthStyle.BAR).hunger)
    }

    @Test fun changingThePanelLayoutPreservesTheLegacyLayout() {
        val old = HudPosition(HorizontalAnchor.LEFT,VerticalAnchor.CENTER,6,4)
        val fresh = HudPosition(HorizontalAnchor.RIGHT,VerticalAnchor.BOTTOM,-30,-20)
        val settings = HudSettings(positions=mapOf(HudPart.ABILITY to old))
            .positioned(mapOf(HudPart.ABILITY to fresh))
        assertEquals(fresh,settings.activePositions[HudPart.ABILITY])
        assertEquals(old,settings.copy(interfaceStyle=InterfaceStyle.VANILLA).activePositions[HudPart.ABILITY])
    }

    @Test fun nativeSizeAttachmentsFitTheSmallestSupportedViewportForEitherHand() {
        for (width in listOf(320,427,480,640)) for (height in listOf(180,240,360)) {
            for (left in listOf(false,true)) {
                val ability=BottomPanel.ability(width,height,left)
                val orb=BottomPanel.orb(width,height,20,true)
                val auxiliary=BottomPanel.auxiliary(width,height)
                for (rect in listOf(ability,orb,auxiliary)) {
                    assertTrue(rect.x>=0 && rect.y>=0 && rect.right<=width && rect.bottom<=height)
                }
                assertFalse(ability.overlaps(orb))
                assertFalse(ability.overlaps(auxiliary))
            }
        }
    }

    @Test fun auxiliaryBoundaryClampsUntrustedSnapshotsWithoutInventingTheThirdValue() {
        assertEquals(AuxiliaryValues(0,10000,0),AuxiliaryValues(Int.MIN_VALUE,Int.MAX_VALUE,-1).clamped())
        assertEquals(0,PreviewAuxiliaryHudSource.snapshot().unassigned)
    }

    @Test fun chatMessagesClearThePanelOnlyWhereTheirHorizontalBoundsIntersect() {
        val orb=BottomPanel.orb(640,360,0,false)
        val rise=BottomPanel.chatRise(360,328,listOf(orb))
        assertTrue(360-40-rise <= orb.y-4)
        assertEquals(0,BottomPanel.chatRise(360,100,listOf(orb)))
        assertEquals(0,BottomPanel.chatRise(360,328,emptyList()))
    }

    @Test fun movingTheOrbToTheTopCannotPushChatOffscreen() {
        assertEquals(0,BottomPanel.chatRise(180,328,listOf(HudRect(4,4,48,44))))
        val tallPanel=HudRect(4,4,100,170)
        assertTrue(180-40-BottomPanel.chatRise(180,328,listOf(tallPanel))>=40)
    }
}
