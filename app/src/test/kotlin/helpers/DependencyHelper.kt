package helpers

import io.mockk.mockk
import se.strawberry.app.AppDependencies

object DependencyHelper {
    fun buildFakeDependency(): AppDependencies {
        return AppDependencies(
            mapper = mockk(),
            wireMockClient = mockk(),
            stubService = mockk(),
            requestService = mockk(),
            sessionRepository = mockk(),
            trafficPersister = mockk(),
            trafficBroadcastService = mockk()
        )
    }
}

