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

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.PropertyBinding
import org.swiftformat.plugin.utils.SwiftSuggest

const val swiftFormatTool = "swift-format"

@Suppress("UnstableApiUsage", "DialogTitleCapitalization")
class SwiftFormatConfigurable(private val project: Project) : Configurable {

  private fun Row.toolPathTextField(programName: String): Cell<TextFieldWithBrowseButton> {
    return textFieldWithBrowseButton(
            "Select '$programName'",
            project,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                .withFileFilter { it.name == programName }
                .also { it.isForcedToUseIdeaFileChooser = true })
        .resizableColumn()
        .horizontalAlign(HorizontalAlign.FILL)
  }

  private val settings = SwiftFormatSettings.getInstance(project)
  private lateinit var panel: DialogPanel

  // Bug?: using PropertyBinding because regular property can't be private
  private var isEnabled =
      PropertyBinding(
          get = settings::isEnabled,
          set = {
            settings.setEnabled(
                if (it) SwiftFormatSettings.EnabledState.ENABLED else getDisabledState())
          })

  override fun createComponent(): DialogPanel {
    panel = panel {
      row { checkBox("Enable swift-format").bindSelected(isEnabled) }
      row("Location:") {
        pathFieldPlusAutoDiscoverButton(swiftFormatTool) { it.bindText(settings::swiftFormatPath) }
      }
    }

    return panel
  }

  private fun Row.pathFieldPlusAutoDiscoverButton(
      executableName: String,
      fieldCallback: (Cell<TextFieldWithBrowseButton>) -> Unit
  ): Panel {
    lateinit var field: Cell<TextFieldWithBrowseButton>
    val panel = panel {
      row {
        field = toolPathTextField(executableName)
        button("Auto Discover") { field.text(autoDiscoverPathTo(executableName)) }
      }
    }

    fieldCallback(field)

    return panel
  }

  private fun autoDiscoverPathTo(programName: String) =
      SwiftSuggest.suggestTools(programName)?.toString() ?: ""

  override fun disposeUIResources() {}

  override fun reset() = panel.reset()

  override fun apply() = panel.apply()

  override fun isModified() = panel.isModified()

  override fun getDisplayName() = "Swift"

  private fun getDisabledState(): SwiftFormatSettings.EnabledState {
    // The default settings (inherited by new projects) are either 'enabled' or
    // 'show notification'. There's no way to default new projects to disabled. If someone wants
    // that, we can add another checkbox, I suppose.
    return if (project.isDefault) {
      SwiftFormatSettings.EnabledState.UNKNOWN
    } else {
      SwiftFormatSettings.EnabledState.DISABLED
    }
  }
}
