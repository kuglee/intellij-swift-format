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

import com.google.common.base.Preconditions
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.psi.codeStyle.CodeStyleManager
import kotlin.reflect.jvm.kotlinFunction

/**
 * A component that replaces the default IntelliJ [CodeStyleManager] with one that formats via
 * swift-format.
 */
internal class SwiftFormatInstaller : ProjectManagerListener {

  override fun projectOpened(project: Project) {
    installFormatter(project)
  }

  companion object {
    private fun installFormatter(project: Project) {
      var currentManager = CodeStyleManager.getInstance(project)
      if (currentManager is SwiftFormatCodeStyleManager) {
        currentManager = currentManager.delegate
      }
      setManager(project, SwiftFormatCodeStyleManager(currentManager!!))
    }

    private fun setManager(project: Project, newManager: CodeStyleManager) {
      val componentManagerImplClass =
          (Class.forName("com.intellij.serviceContainer.ComponentManagerImpl"))
      val registerServiceInstanceMethod =
          componentManagerImplClass
              .getMethod(
                  "registerServiceInstance",
                  Class::class.java,
                  Object::class.java,
                  PluginDescriptor::class.java)
              .kotlinFunction
      val platformComponentManager = componentManagerImplClass.cast(project)
      val plugin = PluginManagerCore.getPlugin(PluginId.getId("org.swiftformat.plugin"))
      Preconditions.checkState(plugin != null, "Couldn't locate our own PluginDescriptor.")
      registerServiceInstanceMethod!!.call(
          platformComponentManager, CodeStyleManager::class.java, newManager, plugin!!)
    }
  }
}
