/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 *
 * Originally from intellij-elm
 */

package org.swiftformat.plugin

import com.intellij.notification.*
import com.intellij.openapi.project.Project

val NOTIFICATION_GROUP: NotificationGroup =
    NotificationGroupManager.getInstance().getNotificationGroup("swift-format")

/**
 * Show a balloon notification along with action(s). The notification will be automatically
 * dismissed when an action is invoked.
 *
 * @param content The main content to be shown in the notification
 * @param type The notification type
 * @param actions Optional list of actions to be included in the notification
 */
@Suppress("DialogTitleCapitalization")
fun Project.showBalloon(
    content: String,
    type: NotificationType,
    vararg actions: Pair<String, (() -> Unit)>
) {
  val notification = Notification(NOTIFICATION_GROUP.displayId, "swift-format", content, type)

  actions.forEach { (title, fn) ->
    notification.addAction(
        NotificationAction.create(title) { _, notif ->
          notif.hideBalloon()
          fn()
        })
  }
  notification.notify(this)
}
