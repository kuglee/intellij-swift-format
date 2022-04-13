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

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.io.exists
import com.intellij.util.ui.JBEmptyBorder
import java.awt.Container
import java.awt.Dimension
import javax.swing.JComponent
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.swiftformat.plugin.utils.SwiftSuggest

const val swiftFormatTool = "swift-format"
private val log = Logger.getInstance("org.swiftformat.plugin.SwiftFormatConfigurable")

@Suppress("UnstableApiUsage", "DialogTitleCapitalization")
class SwiftFormatConfigurable(private val project: Project) : Configurable, Disposable {
  private val settings = SwiftFormatSettings.getInstance(project)
  private var configuration: Configuration
  private lateinit var mainPanel: DialogPanel
  private lateinit var restoreDefaultsButton: Cell<ActionLink>

  init {
    Disposer.register(project, this)
    configuration = readConfiguration() ?: Configuration()
  }

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

  private fun JBTabbedPane.add(title: String, component: JComponent, scrollPane: Boolean = false) {
    this.add(
        title,
        if (scrollPane)
            panel {
              row {
                    cell(component, JBScrollPane(component).also { it.border = JBEmptyBorder(0) })
                        .horizontalAlign(HorizontalAlign.FILL)
                        .verticalAlign(VerticalAlign.FILL)
                        .resizableColumn()
                  }
                  .resizableRow()
            }
        else component)
  }

  private fun DialogPanel.applyRecursively() {
    fun applyRecursively(container: Container) {
      (container as? DialogPanel)?.apply()
      container.components.forEach {
        (it as? DialogPanel)?.apply()
        applyRecursively(it as Container)
      }
    }

    applyRecursively(this)
  }

  private fun DialogPanel.resetRecursively() {
    fun resetRecursively(container: Container) {
      (container as? DialogPanel)?.reset()
      container.components.forEach {
        (it as? DialogPanel)?.reset()
        resetRecursively(it as Container)
      }
    }

    resetRecursively(this)
  }

  private fun DialogPanel.isModifiedRecursive(): Boolean {
    fun isModifiedRecursive(container: Container): Boolean =
        (container is DialogPanel && container.isModified()) ||
            container.components.any {
              (it is DialogPanel && it.isModified()) || isModifiedRecursive(it as Container)
            }

    return isModifiedRecursive(this)
  }

  private fun restoreDefaultConfiguration() {
    restoreDefaultsButton.visible(false)

    val oldConfiguration = configuration.copy()
    configuration = defaultConfiguration.copy()
    reset()
    configuration = oldConfiguration
  }

  private fun settingsPanel(): DialogPanel = panel {
    row {
      checkBox("Enable swift-format")
          .bindSelected(
              getter = settings::isEnabled,
              setter = {
                settings.setEnabled(
                    if (it) SwiftFormatSettings.EnabledState.ENABLED else getDisabledState())
              })
      restoreDefaultsButton =
          link("Restore Defaults") { restoreDefaultConfiguration() }
              .bold()
              .horizontalAlign(HorizontalAlign.RIGHT)
              .visible(!configuration.isDefault())
    }
    row("Location:") {
      pathFieldPlusAutoDiscoverButton(swiftFormatTool) { it.bindText(settings::swiftFormatPath) }
    }
  }

