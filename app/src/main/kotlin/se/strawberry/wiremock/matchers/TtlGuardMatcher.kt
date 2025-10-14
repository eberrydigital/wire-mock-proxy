package se.strawberry.wiremock.matchers

import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.matching.MatchResult
import com.github.tomakehurst.wiremock.matching.RequestMatcherExtension
import se.strawberry.common.MatcherNames

class TtlGuardMatcher : RequestMatcherExtension() {
    override fun getName(): String = MatcherNames.TTL_GUARD
    override fun match(request: Request, parameters: Parameters): MatchResult {
        val expiresAt = (parameters["expiresAtMs"] as? Number)?.toLong()
            ?: return MatchResult.exactMatch()
        return if (System.currentTimeMillis() <= expiresAt) MatchResult.exactMatch()
        else MatchResult.noMatch()
    }
}