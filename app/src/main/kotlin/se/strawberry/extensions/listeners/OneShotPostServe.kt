package se.strawberry.extensions.listeners

import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.extension.ServeEventListener
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import com.github.tomakehurst.wiremock.common.Metadata
import se.strawberry.admin.ServerRef
import se.strawberry.common.ListenerNames
import se.strawberry.common.MetadataKeys


class OneShotServeEventListener() : ServeEventListener {

    override fun getName(): String = ListenerNames.ONE_SHOT

    override fun afterComplete(serveEvent: ServeEvent, parameters: Parameters) {
        val mapping = serveEvent.stubMapping ?: return
        val md: Metadata = mapping.metadata ?: return

        val expiresAtMs: Long = when (val v = md[MetadataKeys.EXPIRES_AT]) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: 0L
            else -> 0L
        }
        val usesLeft: Int = when (val v = md["remainingUses"]) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: -1
            else -> -1
        }

        val now = System.currentTimeMillis()
        if (expiresAtMs > 0 && now > expiresAtMs) {
            ServerRef.server.removeStubMapping(mapping)
            return
        }

        if (usesLeft > 0) {
            val next = usesLeft - 1
            if (next <= 0) {
                ServerRef.server.removeStubMapping(mapping)
            } else {
                val newMd = Metadata.metadata()
                    .apply {
                        attr("remainingUses", next)
                        if (expiresAtMs > 0) attr(MetadataKeys.EXPIRES_AT, expiresAtMs)
                    }
                    .build()
                mapping.metadata = newMd
                ServerRef.server.editStubMapping(mapping)
            }
        }
    }
}