  private fun tabsAndIndentsPanel(): DialogPanel = panel {
    row {
      checkBox("Use tab character")
          .bindSelected(
              getter = {
                (configuration.indentation ?: defaultConfiguration.indentation!!) is
                    Indentation.Tabs
              },
              setter = {
                if (it) {
                  configuration.indentation =
                      Indentation.Tabs(defaultConfiguration.indentation!!.count)
                } else {
                  configuration.indentation =
                      Indentation.Spaces(defaultConfiguration.indentation!!.count)
                }
              })
    }
    row("Tab size:") {
      intTextField(0..10000)
          .columns(1)
          .bindIntText(
              getter = { configuration.tabWidth ?: defaultConfiguration.tabWidth!! },
              setter = { configuration.tabWidth = it })
    }
    row("Indent:") {
      intTextField(0..10000)
          .columns(1)
          .bindIntText(
              getter = {
                configuration.indentation?.count ?: defaultConfiguration.indentation!!.count
              },
              setter = {
                if (configuration.indentation != null) {
                  configuration.indentation!!.count = it
                } else {
                  configuration.indentation = defaultConfiguration.indentation
                  configuration.indentation!!.count = it
                }
              })
    }
    indent {
      row {
        checkBox("Indent conditional compilation blocks")
            .bindSelected(
                getter = {
                  configuration.indentConditionalCompilationBlocks
                      ?: defaultConfiguration.indentConditionalCompilationBlocks!!
                },
                setter = { configuration.indentConditionalCompilationBlocks = it })
      }
      row {
        checkBox("Indent switch case labels")
            .bindSelected(
                getter = {
                  configuration.indentSwitchCaseLabels
                      ?: defaultConfiguration.indentSwitchCaseLabels!!
                },
                setter = { configuration.indentSwitchCaseLabels = it })
      }
    }
    row("Line length:") {
      intTextField(0..10000)
          .columns(1)
          .bindIntText(
              getter = { configuration.lineLength ?: defaultConfiguration.lineLength!! },
              setter = { configuration.lineLength = it })
    }
  }

  private fun lineBreaksPanel(): DialogPanel = panel {
    row {
      checkBox("Respects existing line breaks")
          .bindSelected(
              getter = {
                configuration.respectsExistingLineBreaks
                    ?: defaultConfiguration.respectsExistingLineBreaks!!
              },
              setter = { configuration.respectsExistingLineBreaks = it })
    }
    row {
      checkBox("Line break before control flow keywords")
          .bindSelected(
              getter = {
                configuration.lineBreakBeforeControlFlowKeywords
                    ?: defaultConfiguration.lineBreakBeforeControlFlowKeywords!!
              },
              setter = { configuration.lineBreakBeforeControlFlowKeywords = it })
    }
    row {
      checkBox("Line break before each argument")
          .bindSelected(
              getter = {
                configuration.lineBreakBeforeEachArgument
                    ?: defaultConfiguration.lineBreakBeforeEachArgument!!
              },
              setter = { configuration.lineBreakBeforeEachArgument = it })
    }
    row {
      checkBox("Line break before each generic requirement")
          .bindSelected(
              getter = {
                configuration.lineBreakBeforeEachGenericRequirement
                    ?: defaultConfiguration.lineBreakBeforeEachGenericRequirement!!
              },
              setter = { configuration.lineBreakBeforeEachGenericRequirement = it })
    }
    row {
      checkBox("Prioritize keeping function output together")
          .bindSelected(
              getter = {
                configuration.prioritizeKeepingFunctionOutputTogether
                    ?: defaultConfiguration.prioritizeKeepingFunctionOutputTogether!!
              },
              setter = { configuration.prioritizeKeepingFunctionOutputTogether = it })
    }
    row {
      checkBox("Line break around multiline expression chain components")
          .bindSelected(
              getter = {
                configuration.lineBreakAroundMultilineExpressionChainComponents
                    ?: defaultConfiguration.lineBreakAroundMultilineExpressionChainComponents!!
              },
              setter = { configuration.lineBreakAroundMultilineExpressionChainComponents = it })
    }
    row("Maximum blank lines:") {
      intTextField(0..10000)
          .columns(1)
          .bindIntText(
              getter = {
                configuration.maximumBlankLines ?: defaultConfiguration.maximumBlankLines!!
              },
              setter = { configuration.maximumBlankLines = it })
    }
  }

