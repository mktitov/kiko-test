package tim.kiko

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor

sealed class ScheduleCmd

class GetFlat(val flatId: Int, val response: CompletableDeferred<Flat?>) : ScheduleCmd()
class GetSchedule(val flatId: Int, val response: CompletableDeferred<Collection<ViewSchedule>?>) : ScheduleCmd()
class ReserveTime(
    val flatId: Int,
    val time: Int,
    val tenantId: Int,
    val response: CompletableDeferred<Boolean>
) : ScheduleCmd()
class CancelReservation(
    val flatId: Int,
    val time: Int,
    val tenantId: Int,
    val response: CompletableDeferred<Boolean>
) : ScheduleCmd()

class ConfirmReservation(
    val flatId: Int,
    val time: Int,
    val tenantId: Int,
    val agreed: Boolean,
    val response: CompletableDeferred<Boolean>
) : ScheduleCmd()

fun CoroutineScope.scheduleActor(schedulesRepo: SchedulesRepo, notificationService: SendChannel<NotificationCmd>) =
    actor<ScheduleCmd> {
        val flats = generateFlats()
        for (cmd in channel) {
            when (cmd) {
                is GetSchedule -> cmd.response.complete(schedulesRepo.getAll(cmd.flatId))
                is GetFlat -> cmd.response.complete(flats[cmd.flatId])
                is ReserveTime -> {
                    try {
                        val schedule = schedulesRepo.get(cmd.flatId, cmd.time)
                        val flat = flats[cmd.flatId]
                        if (flat == null || schedule == null || schedule.agreed == false || schedule.requestedBy != null || cmd.tenantId == flat.currentTenantId) {
                            cmd.response.complete(false)
                        } else {
                            if (flat.currentTenantId == null) {
                                schedulesRepo.update(schedule.copy(requestedBy = cmd.tenantId, agreed = true))
                                notificationService.send(
                                    AddNotification(
                                        cmd.tenantId,
                                        ReservationConfirmation(cmd.flatId, cmd.time, agreed = true)
                                    )
                                )
                            } else {
                                schedulesRepo.update(schedule.copy(requestedBy = cmd.tenantId))
                                notificationService.send(
                                    AddNotification(
                                        flat.currentTenantId,
                                        NeedConfirmation(cmd.flatId, cmd.time)
                                    )
                                )
                            }
                            cmd.response.complete(true)
                        }
                    } catch (e: Throwable) {
                        cmd.response.completeExceptionally(e)
                    }
                }
                is ConfirmReservation -> {
                    try {
                        val schedule = schedulesRepo.get(cmd.flatId, cmd.time)
                        val flat = flats[cmd.flatId]
                        if (flat?.currentTenantId == null || flat.currentTenantId != cmd.tenantId ||
                            schedule == null || schedule.agreed != null || schedule.requestedBy == null
                        ) {
                            cmd.response.complete(false)
                        } else {
                            schedulesRepo.update(schedule.copy(agreed = cmd.agreed))
                            notificationService.send(
                                AddNotification(
                                    schedule.requestedBy,
                                    ReservationConfirmation(flat.id, cmd.time, agreed = cmd.agreed)
                                )
                            )
                            cmd.response.complete(true)
                        }
                    } catch (e: Throwable) {
                        cmd.response.completeExceptionally(e)
                    }
                }
                is CancelReservation -> {
                    try {
                        val schedule = schedulesRepo.get(cmd.flatId, cmd.time)
                        val flat = flats[cmd.flatId]
                        if (schedule?.requestedBy != cmd.tenantId) {
                            cmd.response.complete(false)
                        } else {
                            val agreed = if (schedule.agreed == false) false else null
                            schedulesRepo.update(schedule.copy(requestedBy = null, agreed = agreed))
                            if (flat?.currentTenantId != null && agreed != false) {
                                notificationService.send(
                                    AddNotification(flat.currentTenantId, ReservationCanceled(flat.id, cmd.time))
                                )
                            }
                            cmd.response.complete(true)
                        }
                    } catch (e: Throwable) {
                        cmd.response.completeExceptionally(e)
                    }
                }
            }
        }
    }

private fun generateFlats(): MutableMap<Int, Flat> {
    val flats = mutableMapOf<Int, Flat>()
    for (i in 1..10) {
        flats[i] = Flat(i, "address $i", i + 10)
    }
    return flats
}
