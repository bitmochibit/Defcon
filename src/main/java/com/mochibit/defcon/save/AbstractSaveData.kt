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

package com.mochibit.defcon.save

import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.save.savedata.SaveDataInfo
import com.mochibit.defcon.save.schemas.SaveSchema
import com.mochibit.defcon.save.strategy.JsonSaver
import com.mochibit.defcon.save.strategy.SaveStrategy
import java.io.File
import java.util.function.Supplier
import kotlin.math.ceil

abstract class AbstractSaveData<T : SaveSchema> (var schema : T, paginate : Boolean = false) {
    private val saveDataInfo = this.javaClass.getAnnotation(SaveDataInfo::class.java) ?: throw IllegalStateException("SaveDataInfo annotation not found")
    protected var currentPage = if(paginate) 0 else null
    private var saveStrategy = JsonSaver(saveDataInfo, schema::class)

    fun setSuffixSupplier(propertySupplier: Supplier<String>) {
        saveStrategy.fileNameSuffix = propertySupplier;
    }

    fun save() {
        saveSchema(schema, currentPage ?: 0)
    }

    fun load(): T? {
        schema = getSchema(currentPage) ?: return null;
        return schema;
    }

    fun saveSchema(schema: T, page: Int) {
        saveStrategy.save(schema, page)
    }
    fun getSchema(page: Int?): T? {
        return saveStrategy.load(page)
    }


    fun nextPage() {
        currentPage = currentPage?.plus(1) ?: 1
    }

    fun setPage(page: Int) {
        currentPage = page
    }

    fun previousPage() {
        if (currentPage == null) return
        if (currentPage == 0) return
        currentPage = currentPage?.minus(1) ?: 0
    }

    fun pageExists(page: Int): Boolean {
        return saveStrategy.pageExists(page)
    }
}