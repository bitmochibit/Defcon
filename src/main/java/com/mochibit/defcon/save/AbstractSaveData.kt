package com.mochibit.defcon.save

import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.save.savedata.SaveDataInfo
import com.mochibit.defcon.save.schemas.SaveSchema
import com.mochibit.defcon.save.strategy.JsonSaver
import com.mochibit.defcon.save.strategy.SaveStrategy
import java.util.function.Supplier

abstract class AbstractSaveData<T : SaveSchema> (var schema : T, private val splitType: FileSplitType = FileSplitType.NONE) {
    private val saveDataInfo = this.javaClass.getAnnotation(SaveDataInfo::class.java) ?: throw IllegalStateException("SaveDataInfo annotation not found")
    private var saveStrategy = JsonSaver(schema::class).init(saveDataInfo)

    protected var propertySupplier: Supplier<String> = Supplier { "" }
        set(value) {
            field = value
            setupSplit()
        }

    protected var countSplitSupplier: Supplier<Int> = Supplier { 0 }
        set(value) {
            field = value
            setupSplit()
        }


    fun save() {
        saveStrategy.save(schema)
    }

    fun load(): T? {
        val loaded = saveStrategy.load() ?: return null
        schema = loaded
        return loaded
    }


    fun setSaveStrategy(saveStrategy: SaveStrategy<T>): AbstractSaveData<T> {
        this.saveStrategy = saveStrategy
        saveStrategy.init(saveDataInfo)
        setupSplit()
        return this
    }


    protected fun setupSplit() {
        if (saveStrategy !is JsonSaver) return;
        val saveStrategy = saveStrategy as JsonSaver<T>

        when (splitType) {
            FileSplitType.PROPERTY -> {
                saveStrategy.fileNameSuffix = propertySupplier.get()
            }
            FileSplitType.COUNT -> {
                saveStrategy.fileNameSuffix = countSplitSupplier.get().toString()
            }
            FileSplitType.NONE -> {
                // Do nothing
            }
        }
    }


}

