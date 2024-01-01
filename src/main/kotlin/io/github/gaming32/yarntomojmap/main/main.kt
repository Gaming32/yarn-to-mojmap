package io.github.gaming32.yarntomojmap.main

import io.github.gaming32.yarntomojmap.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import net.fabricmc.mappingio.MappingWriter
import net.fabricmc.mappingio.format.MappingFormat
import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.annotation.Arg
import net.sourceforge.argparse4j.inf.ArgumentParserException
import kotlin.system.exitProcess
import kotlin.time.measureTime

private val logger = KotlinLogging.logger {}

fun main(vararg args: String): Unit = runBlocking {
    val parser = ArgumentParsers.newFor("yarn-to-mojmap")
        .fromFilePrefix("@")
        .build()
        .description("Generates Yarn to Mojmap mappings files. Great for using in source-remap.")
    parser.addArgument("-m", "--minecraft")
        .help("The target Minecraft version. Defaults to latest.")
    parser.addArgument("-y", "--yarn")
        .help("The source Yarn build. Defaults to latest.")
        .type(Int::class.java)
    parser.addArgument("-f", "--format")
        .help("The format to export mappings with. Specify either an extension or mapping-io ID. Defaults to Tiny v2.")
        .type(MappingFormatArgumentType)
        .setDefault(MappingFormat.TINY_2_FILE)

    val parsedArgs = object {
        @set:Arg(dest = "minecraft")
        var minecraft: String? = null

        @set:Arg(dest = "yarn")
        var yarn: Int? = null

        @set:Arg(dest = "format")
        lateinit var format: MappingFormat
    }

    try {
        parser.parseArgs(args, parsedArgs)
    } catch (e: ArgumentParserException) {
        parser.handleError(e)
        exitProcess(1)
    }

    try {
        val mappings = createHttpClient().use { http ->
            val (minecraftVersion, clientJsonUrl) = lookupMinecraftVersion(http, parsedArgs.minecraft)
            logger.info { "Using Minecraft version $minecraftVersion" }

            val mojmap = async {
                val minecraftDownloads = lookupMinecraftFileDownloads(http, clientJsonUrl)
                logger.info { "Loaded ${minecraftDownloads.size} download URLs" }

                val mojmap = downloadMojmap(http, minecraftDownloads)
                logger.info { "Loaded ${mojmap.classes.size} classes from Mojmap" }
                mojmap
            }

            val yarnBuild = parsedArgs.yarn ?: lookupLatestYarn(http, minecraftVersion)
            if (yarnBuild == null) {
                logger.error { "No Yarn version found for Minecraft $minecraftVersion" }
                exitProcess(1)
            }
            val yarnVersion = "$minecraftVersion+build.$yarnBuild"
            logger.info { "Using Yarn version $yarnVersion" }

            val intermediaryMappings = async {
                val result = downloadIntermediaryMappings(http, minecraftVersion)
                logger.info { "Loaded ${result.classes.size} classes from Intermediary" }
                result
            }

            val yarnMappings = async {
                val result = downloadYarnMappings(http, yarnVersion)
                logger.info { "Loaded ${result.classes.size} classes from Yarn" }
                result
            }

            MappingsTriple(mojmap.await(), intermediaryMappings.await(), yarnMappings.await())
        }

        logger.info {
            "Loaded ${mappings.mojmap.classes.size + mappings.intermediary.classes.size + mappings.yarn.classes.size}" +
                " total class mappings"
        }
        logger.info { "Starting mapping building process" }

        val timing = measureTime {
            val streamWriter = System.out.bufferedWriter()
            val mappingWriter = MappingWriter.create(streamWriter, parsedArgs.format)
                ?: error("Unsupported output format ${parsedArgs.format}")
            buildMappings(mappings, mappingWriter)
            streamWriter.newLine()
            streamWriter.flush()
        }

        logger.info { "Finished building mappings in $timing" }
    } catch (e: IllegalStateException) {
        // Thrown by kotlin.error()
        logger.error { e.message }
        exitProcess(1)
    } catch (e: Exception) {
        logger.error(e) { "An unexpected error occurred!" }
        exitProcess(1)
    }
}
