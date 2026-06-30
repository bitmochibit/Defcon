/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package me.mochibit.defcon.effects

import me.mochibit.defcon.particles.emitter.EmitterShape
import me.mochibit.defcon.utils.Gradient
import me.mochibit.defcon.utils.MathFunctions
import org.bukkit.Color
import org.joml.Vector3d
import kotlin.math.*

/**
 * Simulates nuclear explosion temperature effects with a physically accurate gradient
 * from bright core to smoke shades based on temperature profiles.
 *
 * @property minTemperatureEmission Minimum temperature for emission calculation (Kelvin)
 * @property maxTemperatureEmission Maximum temperature for emission calculation (Kelvin)
 * @property minTemperature Absolute minimum temperature the component can reach (Kelvin)
 * @property maxTemperature Absolute maximum temperature the component can reach (Kelvin)
 * @property baseCoolingRate Base rate at which temperature decreases per update
 * @property coolingExponent Exponential factor affecting cooling rate (higher values = faster cooling at high temperatures)
 * @property ambientTemperature Temperature that the component will eventually reach when cooling
 * @property smokeTransitionTemperature Temperature below which the component starts transitioning to smoke
 */
class TemperatureComponent(
    var minTemperatureEmission: Double = 600.0,
    var maxTemperatureEmission: Double = 10000.0,
    var minTemperature: Double = 300.0,
    var maxTemperature: Double = 12000.0,
    var baseCoolingRate: Double = 4.2,
    var coolingExponent: Double = 1.35,
    var ambientTemperature: Double = 300.0,
    var smokeTransitionTemperature: Double = 1200.0,
    var temperatureStartPoint: Double = 0.2,      // Default: temperature rises from the start
    var temperatureRampWidth: Double = 1.0,       // Width of temperature transition
    var temperatureProfile: TemperatureProfile = TemperatureProfile.SMOOTH_RISE,
    var turbulenceFactor: Double = 0.15,          // Turbulence for more realistic visuals
    var shockwaveWidth: Double = 0.08,            // Width of shockwave effect
    var coreIntensity: Double = 1.2               // Intensity multiplier for explosion core
) : CycledColorSupplier {

    enum class TemperatureProfile {
        SMOOTH_RISE,

        LINEAR,

        SHARP_RISE,

        PLATEAU,

        MUSHROOM,

        SHOCKWAVE
    }

    // Cache for performance optimization
    private val colorCache = mutableMapOf<Int, Color>()
    private val cacheResolution = 100
    private var cacheUpdateCounter = 0
    private val cacheUpdateFrequency = 5

    // Perlin noise approximation for turbulence
    private var timeOffset = 0.0

    override val colorSupplier: () -> Color
        get() = { calculateExplosionColor() }

    override val shapeColorSupplier: PositionColorSupply
        get() = { position, shape ->
            // Calculate 3D temperature distribution based on position
            val normalizedHeight = (position.y - shape.minHeight) / (shape.maxHeight - shape.minHeight)
            val centerDistance = calculateCenterDistance(position, shape)

            // Apply temperature profile with turbulence
            val temperatureMultiplier = calculateTemperatureMultiplier(
                normalizedHeight,
                centerDistance,
            )

            // Calculate color with temperature multiplier
            val baseTemp = smoothedTemperature * temperatureMultiplier
            val effectiveTemp = baseTemp.coerceIn(minTemperatureEmission, maxTemperatureEmission)

            if (usePhysicalModel) {
                calculatePhysicalExplosionColor(effectiveTemp)
            } else {
                // Use enhanced gradient approach
                val ratio = MathFunctions.remap(effectiveTemp, minTemperatureEmission, maxTemperatureEmission, 0.0, 1.0)
                enhancedTemperatureGradient.getColorAt(ratio)
            }
        }

    /**
     * Calculate normalized distance from center for radial effects
     */
    private fun calculateCenterDistance(position: Vector3d, shape: EmitterShape): Double {
        val centerX = shape.maxWidth / 2.0
        val centerZ = shape.maxWidth / 2.0

        val dx = position.x - centerX
        val dz = position.z - centerZ

        return sqrt(dx * dx + dz * dz) / (shape.maxWidth / 2.0)
    }

    /**
     * Calculate temperature multiplier based on position and selected profile
     * @param normalizedHeight Normalized height (0.0 to 1.0)
     * @param centerDistance Normalized distance from center (0.0 to 1.0)
     * @return Temperature multiplier (0.0 to 1.0)
     */
    private fun calculateTemperatureMultiplier(
        normalizedHeight: Double,
        centerDistance: Double,
    ): Double {
        // Apply turbulence for more realistic appearance
        val turbulence = if (turbulenceFactor > 0) {
            simplexNoise(
                centerDistance * 5.0,
                normalizedHeight * 5.0,
                timeOffset
            ) * turbulenceFactor
        } else 0.0

        // Adjust for temperature start point and ramp width
        val startPoint = temperatureStartPoint
        val rampWidth = temperatureRampWidth

        val baseMultiplier = when (temperatureProfile) {
            TemperatureProfile.SMOOTH_RISE -> {
                // Smooth exponential rise
                val normalizedRamp = (normalizedHeight - startPoint) / rampWidth
                when {
                    normalizedHeight < startPoint -> 0.0
                    normalizedHeight > startPoint + rampWidth -> 1.0
                    else -> (1 - exp(-normalizedRamp * 3.5)).coerceIn(0.0, 1.0)
                }
            }

            TemperatureProfile.LINEAR -> {
                // Linear rise
                when {
                    normalizedHeight < startPoint -> 0.0
                    normalizedHeight > startPoint + rampWidth -> 1.0
                    else -> (normalizedHeight - startPoint) / rampWidth
                }
            }

            TemperatureProfile.SHARP_RISE -> {
                // Sharp rise with steeper gradient
                val normalizedRamp = (normalizedHeight - startPoint) / rampWidth
                when {
                    normalizedHeight < startPoint -> 0.0
                    normalizedHeight > startPoint + rampWidth -> 1.0
                    else -> (normalizedRamp.pow(2.5)).coerceIn(0.0, 1.0)
                }
            }

            TemperatureProfile.PLATEAU -> {
                // Plateau with sudden increase
                when {
                    normalizedHeight < startPoint -> 0.0
                    normalizedHeight < startPoint + (rampWidth / 2) -> 0.5
                    normalizedHeight > startPoint + rampWidth -> 1.0
                    else -> 0.5 + 0.5 * ((normalizedHeight - (startPoint + rampWidth / 2)) / (rampWidth / 2))
                }
            }

            TemperatureProfile.MUSHROOM -> {
                // Mushroom cloud with prominent cap
                when {
                    normalizedHeight < 0.2 -> normalizedHeight * 2.5  // Stem base
                    normalizedHeight < 0.4 -> 0.5 + (normalizedHeight - 0.2) * 1.5  // Stem middle
                    normalizedHeight < 0.7 -> {
                        // Cap formation - hotter at edges than center for mushroom shape
                        val capFactor = 1.0 - ((centerDistance - 0.5).pow(2) * 2.0).coerceIn(0.0, 0.75)
                        0.8 + (normalizedHeight - 0.4) * 0.667 * capFactor
                    }

                    else -> 1.0 - ((normalizedHeight - 0.7) * 1.5).coerceIn(0.0, 0.8)  // Cap top cooling
                }
            }

            TemperatureProfile.SHOCKWAVE -> {
                // Shock wave with bright leading edge
                val distFromEdge = (1.0 - centerDistance).coerceAtLeast(0.0)
                val shockwaveEdge = exp(-(distFromEdge / shockwaveWidth).pow(2))

                val verticalFactor = when {
                    normalizedHeight < 0.3 -> normalizedHeight / 0.3
                    normalizedHeight > 0.8 -> 1.0 - (normalizedHeight - 0.8) * 5.0
                    else -> 1.0
                }

                // Combine factors: core heat + shockwave ring + height adjustment
                val coreThermal = (1.0 - centerDistance.pow(1.5)).coerceIn(0.0, 1.0) * coreIntensity
                val shockwaveThermal = shockwaveEdge * 0.8

                (coreThermal + shockwaveThermal).coerceIn(0.0, 1.0) * verticalFactor
            }
        }

        // Apply turbulence and 3D position effects
        return (baseMultiplier + turbulence).coerceIn(0.0, 1.0)
    }

    var temperature: Double = maxTemperature
        set(value) {
            field = value.coerceIn(minTemperature, maxTemperature)
        }

    val color: Color
        get() = calculateExplosionColor()

    // Smoothed temperature for visual transitions (prevents rapid flickering)
    private var smoothedTemperature: Double = temperature
    private val smoothingFactor: Double = 0.18  // Slightly increased for smoother transitions

    /**
     * Implements physically-based cooling following Newton's law of cooling
     * with additional exponential factor for more dramatic effects at high temperatures
     */
    fun coolDown(delta: Float = 1.0f) {
        // Dynamic cooling rate increases with temperature difference from ambient
        val temperatureDifference = temperature - ambientTemperature
        val coolingRate = baseCoolingRate * (temperatureDifference / 1000.0).pow(coolingExponent)

        // Apply cooling with delta time scaling
        temperature -= coolingRate * delta

        // Ensure we don't cool below ambient temperature
        if (temperature < ambientTemperature) {
            temperature = ambientTemperature
        }

        // Update smoothed temperature for visual rendering
        smoothedTemperature += (temperature - smoothedTemperature) * smoothingFactor * delta

        // Update time offset for turbulence animation
        timeOffset += delta * 0.05

        // Update color cache occasionally for performance
        if (++cacheUpdateCounter % cacheUpdateFrequency == 0) {
            updateColorCache()
        }
    }

    /**
     * Update the color cache for frequently used temperature values
     */
    private fun updateColorCache() {
        colorCache.clear()
        for (i in 0..cacheResolution) {
            val temp =
                minTemperatureEmission + (maxTemperatureEmission - minTemperatureEmission) * (i.toDouble() / cacheResolution)
            colorCache[i] = if (usePhysicalModel) {
                calculatePhysicalExplosionColor(temp)
            } else {
                val ratio = MathFunctions.remap(temp, minTemperatureEmission, maxTemperatureEmission, 0.0, 1.0)
                enhancedTemperatureGradient.getColorAt(ratio)
            }
        }
    }

    /**
     * Calculate explosion color based on temperature, transitioning from bright yellow core transitioning to red,
     * eventually fading to smoke shades as it cools
     */
    private fun calculateExplosionColor(): Color {
        // Use smoothed temperature to prevent flickering during rendering
        val effectiveTemp = smoothedTemperature.coerceIn(minTemperatureEmission, maxTemperatureEmission)

        // Check cache first for performance
        val cacheIndex =
            ((effectiveTemp - minTemperatureEmission) / (maxTemperatureEmission - minTemperatureEmission) * cacheResolution).toInt()
                .coerceIn(0, cacheResolution)

        colorCache[cacheIndex]?.let { return it }

        // Cache miss - calculate color
        return if (usePhysicalModel) {
            calculatePhysicalExplosionColor(effectiveTemp)
        } else {
            // Otherwise use the enhanced color gradient approach
            val ratio = MathFunctions.remap(effectiveTemp, minTemperatureEmission, maxTemperatureEmission, 0.0, 1.0)
            enhancedTemperatureGradient.getColorAt(ratio)
        }
    }

    /**
     * Calculate color using a physically accurate model for nuclear explosions,
     * properly modeling blackbody radiation with perceptual adjustments
     */
    private fun calculatePhysicalExplosionColor(
        temp: Double,
    ): Color {
        // For extremely high temperatures, produce a white-hot core
        if (temp >= 9000) {
            val whiteHotRatio = MathFunctions.remap(temp, 9000.0, maxTemperatureEmission, 0.0, 1.0)
            val baseYellow = calculatePhysicalColor(9000.0)
            return blendColors(
                Color.fromRGB(255, 255, 255),  // Pure white
                baseYellow,
                whiteHotRatio.pow(0.5)  // Non-linear transition for dramatic effect
            )
        }

        // For very low temperatures, transition to smoke
        if (temp <= smokeTransitionTemperature) {
            return calculateSmokeColor(temp)
        }

        return calculatePhysicalColor(temp)
    }

    /**
     * Improved physical color calculation using a modified blackbody radiation model
     */
    private fun calculatePhysicalColor(temp: Double): Color {
        // Modified Planckian locus approximation for more vibrant nuclear explosion colors
        var r = 0.0
        var g = 0.0
        var b = 0.0

        // Enhanced color calculations for more dramatic visuals
        // Red component - rises quickly and remains high
        r = when {
            temp >= 7000 -> 255.0
            temp >= 3000 -> 220.0 + ((temp - 3000) / 4000.0) * 35.0
            temp >= 1000 -> 150.0 + ((temp - 1000) / 2000.0) * 70.0
            else -> 50.0 + (temp / 1000.0) * 100.0
        }

        // Green component - rises more gradually, peaks lower than red
        g = when {
            temp >= 8000 -> 240.0  // Very bright yellow at highest temperatures
            temp >= 6000 -> 210.0 + ((temp - 6000) / 2000.0) * 30.0
            temp >= 4000 -> 160.0 + ((temp - 4000) / 2000.0) * 50.0
            temp >= 2000 -> 80.0 + ((temp - 2000) / 2000.0) * 80.0
            else -> 20.0 + (temp / 2000.0) * 60.0
        }

        // Blue component - minimal at low temps, rises only at very high temps
        b = when {
            temp >= 8500 -> 180.0 + ((temp - 8500) / 1500.0) * 75.0  // Approaches white at extreme temps
            temp >= 7000 -> 100.0 + ((temp - 7000) / 1500.0) * 80.0
            temp >= 5000 -> 30.0 + ((temp - 5000) / 2000.0) * 70.0
            temp >= 3000 -> 10.0 + ((temp - 3000) / 2000.0) * 20.0
            else -> 0.0 + (temp / 3000.0) * 10.0
        }

        // Apply temperature-based intensity boost for dramatic center glow
        val intensityBoost = if (temp > 7000) {
            1.0 + ((temp - 7000) / 3000.0).coerceAtMost(0.2)  // Up to 20% boost at highest temps
        } else {
            1.0
        }

        // Apply perceptual adjustments
        val intensity = (((temp - minTemperatureEmission) / (maxTemperatureEmission - minTemperatureEmission))
            .pow(0.8)  // Non-linear scaling for visual impact
            .coerceIn(0.3, 1.0)  // Minimum intensity increased to 0.3
                * intensityBoost)

        return Color.fromRGB(
            (r * intensity).toInt().coerceIn(0, 255),
            (g * intensity).toInt().coerceIn(0, 255),
            (b * intensity).toInt().coerceIn(0, 255)
        )
    }

    /**
     * Enhanced smoke color calculation with better transitions
     * between embers and smoke phases
     */
    private fun calculateSmokeColor(temp: Double): Color {
        // Map temperature to smoke transition (1.0 = just starting smoke, 0.0 = fully cooled)
        val smokeRatio = ((temp - ambientTemperature) / (smokeTransitionTemperature - ambientTemperature))
            .coerceIn(0.0, 1.0)

        // Use more dramatic non-linear transition
        val emberFactor = smokeRatio.pow(0.4)

        // More dynamic smoke evolution
        val smokeBase = if (smokeRatio > 0.8) {
            // Hot smoke is nearly black with hints of brown
            Color.fromRGB(35, 32, 30)
        } else if (smokeRatio > 0.6) {
            // Dense dark smoke
            Color.fromRGB(45, 43, 41)
        } else if (smokeRatio > 0.4) {
            // Transitional smoke
            Color.fromRGB(65, 63, 62)
        } else if (smokeRatio > 0.2) {
            // Medium gray smoke
            Color.fromRGB(90, 89, 87)
        } else {
            // Distant/cool smoke is lighter
            Color.fromRGB(130, 128, 126)
        }

        // Enhanced ember effect with deeper reds and yellows
        val emberColor = Color.fromRGB(
            (100 + (155 * emberFactor)).toInt(),
            (40 + (80 * emberFactor)).toInt(),
            (20 + (15 * emberFactor)).toInt()
        )

        // Blend between ember and smoke colors based on temperature
        return blendColors(emberColor, smokeBase, emberFactor)
    }

    /**
     * Enhanced color blending with gamma correction for more natural transitions
     */
    private fun blendColors(color1: Color, color2: Color, ratio: Double): Color {
        // Gamma-corrected color blending for more natural transitions
        val r = blendChannel(color1.red, color2.red, ratio)
        val g = blendChannel(color1.green, color2.green, ratio)
        val b = blendChannel(color1.blue, color2.blue, ratio)

        return Color.fromRGB(r, g, b)
    }

    /**
     * Blend color channels with gamma correction
     */
    private fun blendChannel(c1: Int, c2: Int, ratio: Double): Int {
        val gamma = 2.2
        val a = (c1 / 255.0).pow(gamma)
        val b = (c2 / 255.0).pow(gamma)
        return ((a * ratio + b * (1 - ratio)).pow(1.0 / gamma) * 255).toInt().coerceIn(0, 255)
    }

    /**
     * Simple 3D simplex noise approximation for turbulence effects
     */
    private fun simplexNoise(x: Double, y: Double, z: Double): Double {
        // This is a very simplified approximation of Perlin/Simplex noise
        // For a real implementation, consider using a proper noise library
        val noiseScale = 2.0
        return (sin(x * noiseScale) * cos(y * noiseScale + 0.3) * sin(z * noiseScale + 0.7) + 1) / 2
    }

    /**
     * Allows switching between physical model and gradient-based approach
     */
    var usePhysicalModel: Boolean = false

    /**
     * Enhanced gradient with more nuanced colors for nuclear effects
     */
    private val enhancedTemperatureGradient = Gradient(
        arrayOf(
            Color.fromRGB(40, 40, 40),     // Dark smoke (lowest temp)
            Color.fromRGB(60, 58, 55),     // Darker smoke with slight warmth
            Color.fromRGB(85, 80, 75),     // Medium smoke with warmth
            Color.fromRGB(110, 100, 95),   // Light smoke with warmth
            Color.fromRGB(140, 80, 60),    // Dark ember
            Color.fromRGB(160, 70, 40),    // Rich ember
            Color.fromRGB(180, 60, 30),    // Deep ember
            Color.fromRGB(200, 55, 25),    // Deep red
            Color.fromRGB(220, 60, 25),    // Rich red
            Color.fromRGB(235, 75, 30),    // Bright red
            Color.fromRGB(245, 95, 35),    // Red-orange
            Color.fromRGB(250, 120, 40),   // Orange
            Color.fromRGB(252, 150, 45),   // Golden orange
            Color.fromRGB(253, 175, 55),   // Gold
            Color.fromRGB(254, 200, 65),   // Bright gold
            Color.fromRGB(254, 225, 90),   // Yellow-gold
            Color.fromRGB(255, 240, 120),  // Brilliant yellow
            Color.fromRGB(255, 248, 160),  // Intense yellow
            Color.fromRGB(255, 252, 200),  // White-yellow (highest temp)
            Color.fromRGB(255, 255, 255)   // Pure white (extreme temp)
        )
    )

    /**
     * Set temperature with optional instant effect (no smoothing)
     * @param value New temperature value
     * @param instant If true, sets both actual and smoothed temperature together
     */
    fun setTemperature(value: Double, instant: Boolean = false) {
        temperature = value
        if (instant) {
            smoothedTemperature = temperature
        }
    }

    /**
     * Heats up the object by the specified amount
     * @param amount Amount to increase temperature
     */
    fun heatUp(amount: Double) {
        temperature += amount
    }

    override fun start() {
        smoothedTemperature = temperature
        updateColorCache()
    }

    override fun update(delta: Float) {
        coolDown(delta)
    }

    override fun stop() {
        // Clean up if needed
        colorCache.clear()
    }
}