package com.mochibit.defcon.extensions

fun Byte.toBoolean() : Boolean {
    return this != 0.toByte()
}