package io.github.xcore.plugin

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.plugin.AbstractMindustryPlugin
import com.xpdustry.distributor.api.translation.TranslationSource
import io.github.xcore.plugin.translation.XCoreTranslationSource
import java.net.URI

class XCorePlugin : AbstractMindustryPlugin() {

    override fun onInit() {
        Distributor.get().serviceManager.register(this, TranslationSource::class.java, XCoreTranslationSource())
    }

    companion object {
        val DISCORD_LINK = URI("https://discord.gg/RUMCCa9QAC")
    }
}
