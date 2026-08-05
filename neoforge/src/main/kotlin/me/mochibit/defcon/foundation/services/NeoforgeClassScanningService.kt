package me.mochibit.defcon.foundation.services

import me.mochibit.defcon.DefconMod.MOD_ID
import net.neoforged.fml.ModList
import net.neoforged.neoforgespi.language.ModFileScanData
import org.objectweb.asm.Type
import java.lang.annotation.ElementType

class NeoforgeClassScanningService : ClassScanningService {

    private val scanData: ModFileScanData?
        get() = ModList.get().getModFileById(MOD_ID)?.file?.scanResult


    private val classesByType: Map<Type, ModFileScanData.ClassData> by lazy {
        scanData?.classes?.associateBy { it.clazz } ?: emptyMap()
    }

    override fun getClassesAnnotatedWith(annotation: Class<out Annotation>): List<Class<*>> {
        val data = scanData ?: return emptyList()
        val annotationType = Type.getType(annotation)

        return data.annotations
            .filter { it.targetType == ElementType.TYPE && it.annotationType == annotationType }
            .mapNotNull { loadClass(it.clazz.className) }
    }

    override fun getClassesAnnotatedByWithData(annotation: Class<out Annotation>): List<Pair<Class<*>, Map<String, Any>>> {
        val data = scanData ?: return emptyList()
        val annotationType = Type.getType(annotation)

        return data.annotations
            .filter { it.targetType == ElementType.TYPE && it.annotationType == annotationType }
            .mapNotNull { ad ->
                loadClass(ad.clazz.className)?.let { clazz -> clazz to ad.annotationData }
            }
    }

    override fun getSubtypesOf(type: Class<*>): List<Class<*>> {
        if (classesByType.isEmpty()) return emptyList()
        val targetType = Type.getType(type)

        return classesByType.values
            .filter { it.clazz != targetType && isSubtypeOf(it, targetType) }
            .mapNotNull { loadClass(it.clazz.className) }
    }

    private fun isSubtypeOf(
        classData: ModFileScanData.ClassData,
        targetType: Type,
        visited: MutableSet<Type> = mutableSetOf()
    ): Boolean {
        if (!visited.add(classData.clazz)) return false

        if (classData.parent == targetType) return true
        if (classData.interfaces.contains(targetType)) return true

        classData.parent
            ?.let { classesByType[it] }
            ?.let { if (isSubtypeOf(it, targetType, visited)) return true }

        return classData.interfaces.any { iface ->
            classesByType[iface]?.let { isSubtypeOf(it, targetType, visited) } == true
        }
    }

    private fun loadClass(name: String): Class<*>? =
        runCatching { Class.forName(name, false, javaClass.classLoader) }
            .onFailure {

            }
            .getOrNull()
}