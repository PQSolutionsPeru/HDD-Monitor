package com.pqsolutions.hdd_monitor.data.util

import androidx.collection.LruCache
import java.util.concurrent.TimeUnit

class Cache<K : Any, V : Any>(
    private val maxSize: Int,
    private val expirationTimeMillis: Long
) {
    private val cache = LruCache<K, CacheEntry<V>>(maxSize)
    private val lock = Any()

    data class CacheEntry<V>(
        val value: V,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun put(key: K, value: V) {
        synchronized(lock) {
            cache.put(key, CacheEntry(value))
        }
    }

    fun get(key: K): V? {
        synchronized(lock) {
            val entry = cache.get(key) ?: return null

            if (isExpired(entry)) {
                cache.remove(key)
                return null
            }

            return entry.value
        }
    }

    fun remove(key: K) {
        synchronized(lock) {
            cache.remove(key)
        }
    }

    fun clear() {
        synchronized(lock) {
            cache.evictAll()
        }
    }

    fun getAllValues(): List<V> {
        synchronized(lock) {
            return cache.snapshot()
                .filterValues { !isExpired(it) }
                .map { it.value.value }
        }
    }

    fun getSize(): Int {
        synchronized(lock) {
            return cache.size()
        }
    }

    private fun isExpired(entry: CacheEntry<*>): Boolean {
        return System.currentTimeMillis() - entry.timestamp > expirationTimeMillis
    }

    companion object {
        fun getMillis(amount: Long, unit: TimeUnit): Long {
            return unit.toMillis(amount)
        }
    }
}