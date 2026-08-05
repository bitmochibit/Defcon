package me.mochibit.defcon.foundation.services

interface ClassScanningService {
    fun getClassesAnnotatedWith(annotation: Class<out Annotation>): List<Class<*>>

    fun getClassesAnnotatedByWithData(annotation: Class<out Annotation>): List<Pair<Class<*>, Map<String, Any>>>

    fun getSubtypesOf(type: Class<*>): List<Class<*>>
}

val classScanningService: ClassScanningService by lazy {
    loadService<ClassScanningService>()
}