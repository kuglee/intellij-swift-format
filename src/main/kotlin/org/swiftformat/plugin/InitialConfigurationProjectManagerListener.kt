/*
 * Copyright 2022 Gábor Librecz. All Rights Reserved.
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.swiftformat.plugin

import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import org.swiftformat.plugin.utils.SwiftSuggest

internal class InitialConfigurationProjectManagerListener : ProjectManagerListener {
  override fun projectOpened(project: Project) {
    val settings = SwiftFormatSettings.getInstance(project)
    if (settings.isUninitialized) {
      settings.isEnabled = false
      settings.swiftFormatPath = SwiftSuggest.suggestTools(swiftFormatTool)?.toString() ?: ""

      if (settings.swiftFormatPath.isNotBlank()) {
        displayNewUserEnableNotification(project, settings)
      } else {
        displayNewUserConfigureNotification(project)
      }
    }
  }

  private fun displayNewUserEnableNotification(project: Project, settings: SwiftFormatSettings) {
    val enableAction = "Enable" to { settings.isEnabled = true }
    project.showBalloon(
        "The plugin is disabled by default.", NotificationType.INFORMATION, enableAction)
  }

  private fun displayNewUserConfigureNotification(project: Project) {
    val configureAction =
        "Configure" to
            {
              ShowSettingsUtil.getInstance()
                  .showSettingsDialog(project, SwiftFormatConfigurable::class.java)
            }
    project.showBalloon(
        "The plugin needs to be configured.", NotificationType.WARNING, configureAction)
  }
}
