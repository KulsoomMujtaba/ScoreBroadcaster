package com.devhub.scored.features.players.data
import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

/**
 * Room [TypeConverter]s for serialising a [List] of [Player] snapshots to/from a
 * JSON string stored in a single column.
 *
 * [Player] is a lightweight value object (id, name, sourceProfileId, userId,
 * sourceType, externalUserId) so embedding the list as JSON is simpler and more
 * appropriate than a separate join table.
 * No external JSON library is required — [org.json] is part of the Android SDK.
 *
 * Note: [Player.type] is a computed property derived from [Player.sourceProfileId] and
 * is therefore not stored explicitly in JSON.
 *
 * Backward compatibility: rows written before the [PlayerOrigin] fields were introduced
 * will not have those keys.  Missing "sourceType" defaults to [PlayerOrigin.LOCAL].
 * Missing "userId" / "externalUserId" default to null.
 */
class PlayerListTypeConverter {

    @TypeConverter
    fun fromPlayerList(players: List<Player>): String {
        val array = JSONArray()
        players.forEach { player ->
            val obj = JSONObject()
            obj.put("id", player.id)
            obj.put("name", player.name)
            obj.put("sourceProfileId", player.sourceProfileId ?: JSONObject.NULL)
            obj.put("userId", player.userId ?: JSONObject.NULL)
            obj.put("sourceType", player.sourceType.name)
            obj.put("externalUserId", player.externalUserId ?: JSONObject.NULL)
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toPlayerList(json: String): List<Player> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val sourceType = if (obj.has("sourceType")) {
                PlayerOrigin.valueOf(obj.getString("sourceType"))
            } else {
                PlayerOrigin.LOCAL
            }
            Player(
                id = obj.getString("id"),
                name = obj.getString("name"),
                sourceProfileId = obj.nullableString("sourceProfileId"),
                userId = obj.nullableString("userId"),
                sourceType = sourceType,
                externalUserId = obj.nullableString("externalUserId")
            )
        }
    }

    /**
     * Returns the string value for [key], or null if the key is absent or its value is
     * [JSONObject.NULL].  Provides a uniform null-safe accessor for optional columns.
     */
    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)
}
