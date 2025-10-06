package se.strawberry.maintenance

import se.strawberry.admin.ServerRef
import se.strawberry.common.MetadataKeys

/**
 * Removes ephemeral stubs with ttl < now or remainingUses <= 0
 */

object EphemeralCleaner {
    fun pruneNow(now: Long = System.currentTimeMillis()): Int {
        val server = ServerRef.server
        val mappings = server.listAllStubMappings().mappings.toList()
        var removed = 0
        for (sm in mappings) {
            val md = sm.metadata ?: continue
            val expiresAt = (md[MetadataKeys.EXPIRES_AT] as? Number)?.toLong()
            val usesLeft  = (md[MetadataKeys.REMAINING_USES] as? Number)?.toInt()

            val ttlExpired = expiresAt?.let { now > it } ?: false
            val noUses     = usesLeft?.let { it <= 0 } ?: false

            if (ttlExpired || noUses) {
                server.removeStubMapping(sm)
                removed++
            }
        }
        return removed
    }
}
