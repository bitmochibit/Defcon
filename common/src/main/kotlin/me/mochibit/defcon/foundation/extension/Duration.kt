package me.mochibit.defcon.foundation.extension

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun Duration.inTicks(): Int = (this.inWholeMilliseconds / 50).toInt()

fun Int.ticks(): Duration = this.toDuration(DurationUnit.MILLISECONDS) * 50
fun Long.ticks(): Duration = this.toDuration(DurationUnit.MILLISECONDS) * 50