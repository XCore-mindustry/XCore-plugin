package io.github.xcore.plugin.translation

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.component.render.ComponentStringBuilder
import com.xpdustry.distributor.api.translation.*
import fluent.bundle.FluentBundle

class FluentTranslation(private val id: String, private val bundle: FluentBundle) : Translation {

    override fun format(parameters: TranslationArguments): String {
        val arguments = when (parameters) {
            is TranslationArguments.Named -> parameters.arguments
            is TranslationArguments.Array -> parameters.arguments.mapIndexed { index, value -> "arg$index" to value }.toMap()
            else -> emptyMap()
        }
        return bundle.format(id, arguments)
    }

    // Fluent is versatile enough to use formatting directly, so just decode and append
    override fun formatTo(parameters: TranslationArguments, builder: ComponentStringBuilder) {
        builder.append(Distributor.get().mindustryComponentDecoder.decode(format(parameters)))
    }
}
