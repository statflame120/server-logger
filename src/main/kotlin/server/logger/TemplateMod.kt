package server.logger

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object TemplateMod : ModInitializer {
    private val logger = LoggerFactory.getLogger("template-mod")

	override fun onInitialize() {
		logger.info("hi")
	}
}
