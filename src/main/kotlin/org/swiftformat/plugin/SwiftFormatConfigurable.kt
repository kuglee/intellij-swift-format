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

@file:Suppress("UnstableApiUsage")

package org.swiftformat.plugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.Disposer
import com.intellij.project.stateStore
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.ui.*
import java.awt.Container
import java.awt.Dimension
import java.awt.Insets
import java.io.File
import javax.swing.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.swiftformat.plugin.utils.SwiftSuggest

const val swiftFormatTool = "swift-format"
private val log = Logger.getInstance("org.swiftformat.plugin.SwiftFormatConfigurable")

class SwiftFormatConfigurable(val project: Project) :
    Configurable, Configurable.NoMargin, Configurable.NoScroll, Disposable {
  private val settings = SwiftFormatSettings.getInstance(project)
  private var configuration = readConfiguration() ?: Configuration()
  private lateinit var mainPanel: DialogPanel
  private lateinit var configurationTabbedPane: JBTabbedPane
  private lateinit var restoreDefaultsPanel: Panel
  lateinit var storeAsProjectFileCheckBox: Cell<JBCheckBox>
  private lateinit var useCustomConfigurationCheckBox: Cell<JBCheckBox>
  private val mainPanelInsets = Insets(5, 16, 10, 16)
  var currentSwiftFormatConfigPath: String? = swiftFormatConfigFolderPath

  init {
    Disposer.register(project, this)
  }

  private var swiftFormatConfigFolderPath: String?
    get() = settings.getSwiftFormatConfigFolderPath(project)
    set(newValue) {
      settings.setSwiftFormatConfigFolderPath(newValue)
    }

  private val swiftFormatConfigFilePath: String?
    get() = settings.getSwiftFormatConfigFilePath(project)

  private fun restoreDefaultConfiguration() {
    val oldConfiguration = configuration.copy()
    configuration = Configuration.defaultConfiguration.copy()
    resetConfiguration()
    configuration = oldConfiguration
  }

  private fun restoreDefaultCustomConfiguration() {
    restoreDefaultConfiguration()
    storeAsProjectFileCheckBox.component.isSelected = false
    currentSwiftFormatConfigPath = project.dotIdeaFolderPath
  }

  private fun swiftFormatConfigPathIsModified() =
      swiftFormatConfigFolderPath != currentSwiftFormatConfigPath

  private fun settingsPanel(): DialogPanel = panel {
    row {
      checkBox("Enable swift-format")
          .bindSelected(
              getter = settings::isEnabled,
              setter = {
                settings.setEnabled(
                    if (it) SwiftFormatSettings.EnabledState.ENABLED else getDisabledState())
              })
    }
    row("Location:") {
          pathFieldWithAutoDiscoverButton(
              swiftFormatTool,
              project,
              { it.bindText(settings::swiftFormatPath) },
              { autoDiscoverPath() })
        }
        .bottomGap(BottomGap.SMALL)
    row {
      useCustomConfigurationCheckBox =
          checkBox("Use custom configuration")
              .bindSelected(settings::useCustomConfiguration)
              .applyToComponent {
                addActionListener {
                  if (!isSelected) {
                    restoreDefaultCustomConfiguration()
                  }
                }
              }

      val storeAsFileGearButton = createStoreAsFileGearButton()
      storeAsProjectFileCheckBox =
          checkBox("Store as project file")
              .customize(Gaps(right = 0))
              .applyToComponent {
                addActionListener {
                  if (!isSelected || currentSwiftFormatConfigPath.isNullOrBlank()) {
                    currentSwiftFormatConfigPath = project.dotIdeaFolderPath
                  }

                  if (isSelected) {
                    manageStorageFileLocation()
                  }
                }
              }
              .bindSelected(settings::shouldSaveToProject)
              .showIfNonDefaultProject()
      cell(storeAsFileGearButton)
          .enabledIf(storeAsProjectFileCheckBox.selected)
          .customize(Gaps.EMPTY)
          .showIfNonDefaultProject()
    }
  }

  private fun tabsAndIndentsPanel(): DialogPanel = panel {
    row {
      checkBox("Use tab character")
          .bindSelected(
              getter = {
                val indentation =
                    configuration.indentation ?: Configuration.defaultConfiguration.indentation!!
                indentation is Indentation.Tabs
              },
              setter = {
                if (it) {
                  configuration.indentation =
                      Indentation.Tabs(Configuration.defaultConfiguration.indentation!!.count)
                } else {
                  configuration.indentation =
                      Indentation.Spaces(Configuration.defaultConfiguration.indentation!!.count)
                }
              })
    }
    row("Tab size:") {
      intTextField(0..10000)
          .columns(1)
          .bindIntText(
              getter = { configuration.tabWidth ?: Configuration.defaultConfiguration.tabWidth!! },
              setter = { configuration.tabWidth = it })
    }
    row("Indent:") {
      intTextField(0..10000)
          .columns(1)
          .bindIntText(
              getter = {
                configuration.indentation?.count
                    ?: Configuration.defaultConfiguration.indentation!!.count
              },
              setter = {
                if (configuration.indentation != null) {
                  configuration.indentation!!.count = it
                } else {
                  configuration.indentation =
                      Configuration.defaultConfiguration.indentation!!.copy(it)
                }
              })
    }
    indent {
      row {
        checkBox("Indent conditional compilation blocks")
            .bindSelected(
                getter = {
                  configuration.indentConditionalCompilationBlocks
                      ?: Configuration.defaultConfiguration.indentConditionalCompilationBlocks!!
                },
                setter = { configuration.indentConditionalCompilationBlocks = it })
      }
      row {
        checkBox("Indent switch case labels")
            .bindSelected(
                getter = {
                  configuration.indentSwitchCaseLabels
                      ?: Configuration.defaultConfiguration.indentSwitchCaseLabels!!
                },
                setter = { configuration.indentSwitchCaseLabels = it })
      }
    }
    row("Line length:") {
      intTextField(0..10000)
          .columns(1)
          .bindIntText(
              getter = {
                configuration.lineLength ?: Configuration.defaultConfiguration.lineLength!!
              },
              setter = { configuration.lineLength = it })
    }
  }

  private fun lineBreaksPanel(): DialogPanel = panel {
    row {
      checkBox("Respects existing line breaks")
          .bindSelected(
              getter = {
                configuration.respectsExistingLineBreaks
                    ?: Configuration.defaultConfiguration.respectsExistingLineBreaks!!
              },
              setter = { configuration.respectsExistingLineBreaks = it })
    }
    row {
      checkBox("Line break before control flow keywords")
          .bindSelected(
              getter = {
                configuration.lineBreakBeforeControlFlowKeywords
                    ?: Configuration.defaultConfiguration.lineBreakBeforeControlFlowKeywords!!
              },
              setter = { configuration.lineBreakBeforeControlFlowKeywords = it })
    }
    row {
      checkBox("Line break before each argument")
          .bindSelected(
              getter = {
                configuration.lineBreakBeforeEachArgument
                    ?: Configuration.defaultConfiguration.lineBreakBeforeEachArgument!!
              },
              setter = { configuration.lineBreakBeforeEachArgument = it })
    }
    row {
      checkBox("Line break before each generic requirement")
          .bindSelected(
              getter = {
                configuration.lineBreakBeforeEachGenericRequirement
                    ?: Configuration.defaultConfiguration.lineBreakBeforeEachGenericRequirement!!
              },
              setter = { configuration.lineBreakBeforeEachGenericRequirement = it })
    }
    row {
      checkBox("Prioritize keeping function output together")
          .bindSelected(
              getter = {
                configuration.prioritizeKeepingFunctionOutputTogether
                    ?: Configuration.defaultConfiguration.prioritizeKeepingFunctionOutputTogether!!
              },
              setter = { configuration.prioritizeKeepingFunctionOutputTogether = it })
    }
    row {
      checkBox("Line break around multiline expression chain components")
          .bindSelected(
              getter = {
                configuration.lineBreakAroundMultilineExpressionChainComponents
                    ?: Configuration.defaultConfiguration
                        .lineBreakAroundMultilineExpressionChainComponents!!
              },
              setter = { configuration.lineBreakAroundMultilineExpressionChainComponents = it })
    }
    row("Maximum blank lines:") {
      intTextField(0..10000)
          .columns(1)
          .bindIntText(
              getter = {
                configuration.maximumBlankLines
                    ?: Configuration.defaultConfiguration.maximumBlankLines!!
              },
              setter = { configuration.maximumBlankLines = it })
    }
  }

  private fun otherPanel(): DialogPanel = panel {
    row("File scoped declaration privacy:") {
      comboBox(FileScopedDeclarationPrivacy.AccessLevel.values().asList())
          .bindItem(
              getter = {
                configuration.fileScopedDeclarationPrivacy?.accessLevel
                    ?: Configuration.defaultConfiguration.fileScopedDeclarationPrivacy!!.accessLevel
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
                  configuration.rules = configuration.rules ?: RuleRegistry.rules.toMutableMap()
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
    configurationTabbedPane =
        JBTabbedPane().apply {
          tabComponentInsets = Insets(0, 0, 0, 0)

          panels.forEach { (title, panel) ->
            panel.also {
              registerDisposable(it)
              it.border = JBEmptyBorder(5, mainPanelInsets.left, 5, mainPanelInsets.right)
              add(title, it, scrollbar = true)
            }
          }
        }

    mainPanel =
        panel {
              row {
                    cell(settingsPanel())
                        .horizontalAlign(HorizontalAlign.FILL)
                        .customize(Gaps(left = mainPanelInsets.left, right = mainPanelInsets.right))
                  }
                  .bottomGap(BottomGap.MEDIUM)
              rowsRange {
                    row {
                          cell(configurationTabbedPane)
                              .horizontalAlign(HorizontalAlign.FILL)
                              .verticalAlign(VerticalAlign.FILL)
                              .resizableColumn()
                              .showIfNonDefaultProject()
                        }
                        .resizableRow()
                    restoreDefaultsPanel =
                        panel {
                              separator()
                              row {
                                link("Restore defaults") { restoreDefaultConfiguration() }
                                    .bold()
                                    .customize(
                                        Gaps(6, mainPanelInsets.left, 6, mainPanelInsets.right))
                              }
                            }
                            .visible(shouldShowRestoreDefaultsButton(configuration))
                  }
                  .visibleIf(useCustomConfigurationCheckBox.selected)
            }
            .also {
              it.border = JBEmptyBorder(mainPanelInsets.top, 0, mainPanelInsets.bottom, 0)
              it.preferredSize = Dimension(0, 0)
            }

    return mainPanel
  }

  private fun autoDiscoverPath() = SwiftSuggest.suggestTools(swiftFormatTool)?.toString() ?: ""

  override fun disposeUIResources() {}

  override fun reset() {
    mainPanel.resetRecursively()
    resetSwiftFormatConfigPath()
  }

  private fun resetSwiftFormatConfigPath() {
    currentSwiftFormatConfigPath = swiftFormatConfigFolderPath
  }

  override fun apply() {
    mainPanel.applyRecursively()
    applySwiftFormatConfigPath()
    writeConfiguration()
  }

  private fun applyConfiguration() {
    configurationTabbedPane.applyRecursively()
  }

  private fun resetConfiguration() {
    configurationTabbedPane.resetRecursively()
  }

  private fun applySwiftFormatConfigPath() {
    swiftFormatConfigFolderPath = currentSwiftFormatConfigPath
  }

  override fun isModified(): Boolean {
    val isModified = mainPanel.isModifiedRecursive() || swiftFormatConfigPathIsModified()

    restoreDefaultsPanel.visible(shouldShowRestoreDefaultsButton(getCurrentUIConfiguration()))

    return isModified
  }

  private fun getCurrentUIConfiguration(): Configuration {
    val oldConfiguration = configuration.deepCopy()
    applyConfiguration()

    val currentUIConfiguration = configuration

    configuration = oldConfiguration

    return currentUIConfiguration
  }

  // visibleIf doesn't override visible, so isSelected needs to be checked
  private fun shouldShowRestoreDefaultsButton(configuration: Configuration): Boolean =
      useCustomConfigurationCheckBox.component.isSelected && !configuration.isDefault()

  @Suppress("DialogTitleCapitalization") override fun getDisplayName() = "swift-format Settings"

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
    if (swiftFormatConfigFilePath.isNullOrBlank()) {
      return null
    }

    val path = File(swiftFormatConfigFilePath)

    return if (path.exists()) {
      try {
        Json.decodeFromString(path.readText())
      } catch (e: Exception) {
        log.warn(ConfigError.readErrorMessage, e)
        null
      }
    } else {
      null
    }
  }

  private fun writeConfiguration() {
    if (swiftFormatConfigFilePath.isNullOrBlank()) {
      return
    }

    val prettyJson = Json { prettyPrint = true }

    val configurationString = prettyJson.encodeToString(configuration)
    val configFilePath = swiftFormatConfigFilePath

    try {
      File(configFilePath).writeText(configurationString)
    } catch (e: Exception) {
      log.error(ConfigError.writeErrorMessage, e)
    }
  }

  private fun <T : JComponent> Cell<T>.showIfNonDefaultProject(): Cell<T> =
      this.apply {
        if (project.isDefault) {
          visible(false)
        } else visibleIf(useCustomConfigurationCheckBox.selected)
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

// Extensions

val Project.dotIdeaFolderPath: String?
  get() = if (!this.isDefault) stateStore.directoryStorePath.toString() else null

private fun JBTabbedPane.add(title: String, component: JComponent, scrollbar: Boolean = false) {
  this.add(
      title,
      if (scrollbar) {
        panel {
          row {
                // using cell with JBScrollPane because scrollCell adds a border
                cell(component, JBScrollPane(component).apply { border = JBEmptyBorder(0) })
                    .horizontalAlign(HorizontalAlign.FILL)
                    .verticalAlign(VerticalAlign.FILL)
                    .resizableColumn()
              }
              .resizableRow()
        }
      } else {
        panel { row { cell(component).horizontalAlign(HorizontalAlign.FILL).resizableColumn() } }
      })
}

private fun Row.toolPathTextField(
    programName: String,
    project: Project
): Cell<TextFieldWithBrowseButton> {
  return textFieldWithBrowseButton(
          "Select '$programName'",
          project,
          FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
              .withFileFilter { it.name == programName }
              .also { it.isForcedToUseIdeaFileChooser = true })
      .resizableColumn()
      .horizontalAlign(HorizontalAlign.FILL)
}

private fun Row.pathFieldWithAutoDiscoverButton(
    executableName: String,
    project: Project,
    fieldCallback: (Cell<TextFieldWithBrowseButton>) -> Unit,
    autoDiscoverCallback: () -> String
): Panel {
  lateinit var field: Cell<TextFieldWithBrowseButton>
  val panel = panel {
    row {
      field = toolPathTextField(executableName, project)
      button("Auto Discover") { field.text(autoDiscoverCallback()) }
    }
  }

  fieldCallback(field)

  return panel
}

private fun Container.resetRecursively() {
  (this as? DialogPanel)?.reset()
  components.forEach { (it as? Container)?.resetRecursively() }
}

private fun Container.applyRecursively() {
  (this as? DialogPanel)?.apply()
  components.forEach { (it as? Container)?.applyRecursively() }
}

private fun Container.isModifiedRecursive(): Boolean =
    (this is DialogPanel && isModified()) ||
        components.any { (it as? Container)?.isModifiedRecursive() ?: false }
