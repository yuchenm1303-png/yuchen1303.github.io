package com.yuchen.ailedger.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val CAPACITY_PREFS = "storage_productization"
private const val CAPACITY_KEY = "capacity_snapshots"

internal class StorageCapacitySnapshotStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(CAPACITY_PREFS, Context.MODE_PRIVATE)

    fun load(): List<StorageCapacitySnapshot> {
        val raw = prefs.getString(CAPACITY_KEY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        StorageCapacitySnapshot(
                            createdAt = item.optLong("createdAt"),
                            totalBytes = item.optLong("totalBytes"),
                            freeBytes = item.optLong("freeBytes"),
                        ),
                    )
                }
            }.filter { it.totalBytes > 0L }.sortedBy(StorageCapacitySnapshot::createdAt)
        }.getOrDefault(emptyList())
    }

    fun append(overview: DeviceStorageOverview) {
        val items = load().toMutableList()
        items += StorageCapacitySnapshot(
            createdAt = System.currentTimeMillis(),
            totalBytes = overview.totalBytes,
            freeBytes = overview.freeBytes,
        )
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("createdAt", item.createdAt)
                    .put("totalBytes", item.totalBytes)
                    .put("freeBytes", item.freeBytes),
            )
        }
        prefs.edit().putString(CAPACITY_KEY, array.toString()).apply()
    }
}
