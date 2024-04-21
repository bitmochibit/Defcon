package com.mochibit.defcon.save.savedata
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SaveDataInfo(
    val fileName: String,
    val filePath: String = "/data/"
)
