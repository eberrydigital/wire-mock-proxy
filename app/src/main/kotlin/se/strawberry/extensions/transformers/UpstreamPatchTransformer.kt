package se.strawberry.extensions.transformers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.fge.jsonpatch.JsonPatch
import com.github.fge.jsonpatch.JsonPatchException
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.Response
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import org.slf4j.LoggerFactory
import se.strawberry.common.TransformerNames
import kotlin.collections.get


private object ParamKeys {
    const val JSON_PATCH = "jsonPatch"
    const val MERGE_PATCH = "mergePatch"
    const val HEADER_EDITS = "headerEdits"
    const val STATUS = "status"
}

class UpstreamPatchTransformer(
    private val mapper: ObjectMapper
) : ResponseTransformerV2 {

    private val log = LoggerFactory.getLogger(UpstreamPatchTransformer::class.java)

    override fun getName(): String = TransformerNames.UPSTREAM_PATCH
    override fun applyGlobally(): Boolean = false

    override fun transform(response: Response, serveEvent: ServeEvent): Response? {
        val params: Parameters = serveEvent.responseDefinition?.transformerParameters ?: Parameters.empty()

        val jsonPatchOps = params.get(ParamKeys.JSON_PATCH)
        val mergePatchObj = params.get(ParamKeys.MERGE_PATCH)
        val headerEditsObj = params.get(ParamKeys.HEADER_EDITS)
        val statusOverride = params.get(ParamKeys.STATUS)

        if (jsonPatchOps == null && mergePatchObj == null && headerEditsObj == null && statusOverride == null) {
            return response
        }

        val builder = createResponseBuilder(response)

        statusOverride?.let {
            val code = when (it) {
                is Number -> it.toInt()
                is String -> it.toIntOrNull()
                else -> null
            }
            if (code != null) builder.status(code)
        }

        val headers = applyHeaderEdits(response.headers ?: HttpHeaders.noHeaders(), headerEditsObj)
        builder.headers(headers)

        val contentType = (headers.getHeader("Content-Type")?.firstValue()
            ?: response.headers?.getHeader("Content-Type")?.firstValue())?.lowercase()

        val bodyBytes = response.body
        val isLikelyJson = isJsonContentType(contentType) || looksLikeJson(bodyBytes)

        if (isLikelyJson && (jsonPatchOps != null || mergePatchObj != null)) {
            try {
                patchJsonBody(bodyBytes, builder, jsonPatchOps, mergePatchObj)
            } catch (e: Exception) {
                val rid = serveEvent.request?.headers?.getHeader("X-Test-Run-Id")?.firstValue()
                log.warn(
                    "UpstreamPatchTransformer: failed to patch JSON body for {} (testRunId={}): {}",
                    serveEvent.request?.url, rid, e.message
                )
            }
        }

        return builder.build()
    }

    private fun createResponseBuilder(response: Response): Response.Builder =
        try { Response.Builder.like(response) }
        catch (_: Throwable) {
            Response.Builder().status(response.status).headers(response.headers).body(response.body)
        }

    private fun applyHeaderEdits(originalHeaders: HttpHeaders, headerEditsObj: Any?): HttpHeaders {
        var headers = originalHeaders
        if (headerEditsObj is Map<*, *>) {
            val removeNames: Set<String> =
                (headerEditsObj["remove"] as? Map<*, *>)?.keys
                    ?.mapNotNull { it?.toString() }?.map { it.lowercase() }?.toSet()
                    ?: emptySet()

            val setHeaders: List<HttpHeader> =
                (headerEditsObj["set"] as? Map<*, *>)?.entries
                    ?.mapNotNull { (k, v) -> if (k != null && v != null) HttpHeader.httpHeader(k.toString(), v.toString()) else null }
                    ?: emptyList()

            val kept = headers.all().filterNot { h -> removeNames.contains(h.key().lowercase()) }
            headers = HttpHeaders(*(kept + setHeaders).toTypedArray())
        }
        return headers
    }

    private fun isJsonContentType(ct: String?): Boolean =
        ct != null && ("json" in ct) // covers application/json, application/problem+json, etc.

    private fun looksLikeJson(bytes: ByteArray?): Boolean {
        if (bytes == null || bytes.isEmpty()) return false
        // quick heuristic: ignore leading whitespace, then check first char
        var i = 0
        while (i < bytes.size && bytes[i].toInt().toChar().isWhitespace()) i++
        if (i >= bytes.size) return false
        val c = bytes[i].toInt().toChar()
        return c == '{' || c == '['
    }

    private fun patchJsonBody(
        bodyBytes: ByteArray?,
        builder: Response.Builder,
        jsonPatchOps: Any?,
        mergePatchObj: Any?
    ) {
        if (bodyBytes == null) return
        var output: JsonNode = mapper.readTree(bodyBytes)

        if (jsonPatchOps != null) output = applyJsonPatch(output, jsonPatchOps)
        if (mergePatchObj != null) {
            val mergeNode: JsonNode = mapper.valueToTree(mergePatchObj)
            output = JsonMerge.merge(output, mergeNode)
        }

        builder.body(output.toString())
    }

    private fun applyJsonPatch(input: JsonNode, jsonPatchOps: Any): JsonNode {
        val patchNode = mapper.valueToTree<JsonNode>(jsonPatchOps)
        return try {
            JsonPatch.fromJson(patchNode).apply(input)
        } catch (e: JsonPatchException) {
            log.warn("JSON Patch failed: {}", e.message)
            input
        }
    }
}

object JsonMerge {
    fun merge(target: JsonNode, patch: JsonNode): JsonNode {
        val result = target.deepCopy<JsonNode>()
        if (patch.isObject) {
            val fields = patch.fields()
            while (fields.hasNext()) {
                val entry = fields.next()
                val k = entry.key
                val v = entry.value
                if (v.isNull) {
                    if (result.isObject) (result as ObjectNode).remove(k)
                } else {
                    val existing = result.get(k)
                    if (existing != null && existing.isObject && v.isObject) {
                        val merged = merge(existing, v)
                        (result as ObjectNode).set<JsonNode>(k, merged)
                    } else {
                        (result as ObjectNode).set<JsonNode>(k, v)
                    }
                }
            }
            return result
        } else {
            return patch
        }
    }
}
