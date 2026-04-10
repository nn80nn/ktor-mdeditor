package n.learn.routes

import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import n.learn.models.Users
import n.learn.util.generateToken
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

@Serializable
data class RegisterRequest(val email: String, val password: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class TokenResponse(val token: String)

@Serializable
data class MessageResponse(val message: String)

fun Route.authRoutes(application: Application) {

    post("/auth/register") {
        val req = call.receive<RegisterRequest>()

        if (req.email.isBlank() || req.password.length < 6) {
            call.respond(HttpStatusCode.BadRequest, MessageResponse("email и пароль обязательны, пароль минимум 6 символов"))
            return@post
        }

        val exists = transaction {
            Users.selectAll().where { Users.email eq req.email }.count() > 0
        }

        if (exists) {
            call.respond(HttpStatusCode.Conflict, MessageResponse("пользователь с таким email уже существует"))
            return@post
        }

        val hash = BCrypt.withDefaults().hashToString(12, req.password.toCharArray())

        transaction {
            Users.insert {
                it[email] = req.email
                it[passwordHash] = hash
                it[isConfirmed] = true
                it[createdAt] = LocalDateTime.now()
            }
        }

        call.respond(HttpStatusCode.OK, MessageResponse("регистрация прошла успешно"))
    }

    post("/auth/login") {
        val req = call.receive<LoginRequest>()

        val user = transaction {
            Users.selectAll().where { Users.email eq req.email }.firstOrNull()
        }

        if (user == null) {
            call.respond(HttpStatusCode.Unauthorized, MessageResponse("неверный email или пароль"))
            return@post
        }

        // проверяем блокировку
        val lockedUntil = user[Users.lockedUntil]
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            call.respond(HttpStatusCode.TooManyRequests, MessageResponse("слишком много попыток, попробуйте позже"))
            return@post
        }

        val passwordMatch = BCrypt.verifyer()
            .verify(req.password.toCharArray(), user[Users.passwordHash]).verified

        if (!passwordMatch) {
            val newAttempts = user[Users.failedAttempts] + 1
            transaction {
                Users.update({ Users.id eq user[Users.id] }) {
                    it[failedAttempts] = newAttempts
                    if (newAttempts >= 5) {
                        it[Users.lockedUntil] = LocalDateTime.now().plusMinutes(5)
                    }
                }
            }
            call.respond(HttpStatusCode.Unauthorized, MessageResponse("неверный email или пароль"))
            return@post
        }

        // сброс счётчика при успешном входе
        val nullDate: java.time.LocalDateTime? = null
        transaction {
            Users.update({ Users.id eq user[Users.id] }) {
                it[Users.failedAttempts] = 0
                it[Users.lockedUntil] = nullDate
            }
        }

        val token = application.generateToken(user[Users.id])
        call.respond(HttpStatusCode.OK, TokenResponse(token))
    }
}
