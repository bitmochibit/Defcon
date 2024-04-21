package com.mochibit.defcon.save.schemas

import com.mochibit.defcon.radiation.RadiationArea

data class RadiationAreaSchema(
    var radiationAreas: HashSet<RadiationArea> = HashSet()
) : SaveSchema


