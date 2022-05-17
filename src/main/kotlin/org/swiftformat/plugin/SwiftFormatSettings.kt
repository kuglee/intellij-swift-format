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

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

const val swiftFormatConfigFilename = ".swift-format"

@State(name = "SwiftFormatSettings", storages = [Storage("swift-format.xml")])
internal class SwiftFormatSettings : PersistentStateComponent<SwiftFormatSettings.State> {
  private var state = State()

  override fun getState(): State {
    return state
  }

  override fun loadState(state: State) {
    this.state = state
  }

  var isEnabled: Boolean
    get() = state.enabled == EnabledState.ENABLED
    set(enabled) {
      setEnabled(if (enabled) EnabledState.ENABLED else EnabledState.DISABLED)
    }

  fun setEnabled(enabled: EnabledState) {
    state.enabled = enabled
  }

  val isUninitialized: Boolean
    get() = state.enabled == EnabledState.UNKNOWN

  var swiftFormatPath: String
    get() = state.swiftFormatPath
    set(swiftFormatPath) {
      state.swiftFormatPath = swiftFormatPath
    }

  fun getSwiftFormatConfigFolderPath(project: Project): String? =
      if (getErrorIfBadFolderPathForStoringInArbitraryFile(project, state.swiftFormatConfigPath) ==
          null)
          state.swiftFormatConfigPath
      else project.dotIdeaFolderPath

  fun setSwiftFormatConfigFolderPath(newValue: String?) {
    state.swiftFormatConfigPath = newValue
  }

  fun getSwiftFormatConfigFilePath(project: Project): String? {
    val configFolderPath = getSwiftFormatConfigFolderPath(project)

    return if (configFolderPath != null) "$configFolderPath/$swiftFormatConfigFilename" else null
  }

  var useCustomConfiguration: Boolean
    get() = state.useCustomConfiguration
    set(newValue) {
      state.useCustomConfiguration = newValue
    }

  var shouldSaveToProject: Boolean
    get() = state.shouldSaveToProject
    set(shouldSaveToProject) {
      state.shouldSaveToProject = shouldSaveToProject
    }

  internal enum class EnabledState {
    UNKNOWN,
    ENABLED,
    DISABLED
  }

  internal class State {
    var swiftFormatPath = ""
    var enabled = EnabledState.UNKNOWN
    var useCustomConfiguration = false
    var shouldSaveToProject = false
    var swiftFormatConfigPath: String? = null
  }

  companion object {
    fun getInstance(project: Project): SwiftFormatSettings {
      return project.getService(SwiftFormatSettings::class.java)
    }
  }
}
