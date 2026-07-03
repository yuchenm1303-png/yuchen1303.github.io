package com.yuchen.ailedger.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageAppCacheCleanupPolicyTest {
    @Test
    fun globalCacheCleanupUsesTrimCachesOnly() {
        val command = StorageAppCacheCleanupPolicy.command()
        assertTrue(StorageAppCacheCleanupPolicy.isCacheOnlyCommand(command))
        assertTrue(command.startsWith("pm trim-caches "))
        assertFalse(command.contains("pm clear"))
        assertFalse(command.contains("rm "))
    }

    @Test
    fun destructiveCommandsAreRejected() {
        assertFalse(StorageAppCacheCleanupPolicy.isCacheOnlyCommand("pm clear com.example.app"))
        assertFalse(StorageAppCacheCleanupPolicy.isCacheOnlyCommand("rm -rf /data/data/com.example.app/cache"))
    }
}
