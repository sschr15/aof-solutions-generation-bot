package sschr15.tools.aofsolutionsgenbot

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.boolean
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.hasPermission
import com.soywiz.korio.net.http.createHttpClient
import com.soywiz.korio.stream.openSync
import com.soywiz.korio.stream.readAll
import com.soywiz.korio.stream.toAsync
import com.soywiz.korio.stream.writeBytes
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import java.lang.reflect.Method
import java.net.URL
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.readLines

class AofGenerationExtension : Extension() {
    override val name = "AOF Solutions Generator Extension"

    private val classLoader = BotClassLoader(listOf(
        "java.lang.Object",
        "java.lang.String",
        "java.lang.Integer",
        "java.lang.Boolean",
        "java.lang.Byte",
        "java.lang.Short",
        "java.lang.Long",
        "java.lang.Float",
        "java.lang.Double",
        "java.lang.Character",
        "java.lang.Void",
        "java.lang.Math",
        "java.util.Random",
        "java.util.Arrays",
        "java.util.Collections",
        "java.util.List",
        "java.util.ArrayList",
        "java.util.Map",
        "java.util.HashMap",
        "java.util.Set",
        "java.util.HashSet",
        "java.util.stream.Collectors",
        "java.util.stream.Stream",
        "java.util.stream.StreamSupport",
        "java.util.stream.IntStream",
        "java.util.stream.LongStream",
        "java.util.stream.DoubleStream",
        "java.util.date.Date",
        "java.util.Calendar",
        "java.util.TimeZone",
        "java.util.Locale",
        "java.util.GregorianCalendar",
        "java.util.SimpleTimeZone",
        "java.util.Objects",
        "java.util.Optional",
        "java.util.OptionalInt",
        "java.util.OptionalLong",
        "java.util.OptionalDouble",
        "java.util.UUID",
        "java.util.NoSuchElementException",
        "java.lang.Exception",
        "java.lang.RuntimeException",
        "java.lang.IllegalArgumentException",
        "java.lang.IllegalStateException",
        "java.lang.NullPointerException",
        "java.lang.ClassCastException",
        "java.lang.ArrayIndexOutOfBoundsException",
        "java.lang.IndexOutOfBoundsException",
        "java.lang.NumberFormatException",
        "java.lang.StringIndexOutOfBoundsException",
        "java.lang.UnsupportedOperationException",
        "java.lang.NoSuchFieldException",
        "java.lang.NoSuchMethodException",
        "java.lang.ClassNotFoundException",
        "java.lang.InstantiationException",
        "java.lang.IllegalAccessException",
        "com.example.Utilities",
        "com.soywiz.krypto.encoding.Base64",
        "com.soywiz.krypto.encoding.ASCII",
        "com.soywiz.krypto.encoding.Hex",
        "com.soywiz.krypto.encoding.Base64Kt",
        "com.soywiz.krypto.encoding.ASCIIKt",
        "com.soywiz.krypto.encoding.HexKt",
        "com.soywiz.krypto.AES",
        "com.soywiz.krypto.SHA1",
        "com.soywiz.krypto.SHA256",
        "com.soywiz.krypto.Hash",
        "com.soywiz.krypto.Hasher",
        "com.soywiz.krypto.HasherFactory",
        "com.soywiz.krypto.HMAC",
        "com.soywiz.krypto.MD4",
        "com.soywiz.krypto.MD5",
        "com.soywiz.krypto.SecureRandom",
        "com.soywiz.krypto.HasherKt",
    ).map { it.replace('.', '/') })

    // MutableMap<Int?, String> to support `null` to be a library class
    private val savedSolutions by lazy {
        try {
            Path("solutionGenerators.txt").readLines()
                .map { it.split("=") }
                .associate { it[0].toIntOrNull() to Path(it[1]) }
        } catch (e: Exception) {
            mapOf()
        }.toMutableMap()
    }

    private val solutionExecutionMethods = mutableMapOf<Int, Method>()

    private fun getSolutionMethod(id: Int): Method = solutionExecutionMethods.getOrPut(id) {
        val path = savedSolutions[id] ?: error("Solution $id not found")
        val `class` = classLoader.addClass(path.toFile().openSync().readAll())

        `class`.getMethod("verify", String::class.java, java.lang.Long.TYPE)
    }

    private val httpClient = createHttpClient()

