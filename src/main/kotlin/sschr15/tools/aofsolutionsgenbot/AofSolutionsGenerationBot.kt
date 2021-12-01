package sschr15.tools.aofsolutionsgenbot

import com.kotlindiscord.kord.extensions.ExtensibleBot

suspend fun main(args: Array<String>) {
    val bot = ExtensibleBot(args[0]) {
        extensions {
            add {
                AofGenerationExtension()
            }
        }
    }
    bot.start()
}