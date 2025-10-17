// stubs/Stubs.kt
package stubs

object Stubs {
    fun getExact(
        url: String,
        status: Int = 200,
        bodyText: String = "ok",
        uses: Int? = null,
        ttlMs: Long? = null
    ): String = buildString {
        append("""
        {
          "request": {
            "method": "GET",
            "url": { "type": "EXACT", "value": "$url" }
          },
          "response": {
            "mode": "STATIC",
            "status": $status,
            "headers": { "Content-Type": "text/plain; charset=utf-8" },
            "bodyText": "$bodyText"
          }
        """.trimIndent())
        if (uses != null || ttlMs != null) {
            append(""",
          "ephemeral": { "uses": ${uses ?: "null"}, "ttlMs": ${ttlMs ?: "null"} }
        """.trimIndent())
        }
        append("\n}")
    }
}