    override suspend fun setup() {
        for (file in Files.walk(Path("solutions"))) {
            if (file.toString().endsWith(".class")) {
                val day = if (file.fileName.toString() == "Utilities.class") null
                else file.fileName.toString().substringBeforeLast(".class").substring(8).toInt()
                savedSolutions[day] = file
            }
        }

        if (savedSolutions.containsKey(null)) {
            // add the utilities class if it exists
            classLoader.addClass(savedSolutions[null]!!.toFile().openSync().readAll())
        }

        ephemeralSlashCommand(::SubmitResultCommandArguments) {
            name = "submit"
            description = "Submit your AOF additional problem solutions to the bot"

            action {
                val solution = getSolutionMethod(arguments.day)
                val result = solution(null, arguments.result, user.id.value.toLong()) as Boolean

                respond {
                    content = "Your solution for day ${arguments.day} was ${if (result) "correct" else "incorrect"}"
                }

                if (result && arguments.announceIfCorrect) {
                    if (channel is GuildMessageChannelBehavior) {
                        channel.createMessage(":tada: ${user.mention} solved the extra problem for day ${arguments.day}!")
                    }
                }
            }
        }

        ephemeralSlashCommand(::AddChallengeVerifierArguments) {
            name = "create"
            description = "Create a new challenge verifier for a given day"

            action {
                suspend fun fail(message: String) {
                    this.respond {
                        content = message
                    }
                }
                val guildId = event.interaction.data.guildId.value ?: run {
                    fail("This command can only be used in a guild")
                    return@action
                }
                val user = event.interaction.user.asMemberOrNull(guildId) ?: run {
                    fail("You must be a member of this guild to use this command")
                    return@action
                }
                if (!user.hasPermission(Permission.Administrator)) {
                    fail("You must be an administrator to use this command")
                    return@action
                }

                val file = Path("solutions/Solution${arguments.day}.class")
                savedSolutions[arguments.day] = file
                if (arguments.solution != null) {
                    val node = ClassNode()
                    (AofGenerationExtension::class.java.getResourceAsStream("sschr15/tools/aofsolutionsgenbot/ExampleSolutionFile.class")
                        ?: Path("../build/classes/java/main/sschr15/tools/aofsolutionsgenbot/ExampleSolutionFile.class").inputStream())
                        .use { it.toAsync().readAll() }
                        .also { ClassReader(it).accept(node, 0) }

                    node.name = "TextSolution${arguments.day}"

                    val method = node.methods.first { it.name == "verify" }
                    val instructionToReplace = method.instructions.filterIsInstance<LdcInsnNode>().first()
                    instructionToReplace.cst = arguments.solution
                    file.toFile().openSync(mode = "rw").writeBytes(ClassWriter(0).run {
                        node.accept(this)
                        toByteArray()
                    })
                } else if (arguments.solutionClass != null) {
                    println("getting class from url")
//                    val bytes = httpClient.requestAsBytes(Http.Method.GET, arguments.solutionClass!!).content
                    val bytes = URL(arguments.solutionClass!!).openStream().use { it.toAsync().readAll() }
                    println("saving class to file")
                    file.toFile().openSync(mode = "rw").writeBytes(bytes)
                }

                try {
                    val `class` = classLoader.addClass(file.toFile().openSync().readAll())
                    val solution = `class`.getMethod("verify", String::class.java, java.lang.Long.TYPE)
                    solutionExecutionMethods[arguments.day] = solution

                    respond {
                        content = "Created solution for day ${arguments.day}"
                    }
                } catch (e: IllegalArgumentException) {
                    println(e.stackTraceToString())
                    respond {
                        content = "Failed to load solution for day ${arguments.day}: ${e.message}"
                    }
                }
            }
        }
    }
}

class SubmitResultCommandArguments : Arguments() {
    val result by string("solution", "Your solution to today's additional problem")
    val day by int("day", "Day of the challenge for this attempt")

    val announceIfCorrect by boolean("announceIfCorrect", "Whether to announce if you solved the challenge")
}

class AddChallengeVerifierArguments : Arguments() {
    val day by int("day", "Day of the challenge to verify")
    val solution by optionalString("solution", "Solution to the challenge, if it remains unchanged no matter who solves it")
    val solutionClass by optionalString("solutionClass", "URL pointing to the solution class, which will be loaded and verified")
}
