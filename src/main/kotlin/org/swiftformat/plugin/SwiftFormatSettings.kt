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
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.OptionTag

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

  fun getSwiftFormatConfigFolderPath(project: Project): String? {
    when (state.config) {
      is Config.Project -> {
        val swiftFormatConfigPath = (state.config as Config.Project).folderPath
        if (getErrorIfBadFolderPathForStoringInArbitraryFile(project, swiftFormatConfigPath) ==
            null) {
          return swiftFormatConfigPath
        }
      }
      is Config.Default -> {
        return project.dotIdeaFolderPath
      }
      null -> return null
    }

    return null
  }

  fun setSwiftFormatConfigFolderPath(newValue: String?) {
    (state.config as? Config.Project)?.folderPath = newValue
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

  var config: Config?
    get() = state.config
    set(newValue) {
      state.config = newValue
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
    @OptionTag(converter = ConfigConverter::class) var config: Config? = null
  }

  companion object {
    fun getInstance(project: Project): SwiftFormatSettings {
      return project.getService(SwiftFormatSettings::class.java)
    }
  }
}

sealed class Config {
  data class Default(var configJson: String?) : Config()
  data class Project(var folderPath: String?) : Config()
}

class ConfigConverter : Converter<Config?>() {
  override fun fromString(value: String): Config =
      if (value.startsWith("{")) {
        Config.Default(value)
      } else Config.Project(value)

  override fun toString(value: Config): String =
      when (value) {
        is Config.Default -> value.configJson
        is Config.Project -> value.folderPath
      }
          ?: ""
}
