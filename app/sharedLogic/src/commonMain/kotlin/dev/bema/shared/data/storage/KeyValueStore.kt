package dev.bema.shared.data.storage

interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

expect object PlatformKeyValueStore : KeyValueStore
