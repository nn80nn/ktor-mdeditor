package n.learn

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import n.learn.routes.authRoutes
import n.learn.routes.documentRoutes

fun Application.configureRouting() {
    val uploadsDir = environment.config.propertyOrNull("uploads.dir")?.getString() ?: "uploads"

    routing {
        get("/") {
            call.respondText("MDEditor API is running")
        }
        authRoutes(this@configureRouting)
        documentRoutes(uploadsDir)
    }
}
