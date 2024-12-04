package io.github.xcore.plugin.translation

import com.xpdustry.distributor.api.translation.BundleTranslationSource
import com.xpdustry.distributor.api.translation.TranslationSource
import fluent.bundle.FluentBundle
import fluent.functions.cldr.CLDRFunctionFactory
import fluent.syntax.AST.Message
import fluent.syntax.parser.FTLParser
import fluent.syntax.parser.FTLStream
import io.github.xcore.plugin.XCorePlugin
import java.nio.file.FileSystems
import java.util.*
import kotlin.io.path.*

@Suppress("FunctionName")
fun XCoreTranslationSource(): TranslationSource {
    val registry = BundleTranslationSource.create(Locale.ENGLISH)
    val location = XCorePlugin::class.java.protectionDomain.codeSource.location.toURI().toPath()
    // Create a simple zip file system to read the FTL files
    FileSystems.newFileSystem(location, null as ClassLoader?).use { fs ->
        fs.getPath("bundles").listDirectoryEntries().forEach { entry ->
            if (entry.extension != "ftl") return@forEach
            val locale = entry.nameWithoutExtension
                .split('_', limit = 2)
                .getOrNull(1)
                ?.let(Locale::forLanguageTag)
                ?: Locale.ROOT
            val resource = FTLParser.parse(FTLStream.of(entry.readText()))
            if (resource.hasErrors()) {
                error("Failed to parse FTL file: $entry")
            }
            val bundle = FluentBundle.builder(locale, CLDRFunctionFactory.INSTANCE)
                .addResource(resource)
                .withFunctionFactory(CLDRFunctionFactory.INSTANCE)
                .build()
            // That lib is so shit, why can't we list entries with the bundle directly
            val keys = resource.entries.asSequence()
                .filterIsInstance<Message>()
                .mapTo(mutableSetOf()) { it.identifier().name }
            registry.registerAll(locale, keys) { id -> FluentTranslation(id, bundle) }
        }
    }
    return registry
}