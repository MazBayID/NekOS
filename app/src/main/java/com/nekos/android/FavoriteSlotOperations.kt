package com.nekos.android

sealed class FavoriteSlotOperation {
    data class Updated(val packages: List<String?>) : FavoriteSlotOperation()
    object Unchanged : FavoriteSlotOperation()
    object Full : FavoriteSlotOperation()
}

fun addFavoriteToSlots(
    current: List<String?>,
    packageName: String
): FavoriteSlotOperation {
    if (packageName in current) return FavoriteSlotOperation.Unchanged

    val updated = current.toMutableList()
    val emptySlot = updated.indexOfFirst { it == null }
    if (emptySlot < 0) return FavoriteSlotOperation.Full

    updated[emptySlot] = packageName
    return FavoriteSlotOperation.Updated(updated)
}

fun placeFavoriteFromDrawer(
    current: List<String?>,
    packageName: String,
    targetIndex: Int
): FavoriteSlotOperation {
    val updated = current.toMutableList()
    while (updated.size < 15) updated.add(null)
    require(targetIndex in 0 until 15) { "Favorite target must be between 0 and 14" }

    val sourceIndex = updated.indexOf(packageName)
    if (sourceIndex == targetIndex) return FavoriteSlotOperation.Unchanged

    val displaced = updated[targetIndex]
    if (sourceIndex >= 0) {
        updated[targetIndex] = packageName
        updated[sourceIndex] = displaced
    } else {
        if (displaced != null) {
            val emptyIndex = updated.indexOfFirst { it == null }
            if (emptyIndex < 0) return FavoriteSlotOperation.Full
            updated[emptyIndex] = displaced
        }
        updated[targetIndex] = packageName
    }

    return FavoriteSlotOperation.Updated(updated)
}
