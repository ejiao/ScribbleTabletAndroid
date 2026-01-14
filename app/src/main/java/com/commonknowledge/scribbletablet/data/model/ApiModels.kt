package com.commonknowledge.scribbletablet.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MARK: - Request Models

@Serializable
data class GenerateRequest(
    @SerialName("image_base64") val imageBase64: String,
    val viewport: ViewportSize,
    val assets: List<AssetInput>? = null,
    @SerialName("edit_mode") val editMode: Boolean? = null
)

@Serializable
data class ViewportSize(
    @SerialName("w_px") val widthPx: Int,
    @SerialName("h_px") val heightPx: Int
)

@Serializable
data class AssetInput(
    val id: String,
    val type: AssetType,
    val content: String? = null,
    val url: String? = null,
    val box: NormalizedBox? = null,
    val metadata: Map<String, String>? = null
)

@Serializable
enum class AssetType {
    @SerialName("image") IMAGE,
    @SerialName("text") TEXT,
    @SerialName("html") HTML,
    @SerialName("audio") AUDIO,
    @SerialName("video") VIDEO
}

// MARK: - Response Models

@Serializable
data class GenerateResponse(
    @SerialName("generation_id") val generationId: String? = null,
    val actions: List<CanvasAction>
)

@Serializable
data class CanvasAction(
    val type: ActionType,
    val box: NormalizedBox? = null,
    @SerialName("target_asset_id") val targetAssetId: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    val text: String? = null,
    val html: String? = null,
    val prompt: String? = null,
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("source_asset_ids") val sourceAssetIds: List<String>? = null,
    @SerialName("transform_type") val transformType: TransformType? = null
)

@Serializable
enum class ActionType {
    @SerialName("place_image") PLACE_IMAGE,
    @SerialName("place_text") PLACE_TEXT,
    @SerialName("place_web") PLACE_WEB,
    @SerialName("place_video") PLACE_VIDEO,
    @SerialName("modify_asset") MODIFY_ASSET,
    @SerialName("delete_asset") DELETE_ASSET
}

@Serializable
enum class TransformType {
    @SerialName("img2img") IMG2IMG,
    @SerialName("upscale") UPSCALE,
    @SerialName("style_transfer") STYLE_TRANSFER,
    @SerialName("variation") VARIATION,
    @SerialName("text2video") TEXT2VIDEO,
    @SerialName("img2video") IMG2VIDEO
}

@Serializable
data class NormalizedBox(
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double
)
