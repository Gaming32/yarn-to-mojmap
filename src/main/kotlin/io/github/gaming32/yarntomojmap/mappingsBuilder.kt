package io.github.gaming32.yarntomojmap

import io.github.oshai.kotlinlogging.KotlinLogging
import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MappingTree.MemberMapping

private val logger = KotlinLogging.logger {}

fun buildMappings(mappings: MappingsTriple, visitor: MappingVisitor) {
    if (visitor.visitHeader()) {
        visitor.visitNamespaces("yarn", listOf("mojang"))
    }
    if (!visitor.visitContent()) return
    val (mojmap, intermediary, yarn) = mappings
    val yarnDescMapper: DescMapper = yarn::mapDesc
    for (mojmapClass in mojmap.classes) {
        val named = mojmapClass.srcName
        val obf = mojmapClass.getDstName(0)!!

        val intermediaryClass = intermediary.getClass(obf)
        if (intermediaryClass == null) {
            if (named != obf) { // Intermediary doesn't contain fully unobfuscated entries
                logger.warn { "Missing intermediary name for $obf ($named)" }
            }
            continue
        }
        val intermediaryName = intermediaryClass.getDstName(0)!!

        val yarnName = yarn.fixInnerName(intermediaryName)
        if (!visitor.visitClass(yarnName)) continue
        visitor.visitDstName(MappedElementKind.CLASS, 0, named)
        if (!visitor.visitElementContent(MappedElementKind.CLASS)) continue

        val yarnClass = yarn.getClass(intermediaryName) ?: continue
        fixMembers(
            mojmapClass.fields, intermediaryClass::getField,
            yarnClass::getField, yarnDescMapper, visitor,
            MappedElementKind.FIELD, MappingVisitor::visitField
        )
        fixMembers(
            mojmapClass.methods, intermediaryClass::getMethod,
            yarnClass::getMethod, yarnDescMapper, visitor,
            MappedElementKind.METHOD, MappingVisitor::visitMethod
        )
    }
}

private typealias MemberMappings = Collection<MemberMapping>
private typealias MemberGetter = (srcName: String, srcDest: String?) -> MemberMapping?
private typealias DescMapper = (desc: CharSequence, namespace: Int) -> String
private typealias InitialVisitor = MappingVisitor.(srcName: String, srcDesc: String?) -> Boolean

private fun fixMembers(
    mojmap: MemberMappings, intermediary: MemberGetter, yarn: MemberGetter,
    yarnDescMapper: DescMapper, visitor: MappingVisitor,
    kind: MappedElementKind, initialVisitor: InitialVisitor
) {
    for (mojmapMember in mojmap) {
        val named = mojmapMember.srcName
        val namedDesc = mojmapMember.srcDesc
        val obf = mojmapMember.getDstName(0)!!
        val obfDesc = mojmapMember.getDstDesc(0)

        val intermediaryMember = intermediary(obf, obfDesc) ?: continue // Not propagated
        val intermediaryName = intermediaryMember.getDstName(0)!!
        val intermediaryDesc = intermediaryMember.getDstDesc(0)

        val yarnMember = yarn(intermediaryName, intermediaryDesc)
        val srcName = yarnMember?.getDstName(0) ?: intermediaryName
        val srcDesc = yarnMember?.getDstDesc(0) ?: intermediaryDesc?.let { yarnDescMapper(it, 0) }

        if (!visitor.initialVisitor(srcName, srcDesc)) continue
        visitor.visitDstName(kind, 0, named)
        visitor.visitDstDesc(kind, 0, namedDesc)
        visitor.visitElementContent(kind)
    }
}

private fun MappingTree.fixInnerName(src: String): String {
    var useSrc = src
    var suffix = ""
    while (true) {
        val dst = mapClassName(useSrc, 0)
        if (dst != useSrc) {
            return dst + suffix
        }
        val mid = useSrc.lastIndexOf('$')
        if (mid == -1) break
        suffix = useSrc.substring(mid) + suffix
        useSrc = useSrc.substring(0, mid)
    }
    return src
}
