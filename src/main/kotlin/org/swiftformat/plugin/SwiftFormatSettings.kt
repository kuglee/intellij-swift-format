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
import java.io.File
import java.nio.file.Path
import java.security.SecureRandom

const val swiftFormatConfigFilename = ".swift-format"

@State(name = "SwiftFormatSettings", storages = [Storage("swift-format.xml")])
internal class SwiftFormatSettings : PersistentStateComponent<SwiftFormatSettings.State> {
  private var state = State()
  private var uniqueId = java.lang.Long.toUnsignedString(SecureRandom().nextLong())

  override fun getState(): State {
    return state
  }

  override fun loadState(state: State) {
    this.state = state

    if (config is Config.Default) {
      val path = getSwiftFormatConfigFilePath(null)
      if (!path.isNullOrBlank()) {
        (config as Config.Default).configJson?.let { File(path).writeText(it) }
      }
    }
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

  fun getSwiftFormatConfigFolderPath(project: Project?): String? {
    val path = getSwiftFormatConfigFilePath(project)
    return if (path != null) Path.of(path).parent?.toString() else null
  }

  fun setSwiftFormatConfigFolderPath(newValue: String?) {
    (config as? Config.Project)?.folderPath = newValue
  }

  fun getSwiftFormatConfigFilePath(project: Project?): String? {
    return when (config) {
      is Config.Project -> {
        val swiftFormatConfigPath = (config as Config.Project).folderPath
        if (project != null &&
            getErrorIfBadFolderPathForStoringInArbitraryFile(project, swiftFormatConfigPath) ==
                null) {
          "$swiftFormatConfigPath/$swiftFormatConfigFilename"
        } else {
          null
        }
      }
      is Config.Default -> {
        "${System.getProperty("java.io.tmpdir")}/$swiftFormatConfigFilename$uniqueId"
      }
      null -> null
    }
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
