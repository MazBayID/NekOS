package com.nekos.android

private const val DOCK_SLOT_COUNT = 5

sealed class DockSlotOperation {
    data class Updated(val packages: List<String?>) : DockSlotOperation()
    object Unchanged : DockSlotOperation()
    object Full : DockSlotOperation()
}

fun addDockPackage(current: List<String?>, packageName: String): DockSlotOperation {
    if (packageName.isBlank() || packageName in current) return DockSlotOperation.Unchanged
    val updated = normalizeDockPackages(current).toMutableList()
    val emptySlot = updated.indexOfFirst { it == null }
    if (emptySlot < 0) return DockSlotOperation.Full
    updated[emptySlot] = packageName
    return DockSlotOperation.Updated(updated)
}

fun removeDockPackage(current: List<String?>, packageName: String): List<String?> {
    val normalized = normalizeDockPackages(current)
    if (packageName !in normalized) return normalized
    return normalizeDockPackages(normalized.map { if (it == packageName) null else it })
}

fun normalizeDockPackages(packages: List<String?>): List<String?> {
    val seen = mutableSetOf<String>()
    val normalized = packages.take(DOCK_SLOT_COUNT).map { packageName ->
        packageName?.takeIf { it.isNotBlank() && seen.add(it) }
    }
    return normalized + List(DOCK_SLOT_COUNT - normalized.size) { null }
}
