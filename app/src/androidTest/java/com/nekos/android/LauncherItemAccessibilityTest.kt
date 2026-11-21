package com.nekos.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LauncherItemAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun launcherItemExposesButtonSemanticsAndActions() {
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

        val semantics = composeRule
            .onNodeWithContentDescription("Example app")
            .fetchSemanticsNode()
            .config

        assertEquals(Role.Button, semantics[SemanticsProperties.Role])
        val onClick = semantics[SemanticsActions.OnClick]
        val customActions = semantics[SemanticsActions.CustomActions]
        assertEquals("Open Example app", onClick.label)
        assertEquals(listOf("App actions"), customActions.map { it.label })

        composeRule.runOnIdle {
            assertTrue(onClick.action?.invoke() == true)
            assertEquals(1, activationCount.intValue)
            assertTrue(customActions.single().action())
            assertEquals(1, actionCount.intValue)
        }
    }
}
