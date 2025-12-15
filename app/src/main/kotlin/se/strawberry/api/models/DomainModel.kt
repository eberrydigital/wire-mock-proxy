package se.strawberry.api.models

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import se.strawberry.common.Json

abstract class DomainModel {
    fun toJsonBody(): RequestBody {
        val json = "application/json; charset=utf-8".toMediaType()
        return Json.mapper.writeValueAsString(this).toRequestBody(json)
    }
}