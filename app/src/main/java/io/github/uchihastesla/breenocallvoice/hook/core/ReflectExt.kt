package io.github.uchihastesla.breenocallvoice.hook.core

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Clean reflection helpers for resolving target classes, methods, and fields.
 */
object ReflectExt {

    fun findClass(classLoader: ClassLoader, name: String): Class<*> =
        Class.forName(name, false, classLoader)

    fun findMethod(cls: Class<*>, name: String, vararg paramTypes: Class<*>): Method {
        var cur: Class<*>? = cls
        while (cur != null) {
            try {
                return cur.getDeclaredMethod(name, *paramTypes).apply {
                    isAccessible = true
                }
            } catch (_: NoSuchMethodException) {
                cur = cur.superclass
            }
        }
        throw NoSuchMethodException("${cls.name}#$name")
    }

    fun findField(cls: Class<*>, name: String): Field {
        var cur: Class<*>? = cls
        while (cur != null) {
            try {
                return cur.getDeclaredField(name).apply {
                    isAccessible = true
                }
            } catch (_: NoSuchFieldException) {
                cur = cur.superclass
            }
        }
        throw NoSuchFieldException("${cls.name}#$name")
    }

    fun getStaticField(cls: Class<*>, name: String): Any? =
        findField(cls, name).get(null)
}
