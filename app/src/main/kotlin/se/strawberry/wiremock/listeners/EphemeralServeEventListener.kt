package se.strawberry.wiremock.listeners

import com.github.tomakehurst.wiremock.common.Metadata
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.extension.ServeEventListener
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import se.strawberry.common.ListenerNames
import se.strawberry.common.MetadataKeys
import se.strawberry.service.wiremock.WireMockAdmin


class EphemeralServeEventListener(
    private val admin: WireMockAdmin,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ServeEventListener {

    override fun getName(): String = ListenerNames.EPHEMERAL_LISTENER

    override fun afterComplete(serveEvent: ServeEvent, parameters: Parameters) {
        val mapping = serveEvent.stubMapping ?: return
        val md: Metadata = mapping.metadata ?: return

        val expiresAtMs: Long = when (val v = md[MetadataKeys.EXPIRES_AT]) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: 0L
            else -> 0L
        }
        val usesLeft: Int = when (val v = md[MetadataKeys.REMAINING_USES]) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: -1
            else -> -1
        }

        val now = clock()
        if (expiresAtMs > 0 && now > expiresAtMs) {
            admin.removeStub(mapping)
            return
        }

        if (usesLeft > 0) {
            val next = usesLeft - 1
            if (next <= 0) {
                admin.removeStub(mapping)
            } else {
                val newMd = Metadata.metadata()
                    .apply {
                        attr(MetadataKeys.REMAINING_USES, next)
                        if (expiresAtMs > 0) attr(MetadataKeys.EXPIRES_AT, expiresAtMs)
                    }
                    .build()
                mapping.metadata = newMd
                admin.editStub(mapping)
            }
        }
    }
}
