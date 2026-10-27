package com.incognitoblocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure incognito detection logic.
 *
 * These tests run on the JVM and do not touch the Android framework. They verify text
 * normalization, explicit-indicator matching, and state-transition behavior.
 *
 * Note: Accessibility-tree traversal (AccessibilityNodeInfo) cannot be exercised here without
 * Robolectric/the Android SDK; it is covered by the manual verification checklist instead.
 */
class IncognitoDetectionLogicTest {

    @Test
    fun normalizeText_lowercasesTrimsAndCollapsesWhitespace() {
        assertEquals("you've gone incognito",
            IncognitoDetectionLogic.normalizeText("  You've   gone  incognito  "))
    }

    @Test
    fun normalizeText_straightensCurlyApostrophe() {
        assertEquals("you've gone incognito",
            IncognitoDetectionLogic.normalizeText("You\u2019ve gone incognito"))
    }

    @Test
    fun explicitIndicator_matchesYouveGoneIncognito() {
        assertTrue(IncognitoDetectionLogic.isExplicitIncognitoIndicator("You've gone incognito"))
    }

    @Test
    fun explicitIndicator_caseInsensitive() {
        assertTrue(IncognitoDetectionLogic.isExplicitIncognitoIndicator("YOU'VE GONE INCOGNITO"))
    }

    @Test
    fun explicitIndicator_ignoresCurlyApostropheVariant() {
        assertTrue(IncognitoDetectionLogic.isExplicitIncognitoIndicator("You\u2019ve gone incognito"))
    }

    @Test
    fun explicitIndicator_ignoresMenuItemMentions() {
        // A menu offering "New Incognito tab" must NOT be treated as proof of incognito browsing.
        assertFalse(IncognitoDetectionLogic.isExplicitIncognitoIndicator("New Incognito tab"))
        assertFalse(IncognitoDetectionLogic.isExplicitIncognitoIndicator("Close New Incognito tab tab"))
        assertFalse(IncognitoDetectionLogic.isExplicitIncognitoIndicator("Open a new Incognito tab"))
    }

    @Test
    fun explicitIndicator_ignoresGenericKeywords() {
        assertFalse(IncognitoDetectionLogic.isExplicitIncognitoIndicator("incognito"))
        assertFalse(IncognitoDetectionLogic.isExplicitIncognitoIndicator("private"))
        assertFalse(IncognitoDetectionLogic.isExplicitIncognitoIndicator("inprivate"))
        assertFalse(IncognitoDetectionLogic.isExplicitIncognitoIndicator("Close incognito tab"))
        assertFalse(IncognitoDetectionLogic.isExplicitIncognitoIndicator("Turn on incognito mode"))
    }

    @Test
    fun explicitIndicator_ignoresNormalWebsiteText() {
        assertFalse(IncognitoDetectionLogic.isExplicitIncognitoIndicator("example.com - Google Search"))
        assertFalse(IncognitoDetectionLogic.isExplicitIncognitoIndicator("Welcome to Example"))
        assertFalse(IncognitoDetectionLogic.isExplicitIncognitoIndicator(null))
        assertFalse(IncognitoDetectionLogic.isExplicitIncognitoIndicator(""))
        assertFalse(IncognitoDetectionLogic.isExplicitIncognitoIndicator("   "))
    }

    @Test
    fun stateTransition_actsOnlyOnNewIncognitoDetection() {
        // Not incognito -> incognito: should act.
        val t1 = IncognitoDetectionLogic.evaluateTransition(
            IncognitoDetectionLogic.DetectionState.NOT_INCOGNITO, true)
        assertEquals(IncognitoDetectionLogic.DetectionState.INCOGNITO, t1.newState)
        assertTrue(t1.shouldAct)

        // Inconclusive -> incognito: should act (first reliable evidence).
        val t2 = IncognitoDetectionLogic.evaluateTransition(
            IncognitoDetectionLogic.DetectionState.INCONCLUSIVE, true)
        assertEquals(IncognitoDetectionLogic.DetectionState.INCOGNITO, t2.newState)
        assertTrue(t2.shouldAct)

        // Incognito -> incognito: duplicate, must NOT act.
        val t3 = IncognitoDetectionLogic.evaluateTransition(
            IncognitoDetectionLogic.DetectionState.INCOGNITO, true)
        assertEquals(IncognitoDetectionLogic.DetectionState.INCOGNITO, t3.newState)
        assertFalse(t3.shouldAct)

        // Incognito -> not incognito: indicator disappeared, reset (not an act).
        val t4 = IncognitoDetectionLogic.evaluateTransition(
            IncognitoDetectionLogic.DetectionState.INCOGNITO, false)
        assertEquals(IncognitoDetectionLogic.DetectionState.NOT_INCOGNITO, t4.newState)
        assertFalse(t4.shouldAct)

        // Not incognito -> not incognito: no change, no act.
        val t5 = IncognitoDetectionLogic.evaluateTransition(
            IncognitoDetectionLogic.DetectionState.NOT_INCOGNITO, false)
        assertEquals(IncognitoDetectionLogic.DetectionState.NOT_INCOGNITO, t5.newState)
        assertFalse(t5.shouldAct)
    }
}
