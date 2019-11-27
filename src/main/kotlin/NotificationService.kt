package tim.kiko

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.actor

sealed class NotificationCmd
class GetNotifications(
    val tenantId: Int,
    val from: Int?,
    val response: CompletableDeferred<Collection<NotificationUpdate>>
) : NotificationCmd()

class AddNotification(val tenantId: Int, val notification: Notification) : NotificationCmd()

fun CoroutineScope.notificationActor(repo: NotificationsRepo) = actor<NotificationCmd> {
    for (cmd in channel) {
        when (cmd) {
            is GetNotifications -> cmd.response.complete(repo.get(cmd.tenantId, cmd.from) ?: emptyList())
            is AddNotification -> repo.add(cmd.tenantId, cmd.notification)
        }
    }
}