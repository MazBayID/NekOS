package com.nekos.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsButton
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LauncherItemAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun launcherItemExposesButtonSemanticsAndActivation() {
        val activationCount = mutableIntStateOf(0)
        val actionCount = mutableIntStateOf(0)

        composeRule.setContent {
            Box(
                Modifier
                    .size(100.dp)
                    .launcherItemSemantics(
                        appLabel = "Example app",
                        onActivate = { activationCount.intValue++ },
                        onShowActions = { actionCount.intValue++ }
                    )
            )
        }

        val appNode = composeRule.onNodeWithContentDescription("Example app")
        appNode.assertIsButton()
            .assertHasClickAction()
            .performClick()

        val customActions = appNode.fetchSemanticsNode().config[SemanticsProperties.CustomActions]
        assertEquals(listOf("App actions"), customActions.map { it.label })
        assertTrue(customActions.single().action())

        composeRule.runOnIdle {
            assertEquals(1, activationCount.intValue)
            assertEquals(1, actionCount.intValue)
        }
    }
}
