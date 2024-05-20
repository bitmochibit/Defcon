package com.mochibit.defcon.save.schemas

data class PlayerDataSchema(
    var playerUUID: String? = null,
    var radiationLevel : Double = 0.0, // The amount of radiation the player has been exposed to
) : SaveSchema {
}