package com.mochibit.defcon.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CommandInfo(val name: String, val permission: String = "", val requiresPlayer: Boolean)
