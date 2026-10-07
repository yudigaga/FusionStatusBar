package com.xtjm.fusionstatusbar

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule

object MiuixTestActions {
    @JvmStatic fun await(rule: ComposeTestRule, condition: java.util.function.BooleanSupplier) {
        rule.waitUntil(10_000) { condition.asBoolean }
    }
    @JvmStatic fun rule(): ComposeTestRule = createEmptyComposeRule()
    @JvmStatic fun click(rule: ComposeTestRule, tag: String) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        rule.onNodeWithTag(tag).performClick()
    }
    @JvmStatic fun exists(rule: ComposeTestRule, text: String): Boolean {
        rule.waitForIdle()
        return rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }
    @JvmStatic fun choice(rule: ComposeTestRule, index: Int) {
        rule.waitForIdle()
        rule.onAllNodesWithTag("miuix_choice_list").onLast()
            .performScrollToNode(hasTestTag("miuix_choice_$index"))
        rule.onAllNodesWithTag("miuix_choice_$index").onLast().performClick()
    }
    @JvmStatic fun width(rule: ComposeTestRule, tag: String): Float {
        rule.waitForIdle()
        return rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.width
    }
}
