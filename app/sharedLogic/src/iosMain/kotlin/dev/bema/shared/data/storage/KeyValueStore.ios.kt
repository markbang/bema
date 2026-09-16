package dev.bema.shared.data.storage

import platform.Foundation.NSUserDefaults

actual object PlatformKeyValueStore : KeyValueStore {
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
}
