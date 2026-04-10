package n.learn

import io.ktor.server.application.*
import n.learn.plugins.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureCORS()
    configureStatusPages()
    configureDatabases()
    configureAuth()
    configureRouting()
}
