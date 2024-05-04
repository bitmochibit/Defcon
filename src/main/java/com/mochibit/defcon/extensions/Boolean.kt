package com.mochibit.defcon.extensions

fun Boolean.toByte() : Byte {
    return if (this) 1 else 0
}