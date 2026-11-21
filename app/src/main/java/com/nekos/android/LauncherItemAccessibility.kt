package com.nekos.android

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * Exposes launcher app gestures to accessibility services without changing
 * the existing touch gesture detector.
 */
internal fun Modifier.launcherItemSemantics(
    appLabel: String,
    onActivate: () -> Unit,
    onShowActions: () -> Unit
): Modifier = semantics(mergeDescendants = true) {
    role = Role.Button
    contentDescription = appLabel
    onClick(label = "Open $appLabel") {
        onActivate()
        true
    }
    customActions = listOf(
        CustomAccessibilityAction(label = "App actions") {
            onShowActions()
            true
        }
    )
}
