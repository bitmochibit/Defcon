package com.mochibit.defcon.save

import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.save.savedata.SaveDataInfo
import com.mochibit.defcon.save.schemas.SaveSchema
import com.mochibit.defcon.save.strategy.JsonSaver
import com.mochibit.defcon.save.strategy.SaveStrategy
import java.time.LocalDate

abstract class AbstractSaveData<T : SaveSchema> (var saveSchema: T, private val splitType: FileSplitType = FileSplitType.NONE) {
    private val saveDataInfo = this.javaClass.getAnnotation(SaveDataInfo::class.java) ?: throw IllegalStateException("SaveDataInfo annotation not found")
    private var saveStrategy = JsonSaver<T>().init(saveDataInfo)

    fun save() {
        saveStrategy.save(saveSchema)
    }

    fun load(): AbstractSaveData<T> {
        saveSchema = saveStrategy.load(saveSchema)
        return this
    }

    fun setSaveStrategy(saveStrategy: SaveStrategy<T>): AbstractSaveData<T> {
        this.saveStrategy = saveStrategy
        saveStrategy.init(saveDataInfo)
        setupSplit()
        return this
    }

    // It will be used to split the file into multiple files based on a property
    open fun propertySupplier(): String {
        return ""
    }

    open fun countSplitSupplier(): Int {
        return 0
    }



    protected fun setupSplit() {
        if (saveStrategy !is JsonSaver) return;
        val saveStrategy = saveStrategy as JsonSaver<T>

        info("reading property supplier" + propertySupplier())

        when (splitType) {
            FileSplitType.PROPERTY -> {
                saveStrategy.fileNameSuffix = propertySupplier()
            }
            FileSplitType.COUNT -> {
                saveStrategy.fileNameSuffix = countSplitSupplier().toString()
            }
            FileSplitType.NONE -> {
                // Do nothing
            }
        }
    }


}

