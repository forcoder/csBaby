package com.csbaby.kefu.data.remote

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * 统一 TypeAdapterFactory，兼容服务端返回格式差异：
 * - Boolean 字段返回 0/1 数字而非 true/false
 * - 数字字段返回字符串格式而非数字
 */
class LenientTypeAdapterFactory : TypeAdapterFactory {

    override fun <T> create(gson: Gson, typeToken: TypeToken<T>): TypeAdapter<T>? {
        val rawType = typeToken.rawType
        return when (rawType) {
            Boolean::class.java, java.lang.Boolean::class.java -> BooleanAdapter() as TypeAdapter<T>
            Int::class.java, java.lang.Integer::class.java -> IntAdapter() as TypeAdapter<T>
            Long::class.java, java.lang.Long::class.java -> LongAdapter() as TypeAdapter<T>
            Float::class.java, java.lang.Float::class.java -> FloatAdapter() as TypeAdapter<T>
            Double::class.java, java.lang.Double::class.java -> DoubleAdapter() as TypeAdapter<T>
            else -> null
        }
    }

    // ========== Boolean 适配器：兼容 0/1 数字和 "true"/"false" 字符串 ==========

    private class BooleanAdapter : TypeAdapter<Boolean?>() {
        override fun write(out: JsonWriter, value: Boolean?) {
            out.value(value)
        }

        override fun read(`in`: JsonReader): Boolean? {
            return when (`in`.peek()) {
                JsonToken.NULL -> { `in`.nextNull(); false }
                JsonToken.NUMBER -> `in`.nextInt() == 1
                JsonToken.STRING -> {
                    val v = `in`.nextString()
                    v.equals("true", ignoreCase = true) || v == "1"
                }
                else -> `in`.nextBoolean()
            }
        }
    }

    // ========== Int 适配器：兼容字符串格式数字 ==========

    private class IntAdapter : TypeAdapter<Int?>() {
        override fun write(out: JsonWriter, value: Int?) {
            out.value(value)
        }

        override fun read(`in`: JsonReader): Int? {
            return when (`in`.peek()) {
                JsonToken.NULL -> { `in`.nextNull(); 0 }
                JsonToken.STRING -> `in`.nextString().toIntOrNull() ?: 0
                JsonToken.NUMBER -> `in`.nextInt()
                else -> { `in`.skipValue(); 0 }
            }
        }
    }

    // ========== Long 适配器：兼容字符串格式数字 ==========

    private class LongAdapter : TypeAdapter<Long?>() {
        override fun write(out: JsonWriter, value: Long?) {
            out.value(value)
        }

        override fun read(`in`: JsonReader): Long? {
            return when (`in`.peek()) {
                JsonToken.NULL -> { `in`.nextNull(); 0L }
                JsonToken.STRING -> `in`.nextString().toLongOrNull() ?: 0L
                JsonToken.NUMBER -> `in`.nextLong()
                else -> { `in`.skipValue(); 0L }
            }
        }
    }

    // ========== Float 适配器：兼容字符串格式数字 ==========

    private class FloatAdapter : TypeAdapter<Float?>() {
        override fun write(out: JsonWriter, value: Float?) {
            out.value(value)
        }

        override fun read(`in`: JsonReader): Float? {
            return when (`in`.peek()) {
                JsonToken.NULL -> { `in`.nextNull(); 0f }
                JsonToken.STRING -> `in`.nextString().toFloatOrNull() ?: 0f
                JsonToken.NUMBER -> `in`.nextDouble().toFloat()
                else -> { `in`.skipValue(); 0f }
            }
        }
    }

    // ========== Double 适配器：兼容字符串格式数字 ==========

    private class DoubleAdapter : TypeAdapter<Double?>() {
        override fun write(out: JsonWriter, value: Double?) {
            out.value(value)
        }

        override fun read(`in`: JsonReader): Double? {
            return when (`in`.peek()) {
                JsonToken.NULL -> { `in`.nextNull(); 0.0 }
                JsonToken.STRING -> `in`.nextString().toDoubleOrNull() ?: 0.0
                JsonToken.NUMBER -> `in`.nextDouble()
                else -> { `in`.skipValue(); 0.0 }
            }
        }
    }
}