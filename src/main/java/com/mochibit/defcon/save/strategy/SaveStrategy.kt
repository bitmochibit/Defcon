package com.mochibit.defcon.save.strategy

interface SaveStrategy<T> {
    fun save(data: T)
    fun get(id: Int): T
    fun getAll(): HashSet<T>
    fun delete(data: T)
}