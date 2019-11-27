package tim.kiko

import com.google.gson.*
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.gson.gson
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.CompletableDeferred
import java.lang.reflect.Type
import java.text.DateFormat

fun Application.main() {
    install(CallLogging)
    install(DefaultHeaders)
    install(ContentNegotiation) {
        gson {
            setDateFormat(DateFormat.LONG)
            setPrettyPrinting()
            val notificationGson = Gson()
            registerTypeHierarchyAdapter(Notification::class.java, object: JsonSerializer<Notification> {
                override fun serialize(
                    src: Notification?,
                    typeOfSrc: Type?,
                    context: JsonSerializationContext?
                ): JsonElement {
                    return if (context != null && src != null) {
                        val elem = notificationGson.toJsonTree(src)
                        if (elem is JsonObject) {
                            elem.addProperty("type", src.javaClass.simpleName)
                        }
                        elem
                    } else {
                        JsonNull.INSTANCE
                    }
                }
            })
        }
    }
    val notificationsActor = notificationActor(NotificationsRepoInMemory())
    val schedulesActor = scheduleActor(SchedulesRepoInMemory(), notificationsActor)
    routing {
        get("/flats/{id}") {
            when (val id = call.parameters["id"]?.toIntOrNull()) {
                null -> call.notFound()
                else -> {
                    val resp = CompletableDeferred<Flat?>()
                    schedulesActor.send(GetFlat(id, resp))
                    when (val flat = resp.await()) {
                        null -> call.notFound()
                        else -> call.respond(flat)
                    }
                }
            }
        }
        get("/flats/{id}/schedules") {
            when (val id = call.parameters["id"]?.toIntOrNull()) {
                null -> call.notFound()
                else -> {
                    val resp = CompletableDeferred<Collection<ViewSchedule>?>()
                    schedulesActor.send(GetSchedule(id, resp))
                    when (val schedules = resp.await()) {
                        null -> call.notFound()
                        else -> call.respond(schedules)
                    }
                }
            }
        }
        get("/flats/{id}/schedules/{time}/reserve") {
            withAuthAndSchedule { tenantId, flatId, time ->
                val resp = CompletableDeferred<Boolean>()
                schedulesActor.send(ReserveTime(flatId, time, tenantId, resp))
                call.respond(resp.await())
            }
        }
        get("/flats/{id}/schedules/{time}/confirm") {
            withAuthAndSchedule { tenantId, flatId, time ->
                val resp = CompletableDeferred<Boolean>()
                schedulesActor.send(ConfirmReservation(flatId, time, tenantId, agreed = true, response = resp))
                call.respond(resp.await())
            }
        }
        get("/flats/{id}/schedules/{time}/reject") {
            withAuthAndSchedule { tenantId, flatId, time ->
                val resp = CompletableDeferred<Boolean>()
                schedulesActor.send(ConfirmReservation(flatId, time, tenantId, agreed = false, response = resp))
                call.respond(resp.await())
            }
        }
        get("/flats/{id}/schedules/{time}/cancel") {
            withAuthAndSchedule { tenantId, flatId, time ->
                val resp = CompletableDeferred<Boolean>()
                schedulesActor.send(CancelReservation(flatId, time, tenantId, resp))
                call.respond(resp.await())
            }
        }
        get("/notifications/{from?}") {
            withAuth { tenantId ->
                val from = call.parameters["from"]?.toIntOrNull()
                val response = CompletableDeferred<Collection<NotificationUpdate>>()
                notificationsActor.send(GetNotifications(tenantId, from, response))
                call.respond(response.await())
            }
        }
    }
}



private suspend fun PipelineContext<Unit, ApplicationCall>.withAuth(fn: suspend PipelineContext<Unit, ApplicationCall>.(Int) -> Unit) {
    when (val tenantId =
        call.request.queryParameters["tenantId"]?.toIntOrNull()) { //Auth not implemented so in the test I use workaround
        null -> call.respondText("", status = HttpStatusCode.Unauthorized)
        else -> fn(tenantId)
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.withAuthAndSchedule(fn: suspend PipelineContext<Unit, ApplicationCall>.(Int, Int, Int) -> Unit) {
    when (val tenantId = call.request.queryParameters["tenantId"]?.toIntOrNull()) { //Auth not implemented so in the test I use workaround
        null -> call.respondText("", status = HttpStatusCode.Unauthorized)
        else -> {
            val flatId = call.parameters["id"]?.toIntOrNull()
            val time = call.parameters["time"]?.toIntOrNull()
            if (flatId == null || time == null) {
                call.notFound()
            } else {
                fn(tenantId, flatId, time)
            }
        }
    }
}

private suspend fun ApplicationCall.notFound() {
    respondText("", status = HttpStatusCode.NotFound)
}
