package com.mochibit.defcon.customassets.items

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ModelInfo(
    val directory : String,
    val name : String,
)
