object CreateHarmonicsMod {
    const val MOD_ID = "defcon"
    private var initialized = false

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private val _registrate: CreateRegistrate =
        Registrate
            .create(MOD_ID)
            .defaultCreativeTab(null as ResourceKey<CreativeModeTab>?)
            .setTooltipModifierFactory { item ->
                ItemDescription
                    .Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                    .andThen(TooltipModifier.mapNull(KineticStats.create(item)))
            }

    val registrate: CreateRegistrate get() {
        if (!initialized) {
            throw IllegalStateException("Create registrate was not initialized!")
        }
        return _registrate
    }

    fun commonPreFreezeSetup(registry: Registry<*>) {
        autoRegister<PreFreezeCommonRegistry>(registry)
    }

    fun commonSetup(registrateConfiguration: CreateRegistrate.() -> Unit) {
        if (initialized) {
            return "Common was already initialized".err()
        }
        initialized = true
        _registrate.registrateConfiguration()
        ModDispatchers.setupEvents()
        autoRegister<CommonRegistry>()
        autoHandler<CommonGuiEventHandler>()
        autoHandler<CommonEventHandler>()
    }
}

val ModRegistrate: CreateRegistrate = CreateHarmonicsMod.registrate