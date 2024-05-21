package com.mochibit.defcon.save

import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.save.savedata.SaveDataInfo
import com.mochibit.defcon.save.schemas.SaveSchema
import com.mochibit.defcon.save.strategy.JsonSaver
import com.mochibit.defcon.save.strategy.SaveStrategy
import java.io.File
import java.util.function.Supplier
import kotlin.math.ceil

abstract class AbstractSaveData<T : SaveSchema> (var schema : T, private val paginate : Boolean = false) {
    private val saveDataInfo = this.javaClass.getAnnotation(SaveDataInfo::class.java) ?: throw IllegalStateException("SaveDataInfo annotation not found")
    private var saveStrategy = JsonSaver(schema::class).init(saveDataInfo, paginate)
        set(value) {
            field = value
            saveStrategy.init(saveDataInfo, paginate)
        }

    protected var propertySupplier: Supplier<String> = Supplier { "" }

    fun save() {
        applySuffix()
        saveStrategy.save(schema)
    }


    fun load(): T? {
        applySuffix()
        val loaded = saveStrategy.load() ?: return null;
        schema = loaded;
        return loaded;
    }

    fun applySuffix() {
        if (saveStrategy !is JsonSaver) return;
        (saveStrategy as JsonSaver<T>).fileNameSuffix = "-${propertySupplier.get()}"
    }

    fun nextPage() {
        if (saveStrategy !is JsonSaver) return
        (saveStrategy as JsonSaver<T>).nextPage();
    }

    fun setPage(page: Int) {
        if (saveStrategy !is JsonSaver) return
        (saveStrategy as JsonSaver<T>).setPage(page);
    }

    fun previousPage() {
        if (saveStrategy !is JsonSaver) return
        (saveStrategy as JsonSaver<T>).previousPage();
    }

    fun pageExists(): Boolean {
        applySuffix()
        if (saveStrategy !is JsonSaver) return false;
        return (saveStrategy as JsonSaver<T>).pageExists();
    }
}