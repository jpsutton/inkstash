package io.github.jpsutton.inkstash.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Lorcast v0 JSON. Lenient on purpose: the API is beta. Only the fields inkstash uses. */
@Serializable
data class LorcastSet(val id: String = "", val name: String = "", val code: String)

@Serializable
data class LorcastSetList(val results: List<LorcastSet> = emptyList())

@Serializable
data class LorcastSetRef(val id: String = "", val code: String, val name: String = "")

@Serializable
data class LorcastImages(val digital: LorcastImageSet? = null)

@Serializable
data class LorcastImageSet(val small: String? = null, val normal: String? = null, val large: String? = null)

@Serializable
data class LorcastCard(
    val id: String,
    val name: String,
    val version: String? = null,
    @SerialName("collector_number") val collectorNumber: String,
    val set: LorcastSetRef,
    val ink: String? = null,
    val inks: List<String>? = null,
    val inkwell: Boolean = false,
    val cost: Int? = null,
    val type: JsonElement? = null,
    val rarity: String? = null,
    @SerialName("image_uris") val imageUris: LorcastImages? = null,
) {
    fun toCard(): Card = Card(
        id = id,
        setCode = set.code,
        setName = set.name,
        collectorNumber = collectorNumber,
        name = name,
        version = version.orEmpty(),
        ink = ink ?: inks?.firstOrNull().orEmpty(),
        inkable = inkwell,
        cost = cost ?: 0,
        type = typeText(type),
        rarity = rarity.orEmpty().replace('_', ' '),
        imageSmall = imageUris?.digital?.small,
        imageNormal = imageUris?.digital?.normal,
        imageLarge = imageUris?.digital?.large,
    )

    private fun typeText(t: JsonElement?): String = when (t) {
        is JsonArray -> t.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(" ")
        is JsonPrimitive -> t.contentOrNull.orEmpty()
        else -> ""
    }
}

object LorcastJson {
    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun parseCards(text: String): List<LorcastCard> =
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(LorcastCard.serializer()), text)

    fun parseSets(text: String): List<LorcastSet> = json.decodeFromString(LorcastSetList.serializer(), text).results
}
