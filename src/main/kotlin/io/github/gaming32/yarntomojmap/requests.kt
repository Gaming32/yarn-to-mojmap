package io.github.gaming32.yarntomojmap

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree
import java.io.InputStream
import java.util.jar.JarInputStream

private val logger = KotlinLogging.logger {}

fun createHttpClient() = HttpClient {
    install(UserAgent) {
        agent = "yarn-to-mojmap"
    }
    install(ContentNegotiation) {
        gson()
    }
}

suspend fun lookupMinecraftVersion(http: HttpClient, version: String?): Pair<String, Url> {
    logger.info { "Requesting Minecraft version ${version ?: "latest"}" }
    val json = http.get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
        .body<JsonObject>()
    val useVersion = version ?: json["latest"]
        .asJsonObject["release"]
        .asString ?: error("Missing $.latest.release in version_manifest_v2.json")
    val versionData = json["versions"]
        .asJsonArray
        .asSequence()
        .map { it.asJsonObject }
        .firstOrNull { it["id"].asString == useVersion }
        ?: error("No Minecraft version with id '$useVersion'")
    return versionData["id"].asString to Url(versionData["url"].asString)
}

suspend fun lookupLatestYarn(http: HttpClient, minecraftVersion: String): Int? {
    logger.info { "Requesting latest Yarn version for Minecraft $minecraftVersion" }
    return http.get("https://meta.fabricmc.net/v2/versions/yarn/$minecraftVersion")
        .body<JsonArray>()
        .maxOfOrNull { it.asJsonObject["build"].asInt }
}

suspend fun lookupMinecraftFileDownloads(http: HttpClient, url: Url): Map<String, Url> {
    logger.info { "Requesting client json from $url" }
    return http.get(url)
        .body<JsonObject>()["downloads"]
        .asJsonObject
        .asMap()
        .mapValues { Url(it.value.asJsonObject["url"].asString) }
}

suspend fun downloadMojmap(http: HttpClient, downloads: Map<String, Url>): MappingTree {
    val url = downloads.getValue("client_mappings")
    logger.info { "Downloading Mojmap from $url" }
    return http.get(url)
        .body<InputStream>()
        .bufferedReader()
        .use { reader ->
            val result = MemoryMappingTree()
//            result.setIndexByDstNames(true)
            withContext(Dispatchers.IO) {
                MappingReader.read(reader, result)
            }
            result
        }
}

suspend fun downloadIntermediaryMappings(http: HttpClient, mcVersion: String): MappingTree {
    logger.info { "Downloading Intermediary $mcVersion" }
    return downloadFabricMappings(http, "https://maven.fabricmc.net/net/fabricmc/intermediary/$mcVersion/intermediary-$mcVersion-v2.jar")
}

suspend fun downloadYarnMappings(http: HttpClient, yarnVersion: String): MappingTree {
    logger.info { "Downloading Yarn $yarnVersion" }
    return downloadFabricMappings(http, "https://maven.fabricmc.net/net/fabricmc/yarn/$yarnVersion/yarn-$yarnVersion-v2.jar")
}

private suspend fun downloadFabricMappings(http: HttpClient, url: String) =
    http.get(url)
        .body<InputStream>()
        .let(::JarInputStream)
        .use { jis ->
            val result = MemoryMappingTree()
            withContext(Dispatchers.IO) {
                while (true) {
                    val entry = jis.nextJarEntry ?: error("Missing mappings.tiny")
                    if (entry.realName != "mappings/mappings.tiny") {
                        jis.closeEntry()
                        continue
                    }
                    MappingReader.read(jis.reader(), result)
                    jis.closeEntry()
                    break
                }
            }
            result
        }
