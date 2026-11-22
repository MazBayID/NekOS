package com.nekos.android

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.draggableHandle
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.UUID

class LauncherReorderIntegrationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun isolatedContext(): Context {
        val namespace = "LauncherReorderIntegrationTest_${UUID.randomUUID()}"
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        return object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("$namespace-$name", mode)
        }
    }

    @Test
    fun favoriteGridReordersThroughRealDragAndPersistsAfterDrop() {
        val context = isolatedContext()
        val state = FavoriteReorderState(context)
        composeRule.setContent { FavoriteReorderHarness(state) }

        drag("favorite-A", "favorite-C")

        composeRule.runOnIdle {
            assertEquals(listOf("C", "B", "A", "D"), state.order.value)
            assertEquals(
                listOf("C", "B", "A", "D"),
                loadFavoritePackages(context).filterNotNull()
            )
        }
        composeRule.onNodeWithTag("favorite-order")
            .assertTextEquals("C,B,A,D")
    }

    @Test
    fun tagRailReordersThroughRealDragAndPersistsAfterDrop() {
        val context = isolatedContext()
        val state = TagReorderState(context)
        composeRule.setContent { TagReorderHarness(state) }

        drag("tag-a", "tag-b")

        composeRule.runOnIdle {
            assertEquals(listOf("b", "a", "c"), state.tags.value.map { it.id })
            assertEquals(listOf("b", "a", "c"), TagStore.load(context).map { it.id })
        }
        composeRule.onNodeWithTag("tag-order")
            .assertTextEquals("b,a,c")
    }

    @Test
    fun cancellingFavoriteGridDragRestoresOriginalOrderAndDoesNotPersistPartialMove() {
        val context = isolatedContext()
        val state = FavoriteReorderState(context)
        saveFavoritePackages(context, state.order.value)
        composeRule.setContent { FavoriteReorderHarness(state) }

        val source = composeRule.onNodeWithTag("favorite-A").fetchSemanticsNode()
        val target = composeRule.onNodeWithTag("favorite-C").fetchSemanticsNode()
        val targetInSource = target.boundsInRoot.center - source.boundsInRoot.topLeft

        composeRule.onNodeWithTag("favorite-A").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(targetInSource)
            advanceEventTime(100)
            cancel()
        }

        composeRule.runOnIdle {
            assertEquals(listOf("A", "B", "C", "D"), state.order.value)
            assertEquals(
                listOf("A", "B", "C", "D"),
                loadFavoritePackages(context).filterNotNull()
            )
        }
    }

    private fun drag(sourceTag: String, targetTag: String) {
        val source = composeRule.onNodeWithTag(sourceTag).fetchSemanticsNode()
        val target = composeRule.onNodeWithTag(targetTag).fetchSemanticsNode()
        val targetInSource = target.boundsInRoot.center - source.boundsInRoot.topLeft

        composeRule.onNodeWithTag(sourceTag).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(targetInSource)
            advanceEventTime(120)
            up()
        }
    }
}

private class FavoriteReorderState(val context: Context) {
    val order = mutableStateOf(listOf("A", "B", "C", "D"))
    var snapshot: List<String>? = null
}

private class TagReorderState(val context: Context) {
    val tags = mutableStateOf(
        listOf(
            NekTag(id = "a", name = "Alpha"),
            NekTag(id = "b", name = "Beta"),
            NekTag(id = "c", name = "Charlie")
        )
    )
    var snapshot: List<NekTag>? = null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteReorderHarness(state: FavoriteReorderState) {
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        val updated = state.order.value.toMutableList()
        val moved = updated[from.index]
        updated[from.index] = updated[to.index]
        updated[to.index] = moved
        state.order.value = updated
    }

    Column(Modifier.fillMaxSize()) {
        Text(state.order.value.joinToString(","), Modifier.testTag("favorite-order"))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            userScrollEnabled = false
        ) {
            gridItems(state.order.value, key = { it }) { item ->
                ReorderableItem(
                    state = reorderState,
                    key = item,
                    animateItemModifier = Modifier
                ) {
                    val handle = with(this) {
                        Modifier.draggableHandle(
                            dragGestureDetector = LauncherReorderGestureDetector(
                                onReorderStart = { state.snapshot = state.order.value.toList() },
                                onReorderEnd = { cancelled ->
                                    if (cancelled) {
                                        state.snapshot?.let { state.order.value = it }
                                    } else {
                                        saveFavoritePackages(state.context, state.order.value)
                                    }
                                    state.snapshot = null
                                }
                            )
                        )
                    }
                    Box(
                        Modifier.size(112.dp).then(handle).testTag("favorite-$item"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(item)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagReorderHarness(state: TagReorderState) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val sourceId = state.tags.value.getOrNull(from.index)?.id ?: return@rememberReorderableLazyListState
        state.tags.value = moveTagToIndex(state.tags.value, sourceId, to.index)
    }

    Column(Modifier.fillMaxSize()) {
        Text(state.tags.value.joinToString(",") { it.id }, Modifier.testTag("tag-order"))
        LazyColumn(
            state = listState,
            reverseLayout = true,
            userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
        ) {
            lazyItems(state.tags.value, key = { it.id }) { tag ->
                ReorderableItem(
                    state = reorderState,
                    key = tag.id,
                    animateItemModifier = Modifier
                ) {
                    val handle = with(this) {
                        Modifier.draggableHandle(
                            dragGestureDetector = LauncherReorderGestureDetector(
                                onReorderStart = { state.snapshot = state.tags.value.toList() },
                                onReorderEnd = { cancelled ->
                                    if (cancelled) {
                                        state.snapshot?.let { state.tags.value = it }
                                    } else {
                                        TagStore.save(state.context, state.tags.value)
                                    }
                                    state.snapshot = null
                                }
                            )
                        )
                    }
                    Box(
                        Modifier.width(240.dp).height(64.dp).then(handle).testTag("tag-${tag.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tag.name)
                    }
                }
            }
        }
    }
}
