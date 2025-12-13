    package se.strawberry.service.wiremock

    import com.github.tomakehurst.wiremock.stubbing.ServeEvent
    import com.github.tomakehurst.wiremock.stubbing.StubMapping


    interface WireMockClient {
        fun addStub(stub: StubMapping)
        fun removeStub(stub: StubMapping)
        fun listStubs(): List<StubMapping>
        fun resetRequests()

        fun listServeEvents(): List<ServeEvent>
        fun findServeEvent(id: String): ServeEvent?
    }

