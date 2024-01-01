package io.github.gaming32.yarntomojmap

import com.google.gson.JsonObject

@Suppress("NOTHING_TO_INLINE")
inline operator fun JsonObject?.get(key: String) = this?.get(key)
