package sschr15.tools.aofsolutionsgenbot

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode

class BotClassLoader(acceptedClasses: List<String>) : ClassLoader() {
    private val acceptedClasses = acceptedClasses.map { it.replace('/', '.') }.toMutableList()
        .also {
            // add primitive types
            it.addAll(listOf("int", "long", "float", "double", "boolean", "char", "byte", "short", "void"))
        }
    fun addClass(bytes: ByteArray): Class<*> {
        val node = ClassNode()
        ClassReader(bytes).accept(node, 0)
        val allowed = node.methods.all { method ->
            method.instructions.all {
                when (it) {
                    is MethodInsnNode -> (it.owner.replace('/', '.') in acceptedClasses && Type.getArgumentTypes(it.desc).all { type -> type.className in acceptedClasses })
                            || it.owner == node.name
                    is FieldInsnNode -> (it.owner.replace('/', '.') in acceptedClasses && Type.getType(it.desc).className in acceptedClasses) || it.owner == node.name
                    else -> true
                }
            }
                    && Type.getArgumentTypes(method.desc).all { it.className in acceptedClasses }
                    && Type.getReturnType(method.desc).className in acceptedClasses
                    && (method.tryCatchBlocks?.all { it.type == null || it.type.replace('/', '.') in acceptedClasses } ?: true)
                    && (method.exceptions.isNullOrEmpty() || method.name != "verify") // if it's `verify`, no exceptions are allowed to be uncaught
        }
                && node.fields.all { field ->
                    field.desc in acceptedClasses
                }
        if (!allowed) {
            throw IllegalArgumentException("Class ${node.name} is not allowed")
        }

        return defineClass(node.name.replace('/', '.'), bytes, 0, bytes.size)
    }
}
