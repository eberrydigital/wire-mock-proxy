package se.strawberry.admin

import com.github.tomakehurst.wiremock.WireMockServer

object ServerRef {
    @Volatile
    lateinit var server: WireMockServer
}
