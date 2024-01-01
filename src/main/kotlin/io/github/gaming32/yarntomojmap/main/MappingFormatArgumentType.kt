package io.github.gaming32.yarntomojmap.main

import net.fabricmc.mappingio.format.MappingFormat
import net.sourceforge.argparse4j.inf.Argument
import net.sourceforge.argparse4j.inf.ArgumentParser
import net.sourceforge.argparse4j.inf.ArgumentParserException
import net.sourceforge.argparse4j.inf.ArgumentType

object MappingFormatArgumentType : ArgumentType<MappingFormat> {
    private val nameMapping = buildMap<String, _> {
        for (format in MappingFormat.entries) {
            if (!format.hasSingleFile()) continue
            put(format.fileExt!!, format)
            put(format.name, format)
        }
    }

    override fun convert(parser: ArgumentParser, arg: Argument, value: String): MappingFormat =
        nameMapping[value.lowercase()] ?: throw ArgumentParserException("Unknown mappings format $value", parser, arg)
}
