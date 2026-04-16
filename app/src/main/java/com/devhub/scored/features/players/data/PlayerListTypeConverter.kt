package com.devhub.scored.features.players.data
import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

/**
 * Room [TypeConverter]s for serialising a [List] of [Player] snapshots to/from a
 * JSON string stored in a single column.
 *
 * [Player] is a lightweight value object (id, name, sourceProfileId) so embedding
 * the list as JSON is simpler and more appropriate than a separate join table.
 * No external JSON library is required — [org.json] is part of the Android SDK.
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
            Player(
                id = obj.getString("id"),
                name = obj.getString("name"),
                sourceProfileId = if (obj.isNull("sourceProfileId")) null
                                  else obj.getString("sourceProfileId")
            )
        }
    }
}
