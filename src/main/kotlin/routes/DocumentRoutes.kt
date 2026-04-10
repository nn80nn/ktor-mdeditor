package n.learn.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import n.learn.models.Documents
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.time.LocalDateTime

@Serializable
data class DocumentInfo(
    val id: Int,
    val name: String,
    val fileSize: Long,
    val createdAt: String,
    val updatedAt: String
)

fun Route.documentRoutes(uploadsDir: String) {
    val dir = File(uploadsDir)
    if (!dir.exists()) dir.mkdirs()

    authenticate("auth-jwt") {

        get("/documents") {
            val userId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asInt()

            val docs = transaction {
                Documents.selectAll()
                    .where { Documents.userId eq userId }
                    .orderBy(Documents.updatedAt, SortOrder.DESC)
                    .map {
                        DocumentInfo(
                            id = it[Documents.id],
                            name = it[Documents.name],
                            fileSize = it[Documents.fileSize],
                            createdAt = it[Documents.createdAt].toString(),
                            updatedAt = it[Documents.updatedAt].toString()
                        )
                    }
            }

            call.respond(HttpStatusCode.OK, docs)
        }

        get("/documents/{id}") {
            val userId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asInt()
            val docId = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, MessageResponse("неверный id"))

            val doc = transaction {
                Documents.selectAll()
                    .where { (Documents.id eq docId) and (Documents.userId eq userId) }
                    .firstOrNull()
            } ?: return@get call.respond(HttpStatusCode.NotFound, MessageResponse("документ не найден"))

            val file = File(doc[Documents.filePath])
            if (!file.exists()) {
                return@get call.respond(HttpStatusCode.NotFound, MessageResponse("файл не найден"))
            }

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName, doc[Documents.name]
                ).toString()
            )
            call.respondFile(file)
        }

        post("/documents") {
            val userId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asInt()

            val multipart = call.receiveMultipart()
            var name = ""
            var savedFile: File? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        if (part.name == "name") name = part.value
                    }
                    is PartData.FileItem -> {
                        val fileName = "${System.currentTimeMillis()}_${userId}.md"
                        val file = File(dir, fileName)
                        part.streamProvider().use { input ->
                            file.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }
                        savedFile = file
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (name.isBlank() || savedFile == null) {
                savedFile?.delete()
                return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("нужно передать name и файл"))
            }

            val now = LocalDateTime.now()
            val docId = transaction {
                Documents.insert {
                    it[Documents.userId] = userId
                    it[Documents.name] = name
                    it[Documents.filePath] = savedFile!!.absolutePath
                    it[fileSize] = savedFile!!.length()
                    it[createdAt] = now
                    it[updatedAt] = now
                }[Documents.id]
            }

            call.respond(HttpStatusCode.OK, DocumentInfo(
                id = docId,
                name = name,
                fileSize = savedFile!!.length(),
                createdAt = now.toString(),
                updatedAt = now.toString()
            ))
        }

        delete("/documents/{id}") {
            val userId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asInt()
            val docId = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, MessageResponse("неверный id"))

            val doc = transaction {
                Documents.selectAll()
                    .where { (Documents.id eq docId) and (Documents.userId eq userId) }
                    .firstOrNull()
            } ?: return@delete call.respond(HttpStatusCode.NotFound, MessageResponse("документ не найден"))

            File(doc[Documents.filePath]).delete()
            transaction {
                Documents.deleteWhere { Documents.id eq docId }
            }

            call.respond(HttpStatusCode.OK, MessageResponse("удалено"))
        }
    }
}