  private fun otherPanel(): DialogPanel = panel {
    row("File scoped declaration privacy:") {
      comboBox(FileScopedDeclarationPrivacy.AccessLevel.values())
          .bindItem(
              getter = {
                configuration.fileScopedDeclarationPrivacy?.accessLevel
                    ?: defaultConfiguration.fileScopedDeclarationPrivacy!!.accessLevel
              },
              setter = {
                if (it != null) {
                  configuration.fileScopedDeclarationPrivacy = FileScopedDeclarationPrivacy(it)
                }
              })
    }
  }

  private fun rulesPanel(): DialogPanel = panel {
    for (key in RuleRegistry.rules.keys.filter { it in RuleRegistry.formatterRulesKeys }) {
      row {
        checkBox(key.separateCamelCase().sentenceCase())
            .bindSelected(
                getter = {
                  configuration.rules?.getOrDefault(key, RuleRegistry.defaultRules[key])
                      ?: RuleRegistry.defaultRules.getOrDefault(key, false) ?: false
                },
                setter = {
                  configuration.rules = configuration.rules ?: RuleRegistry.rules
                  configuration.rules!![key] = it
                })
      }
    }
  }

  private fun registerDisposable(panel: DialogPanel) {
    val disposable = Disposer.newDisposable()
    panel.registerValidators(disposable)
    Disposer.register(this, disposable)
  }

  override fun createComponent(): JComponent {
    val panels =
        mapOf(
            "Tabs and Indents" to tabsAndIndentsPanel(),
            "Line breaks" to lineBreaksPanel(),
            "Other" to otherPanel(),
            "Rules" to rulesPanel(),
        )
    val configurationTabbedPane =
        JBTabbedPane().apply {
          panels.forEach { (title, panel) ->
            panel.also {
              registerDisposable(it)
              add(title, it, scrollPane = true)
            }
          }
        }

    mainPanel =
        panel {
          row { cell(settingsPanel()).horizontalAlign(HorizontalAlign.FILL) }
              .bottomGap(BottomGap.SMALL)
          row {
                cell(configurationTabbedPane)
                    .horizontalAlign(HorizontalAlign.FILL)
                    .verticalAlign(VerticalAlign.FILL)
                    .resizableColumn()
              }
              .resizableRow()
        }
            .also { it.preferredSize = Dimension(0, 0) }

    return mainPanel
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

  override fun reset() {
    mainPanel.resetRecursively()
    restoreDefaultsButton.visible(!configuration.isDefault())
  }

  override fun apply() {
    mainPanel.applyRecursively()
    restoreDefaultsButton.visible(!configuration.isDefault())
    writeConfiguration()
  }

  override fun isModified() = mainPanel.isModifiedRecursive()

  override fun getDisplayName() = "swift-format Settings"

  override fun dispose() {}

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

  private fun readConfiguration(): Configuration? {
    val path = SwiftFormatSettings.getSwiftFormatConfigFilePath(project)

    return if (path != null && path.exists()) {
      try {
        Json.decodeFromString(path.toFile().readText())
      } catch (e: Exception) {
        log.warn(ConfigError.readErrorMessage, e)
        null
      }
    } else {
      null
    }
  }

  private fun writeConfiguration() {
    val prettyJson = Json { prettyPrint = true }

    val configurationString = prettyJson.encodeToString(configuration)
    val configFilePath = SwiftFormatSettings.getSwiftFormatConfigFilePath(project)

    try {
      configFilePath?.toFile()?.writeText(configurationString)
          ?: log.error(ConfigError.writeErrorMessage)
    } catch (e: Exception) {
      log.error(ConfigError.writeErrorMessage, e)
    }
  }
}

private fun String.separateCamelCase() =
    this.replace("""((?<=\p{Ll})\p{Lu}|(?<!^)\p{Lu}(?=\p{Ll}))""".toRegex(), " $1")

private fun String.sentenceCase(): String {
  return this.split(" ")
      .joinToString(" ") { if (it == it.uppercase()) it else it.lowercase() }
      .replaceFirstChar { it.uppercase() }
}

object ConfigError {
  const val readErrorMessage = "Couldn't read configuration from file"
  const val writeErrorMessage = "Couldn't write configuration to file"
}
