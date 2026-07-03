package com.yuchen.ailedger.service

data class StorageDeviceGuard(
    val batteryPercent: Int?,
    val charging: Boolean,
    val thermalStatus: Int?,
    val thermalLabel: String,
    val heavyWorkAllowed: Boolean,
    val reason: String,
)

data class StoragePermissionHealth(
    val usageAccessGranted: Boolean,
    val imageAccessGranted: Boolean,
    val videoAccessGranted: Boolean,
    val audioAccessGranted: Boolean,
    val selectedPhotoAccessOnly: Boolean,
    val authorizedFolderPresent: Boolean,
    val authorizedFolderPermissionValid: Boolean,
) {
    val mediaAccessGranted: Boolean
        get() = imageAccessGranted || videoAccessGranted || audioAccessGranted || selectedPhotoAccessOnly

    val healthy: Boolean
        get() = usageAccessGranted && mediaAccessGranted &&
            (!authorizedFolderPresent || authorizedFolderPermissionValid)
}

data class StorageCompatibilityReport(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val sdk: Int,
    val title: String,
    val guidance: List<String>,
)
