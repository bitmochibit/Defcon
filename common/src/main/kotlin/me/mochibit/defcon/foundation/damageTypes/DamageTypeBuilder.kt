package me.mochibit.defcon.foundation.damageTypes

import net.minecraft.data.worldgen.BootstrapContext
import net.minecraft.resources.ResourceKey
import net.minecraft.world.damagesource.*


class DamageTypeBuilder(val key: ResourceKey<DamageType>) {
    var msgId: String? = null
    var scaling: DamageScaling? = null
    var exhaustion: Float = 0.0f
    var effects: DamageEffects? = null
    var deathMessageType: DeathMessageType? = null

    /**
     * Set the message ID. this is used for death message lang keys.
     *
     * @see .deathMessageType
     */
    fun msgId(msgId: String): DamageTypeBuilder {
        this.msgId = msgId
        return this
    }

    fun simpleMsgId(): DamageTypeBuilder {
        return msgId(key.location().namespace + "." + key.location().path)
    }

    /**
     * Set the scaling of this type. This determines whether damage is increased based on difficulty or not.
     */
    fun scaling(scaling: DamageScaling): DamageTypeBuilder {
        this.scaling = scaling
        return this
    }

    /**
     * Set the exhaustion of this type. This is the amount of hunger that will be consumed when an entity is damaged.
     */
    fun exhaustion(exhaustion: Float): DamageTypeBuilder {
        this.exhaustion = exhaustion
        return this
    }

    /**
     * Set the effects of this type. This determines the sound that plays when damaged.
     */
    fun effects(effects: DamageEffects): DamageTypeBuilder {
        this.effects = effects
        return this
    }

    /**
     * Set the death message type of this damage type. This determines how a death message lang key is assembled.
     *
     *  * [DeathMessageType.DEFAULT]: [DamageSource.getLocalizedDeathMessage]
     *  * [DeathMessageType.FALL_VARIANTS]: [CombatTracker.getFallMessage]
     *  * [DeathMessageType.INTENTIONAL_GAME_DESIGN]: "death.attack." + msgId, wrapped in brackets, linking to MCPE-28723
     *
     */
    fun deathMessageType(deathMessageType: DeathMessageType): DamageTypeBuilder {
        this.deathMessageType = deathMessageType
        return this
    }

    fun build(): DamageType {
        if (msgId == null) {
            simpleMsgId()
        }
        if (scaling == null) {
            scaling(DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER)
        }
        if (effects == null) {
            effects(DamageEffects.HURT)
        }
        if (deathMessageType == null) {
            deathMessageType(DeathMessageType.DEFAULT)
        }
        return DamageType(msgId, scaling, exhaustion, effects, deathMessageType)
    }

    fun register(ctx: BootstrapContext<DamageType>): DamageType {
        val type = build()
        ctx.register(key, type)
        return type
    }
}