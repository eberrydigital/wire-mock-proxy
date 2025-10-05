package se.strawberry.stubs

import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.matching.MatchResult
import com.github.tomakehurst.wiremock.matching.RequestMatcherExtension

class TtlGuardMatcher : RequestMatcherExtension() {
    override fun getName(): String = "ttl-guard"
    override fun match(request: Request, parameters: Parameters): MatchResult {
        val expiresAt = (parameters["expiresAtMs"] as? Number)?.toLong()
            ?: return MatchResult.exactMatch()
        return if (System.currentTimeMillis() <= expiresAt) MatchResult.exactMatch()
        else MatchResult.noMatch()
    }
}
