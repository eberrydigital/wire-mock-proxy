package se.strawberry.api.models.stub

data class Ephemeral(
    val uses: Int? = 1,
    val ttlMs: Long? = null, //time to live in ms
)

data class Patch(
    val merge: Any? = null,
    val jsonPatch: List<Map<String, Any>>? = null,
)

enum class RespMode { STATIC, PATCH_UPSTREAM }

data class RespDef(
    val mode: RespMode,
    val status: Int = 200,
    val headers: Map<String, String> = emptyMap(),
    val bodyJson: Any? = null,
    val bodyText: String? = null,
    val patch: Patch? = null,
)