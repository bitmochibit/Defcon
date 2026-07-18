package me.mochibit.defcon.foundation.locale

import me.mochibit.defcon.DefconMod.MOD_ID
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.core.RegistryAccess
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

object ModLang : Lang {
    fun builder(): LangBuilder = LangBuilder(MOD_ID)

    fun translatedOptions(prefix: String, vararg keys: String): MutableList<Component> =
        keys.map { translate("$prefix.$it").component() }.toMutableList()

    fun blockName(state: BlockState): LangBuilder =
        builder().add(state.block.name)

    fun itemName(stack: ItemStack): LangBuilder =
        builder().add(stack.hoverName.copy())

    fun translate(langKey: String, vararg args: Any): LangBuilder =
        builder().translate(langKey, *args)

    fun text(text: String): LangBuilder = builder().text(text)
}

interface Lang {
    fun asId(name: String): String = name.lowercase()

    fun nonPluralId(name: String): String =
        asId(name).let { if (it.endsWith("s")) it.dropLast(1) else it }

    fun builder(namespace: String?): LangBuilder = LangBuilder(namespace)
}

class LangBuilder(var namespace: String?) {
    private var component: MutableComponent? = null

    fun space(): LangBuilder = text(" ")

    fun newLine(): LangBuilder = text("\n")

    /**
     * Appends a localised component.
     * To add an independently formatted localised component, use add() and a nested builder.
     */
    fun translate(langKey: String, vararg args: Any): LangBuilder {
        val resolvedArgs = resolveBuilders(args)
        return add(Component.translatable("$namespace.$langKey", *resolvedArgs))
    }

    /** Appends a text component */
    fun text(literalText: String): LangBuilder = add(Component.literal(literalText))

    /** Appends a colored text component */
    fun text(format: ChatFormatting, literalText: String): LangBuilder =
        add(Component.literal(literalText).withStyle(format))

    /** Appends a colored text component */
    fun text(color: Int, literalText: String): LangBuilder =
        add(Component.literal(literalText).withStyle { s -> s.withColor(color) })

    /** Appends the contents of another builder */
    fun add(otherBuilder: LangBuilder): LangBuilder = add(otherBuilder.component())

    /** Appends a component */
    fun add(customComponent: MutableComponent): LangBuilder {
        component = component?.append(customComponent) ?: customComponent
        return this
    }

    /** Appends a component */
    fun add(component: Component): LangBuilder =
        add(component as? MutableComponent ?: component.copy())

    /** Applies the format to all added components */
    fun style(format: ChatFormatting): LangBuilder {
        component = requireComponent().withStyle(format)
        return this
    }

    /** Applies the color to all added components */
    fun color(color: Int): LangBuilder {
        component = requireComponent().withStyle { s -> s.withColor(color) }
        return this
    }

    fun component(): MutableComponent = requireComponent()

    fun string(): String = component().string

    fun json(): String = Component.Serializer.toJson(component(), RegistryAccess.EMPTY)

    fun sendStatus(player: Player) = player.displayClientMessage(component(), true)

    fun sendChat(player: Player) = player.displayClientMessage(component(), false)

    fun addTo(tooltip: MutableList<in MutableComponent?>) {
        tooltip.add(component())
    }

    private fun requireComponent(): MutableComponent =
        component ?: error("No components were added to builder")

    companion object {
        const val DEFAULT_SPACE_WIDTH: Float = 4.0f // space width in vanilla's default font

        fun getIndents(font: Font, defaultIndents: Int): Int {
            val spaceWidth = font.width(" ")
            if (DEFAULT_SPACE_WIDTH == spaceWidth.toFloat()) {
                return defaultIndents
            }
            return Mth.ceil(DEFAULT_SPACE_WIDTH * defaultIndents / spaceWidth)
        }

        fun resolveBuilders(args: Array<out Any>): Array<Any> =
            Array(args.size) { i ->
                (args[i] as? LangBuilder)?.component() ?: args[i]
            }
    }
}