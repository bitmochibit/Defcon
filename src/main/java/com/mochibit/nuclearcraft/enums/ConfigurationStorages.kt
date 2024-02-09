package com.mochibit.nuclearcraft.enums

enum class ConfigurationStorages constructor(val storageFileName: String, val storagePath: String = "") {
    Items("items"),
    Blocks("blocks"),
    Config("config"),
    Structures("structures")

}
