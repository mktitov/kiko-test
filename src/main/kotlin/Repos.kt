package tim.kiko

import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class Flat(val id: Int, val address: String, val currentTenantId: Int?)
data class ViewSchedule(val flatId: Int, val time: Int, val requestedBy: Int?, val agreed: Boolean?)

sealed class Notification
data class ReservationConfirmation(val flatId: Int, val time: Int, val agreed: Boolean) : Notification()
data class NeedConfirmation(val flatId: Int, val time: Int) : Notification()
data class ReservationCanceled(val flatId: Int, val time: Int) : Notification()

data class NotificationUpdate(val id: Int, val notification : Notification)

interface SchedulesRepo {
    suspend fun getAll(flatId: Int): Collection<ViewSchedule>?
    suspend fun get(flatId: Int, time: Int): ViewSchedule?
    suspend fun update(viewSchedule: ViewSchedule)
}

interface NotificationsRepo {
    suspend fun get(tenantId: Int, fromId: Int?): Collection<NotificationUpdate>?
    suspend fun add(tenantId: Int, notification: Notification)
}

class SchedulesRepoInMemory: SchedulesRepo {
    private val schedules = createSchedules()

    override suspend fun getAll(flatId: Int): Collection<ViewSchedule>? = schedules[flatId]?.values?.toList()

    override suspend fun get(flatId: Int, time: Int): ViewSchedule? = schedules[flatId]?.let { it[time] }

    override suspend fun update(viewSchedule: ViewSchedule) {
        val timeSlots = schedules[viewSchedule.flatId]
        if (timeSlots != null && timeSlots.containsKey(viewSchedule.time)) {
            timeSlots[viewSchedule.time] = viewSchedule
        }
    }

    companion object {
        private fun createSchedules(): Map<Int, SortedMap<Int, ViewSchedule>> {
            val schedules = ConcurrentHashMap<Int, SortedMap<Int, ViewSchedule>>()
            for (flatId in 1..10) {
                schedules[flatId] = generateTimeSlots(flatId, 7, 10, 20)
            }
            return schedules
        }

        private fun generateTimeSlots(flatId: Int, daysCount: Int, fromHour: Int, toHour: Int): SortedMap<Int, ViewSchedule> {
            val slots = sortedMapOf<Int, ViewSchedule>()
            var date = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
            for (d in 1..daysCount) {
                var time = date.plusHours(fromHour.toLong())
                for (i in fromHour..(toHour - 1) * 3) {
                    val slotTime = time.toEpochSecond().toInt()
                    slots.put(slotTime, ViewSchedule(flatId, slotTime, null, agreed = null))
                    time = time.plusMinutes(20)
                }
                date.plusDays(1)
            }
            return slots
        }
    }
}

class NotificationsRepoInMemory(): NotificationsRepo {
    private val notifications = ConcurrentHashMap<Int, SortedMap<Int, NotificationUpdate>>()

    override suspend fun get(tenantId: Int, fromId: Int?): Collection<NotificationUpdate>? {
        return notifications[tenantId]?.let {
            when(fromId) {
                null -> it.values.toList()
                else -> it.tailMap(fromId).values.toList()
            }
        }
    }

    override suspend fun add(tenantId: Int, notification: Notification) {
        notifications.getOrPut(tenantId, { sortedMapOf() }).let {
            val nextId = if (it.isEmpty()) 0 else it.values.last().id + 1
            it[nextId] = NotificationUpdate(nextId, notification)
        }
    }
}