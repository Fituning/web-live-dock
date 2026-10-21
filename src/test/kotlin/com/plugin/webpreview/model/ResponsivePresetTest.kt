package com.plugin.webpreview.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsivePresetTest {

    @Test
    fun testTailwindBreakpoints() {
        assertEquals(0, ResponsivePreset.AUTO.targetWidth)
        assertFalse(ResponsivePreset.AUTO.isFixed)

        assertEquals(390, ResponsivePreset.MOBILE.targetWidth)
        assertEquals(844, ResponsivePreset.MOBILE.targetHeight)
        assertTrue(ResponsivePreset.MOBILE.isFixed)

        assertEquals(640, ResponsivePreset.SM.targetWidth)
        assertEquals(768, ResponsivePreset.MD.targetWidth)
        assertEquals(1024, ResponsivePreset.LG.targetWidth)
        assertEquals(1280, ResponsivePreset.XL.targetWidth)
        assertEquals(1536, ResponsivePreset.TW_2XL.targetWidth)
    }

    @Test
    fun testFromId() {
        assertEquals(ResponsivePreset.SM, ResponsivePreset.fromId("sm"))
        assertEquals(ResponsivePreset.MD, ResponsivePreset.fromId("md"))
        assertEquals(ResponsivePreset.LG, ResponsivePreset.fromId("lg"))
        assertEquals(ResponsivePreset.XL, ResponsivePreset.fromId("xl"))
        assertEquals(ResponsivePreset.TW_2XL, ResponsivePreset.fromId("2xl"))
        assertEquals(ResponsivePreset.MOBILE, ResponsivePreset.fromId("mobile"))
        assertEquals(ResponsivePreset.AUTO, ResponsivePreset.fromId("unknown"))
    }
}
