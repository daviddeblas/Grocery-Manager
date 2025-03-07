package com.example.frontend.data.model

enum class SyncStatus {
    LOCAL_ONLY,      // Only exists locally, must be synchronized
    SYNCED,          // Synchronized with the server
    MODIFIED_LOCALLY // Modified locally, must be synced


}