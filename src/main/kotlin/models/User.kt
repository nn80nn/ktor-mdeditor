package n.learn.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val isConfirmed = bool("is_confirmed").default(false)
    val failedAttempts = integer("failed_attempts").default(0)
    val lockedUntil = datetime("locked_until").nullable()
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}
