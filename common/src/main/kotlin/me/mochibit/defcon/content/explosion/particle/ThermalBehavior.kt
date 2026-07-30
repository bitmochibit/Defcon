package me.mochibit.defcon.content.explosion.particle

sealed interface ThermalBehavior {
    data object Static: ThermalBehavior
    data class Cooling(val coolingRate: Float, val ambientTemperature: Float = 0f): ThermalBehavior
}