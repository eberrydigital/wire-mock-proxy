package se.strawberry.wiremock.templating

import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.Options
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension
import se.strawberry.common.TemplateNames
import java.net.URI

/**
 * Registers custom Handlebars helpers for response templating.
 *
 * Usage in stub:
 *   {{service-origin name=request.headers.X-Target-Service base=parameters.fallbackProxyBaseUrl}}
 * -> returns registry name if present, otherwise empty string.
 */

class ServiceTemplateHelpers(
    private val services: Map<String, URI>
) : TemplateHelperProviderExtension {

    override fun provideTemplateHelpers(): Map<String, Helper<*>> {
        val serviceOrigin = Helper<Any?> { _, options: Options ->
            val name = options.hash["name"]
                ?.toString()
                ?.trim()
                .orEmpty()

            val uri = services[name]
            (uri?.toString()) ?: ""
        }

        return mapOf(TemplateNames.SERVICE_ORIGIN to serviceOrigin)
    }

    override fun getName(): String = TemplateNames.SERVICE_TEMPLATE_HELPERS
}
