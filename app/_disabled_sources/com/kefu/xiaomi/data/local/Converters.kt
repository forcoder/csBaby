package com.kefu.xiaomi.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kefu.xiaomi.data.model.MatchType
import com.kefu.xiaomi.data.model.ModelType
import com.kefu.xiaomi.data.model.ReplySource
import com.kefu.xiaomi.data.model.ScenarioType

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }

    @TypeConverter
    fun fromMatchType(value: MatchType): String = value.name

    @TypeConverter
    fun toMatchType(value: String): MatchType = MatchType.valueOf(value)

    @TypeConverter
    fun fromScenarioType(value: ScenarioType): String = value.name

    @TypeConverter
    fun toScenarioType(value: String): ScenarioType = ScenarioType.valueOf(value)

    @TypeConverter
    fun fromModelType(value: ModelType): String = value.name

    @TypeConverter
    fun toModelType(value: String): ModelType = ModelType.valueOf(value)

    @TypeConverter
    fun fromReplySource(value: ReplySource): String = value.name

    @TypeConverter
    fun toReplySource(value: String): ReplySource = ReplySource.valueOf(value)
}
