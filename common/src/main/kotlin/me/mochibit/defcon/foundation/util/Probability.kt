package me.mochibit.defcon.foundation.util

import kotlin.random.Random

/** Roll a probability in [0.0, 1.0). E.g. `0.3.roll()` -> ~30% true. */
fun Double.roll(): Boolean = Random.nextDouble() < this.coerceIn(0.0, 1.0)

/** Roll a probability in [0.0, 1.0). E.g. `0.3.roll()` -> ~30% true. */
fun Float.roll(): Boolean = Random.nextFloat() < this.coerceIn(0.0f, 1.0f)

/** Roll a percentage, e.g. `30.percentRoll()` -> ~30% true. */
fun Double.percentRoll(): Boolean = (this / 100).roll()
fun Int.percentRoll(): Boolean = (this / 100.0).roll()

/** True on average one time in [n]. `oneIn(4)` -> 25% true. */
fun oneIn(n: Int): Boolean = Random.nextInt(n) == 0
