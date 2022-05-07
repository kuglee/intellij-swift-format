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

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.*
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.stateStore
import com.intellij.ui.LayeredIcon
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.popup.PopupState
import com.intellij.util.PathUtil
import com.intellij.util.io.exists
import com.intellij.util.ui.*
import java.awt.Container
import java.awt.Dimension
import java.awt.Insets
import java.io.File
import java.nio.file.Path
import java.util.function.Supplier
import javax.swing.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.SystemIndependent
import org.swiftformat.plugin.utils.SwiftSuggest

const val swiftFormatTool = "swift-format"
private val log = Logger.getInstance("org.swiftformat.plugin.SwiftFormatConfigurable")

@Suppress("UnstableApiUsage", "DialogTitleCapitalization")
class SwiftFormatConfigurable(private val project: Project) :
    Configurable, Configurable.NoMargin, Configurable.NoScroll, Disposable {
  private val settings = SwiftFormatSettings.getInstance(project)
  private var configuration = readConfiguration() ?: Configuration()
  private lateinit var mainPanel: DialogPanel
  private lateinit var configurationTabbedPane: JBTabbedPane
  private lateinit var restoreDefaultsPanel: Panel
  private lateinit var popup: DialogPanel
  private lateinit var storeAsProjectFileCheckBox: Cell<JBCheckBox>
  private lateinit var useCustomConfigurationCheckBox: Cell<JBCheckBox>
  private val storeAsFileGearButton = createStoreAsFileGearButton()
  private val mainPanelInsets = Insets(5, 16, 10, 16)
  private var currentSwiftFormatConfigPath = swiftFormatConfigFolderPath

  init {
    Disposer.register(project, this)
  }

  private var swiftFormatConfigFolderPath: String
    get() = settings.getSwiftFormatConfigFolderPath(project)
    set(newValue) {
      settings.setSwiftFormatConfigFolderPath(newValue)
    }

  private val swiftFormatConfigFilePath: String
    get() = settings.getSwiftFormatConfigFilePath(project)

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

  private fun JBTabbedPane.add(title: String, component: JComponent, scrollbar: Boolean = false) {
    this.add(
        title,
        if (scrollbar) {
          panel {
            row {
                  cell(component, JBScrollPane(component).also { it.border = JBEmptyBorder(0) })
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
          pathFieldWithAutoDiscoverButton(swiftFormatTool) {
            it.bindText(settings::swiftFormatPath)
          }
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
      storeAsProjectFileCheckBox =
          checkBox("Store as project file")
              .gap(RightGap.SMALL)
              .visibleIf(useCustomConfigurationCheckBox.selected)
              .bindSelected(settings::shouldSaveToProject)
              .applyToComponent {
                addActionListener {
                  storeAsFileGearButton.isEnabled = isSelected

                  if (!isSelected || currentSwiftFormatConfigPath.isBlank()) {
                    currentSwiftFormatConfigPath = project.dotIdeaFolderPath
                  }

                  if (isSelected) {
                    manageStorageFileLocation()
                  }
                }
              }
      cell(
              storeAsFileGearButton.apply {
                isEnabled = storeAsProjectFileCheckBox.component.isSelected
              })
          .visibleIf(useCustomConfigurationCheckBox.selected)
    }
  }

  object Icons {
    val gearWithDropdownIcon = LayeredIcon(AllIcons.General.GearPlain, AllIcons.General.Dropdown)
    val gearWithDropdownDisabledIcon =
        LayeredIcon(
            IconLoader.getDisabledIcon(AllIcons.General.GearPlain),
            IconLoader.getDisabledIcon(AllIcons.General.Dropdown))

    val gearWithDropdownErrorIcon = LayeredIcon(AllIcons.General.Error, AllIcons.General.Dropdown)
  }

  private fun createStoreAsFileGearButton(): ActionButton {
    val state = PopupState.forBalloon()
    val showStoragePathAction: AnAction =
        object : DumbAwareAction() {
          override fun actionPerformed(e: AnActionEvent) {
            if (!state.isRecentlyHidden) {
              manageStorageFileLocation(state)
            }
          }
        }

    val presentation = Presentation("Manage File Location")
    presentation.icon = Icons.gearWithDropdownIcon
    presentation.disabledIcon = Icons.gearWithDropdownDisabledIcon

    return object :
        ActionButton(
            showStoragePathAction,
            presentation,
            ActionPlaces.UNKNOWN,
            ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      override fun getIcon(): Icon =
          if (storeAsProjectFileCheckBox.component.isSelected &&
              getErrorIfBadFolderPathForStoringInArbitraryFile(
                  project, currentSwiftFormatConfigPath) != null) {
            Icons.gearWithDropdownErrorIcon
          } else {
            super.getIcon()
          }
    }
  }

  private fun manageStorageFileLocation(state: PopupState<Balloon>? = null) {
    val balloonDisposable = Disposer.newDisposable()
    val pathToErrorMessage = { path: String? ->
      getErrorIfBadFolderPathForStoringInArbitraryFile(project, path)
    }

    var balloon: Balloon? = null
    popup = popup(pathToErrorMessage, balloonDisposable) { balloon?.hide() }
    balloon =
        JBPopupFactory.getInstance()
            .createBalloonBuilder(popup)
            .setDialogMode(true)
            .setBorderInsets(Insets(15, 15, 7, 15))
            .setFillColor(UIUtil.getPanelBackground())
            .setHideOnAction(false)
            .setHideOnLinkClick(false)
            .setHideOnKeyOutside(false)
            .setBlockClicksThroughBalloon(true)
            .setRequestFocus(true)
            .createBalloon()
            .apply {
              setAnimationEnabled(true)

              addListener(
                  object : JBPopupListener {
                    override fun onClosed(event: LightweightWindowEvent) {
                      popup.apply()
                    }
                  })
            }

    state?.prepareToShow(balloon)
    balloon.show(
        RelativePoint.getSouthOf(storeAsProjectFileCheckBox.component), Balloon.Position.below)
  }

  private fun swiftFormatConfigPathIsModified() =
      swiftFormatConfigFolderPath != currentSwiftFormatConfigPath

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
      comboBox(FileScopedDeclarationPrivacy.AccessLevel.values())
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

  private fun Row.pathFieldWithAutoDiscoverButton(
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
    val prettyJson = Json { prettyPrint = true }

    val configurationString = prettyJson.encodeToString(configuration)
    val configFilePath = swiftFormatConfigFilePath

    try {
      File(configFilePath).writeText(configurationString)
    } catch (e: Exception) {
      log.error(ConfigError.writeErrorMessage, e)
    }
  }

  private fun popup(
      pathToErrorMessage: (String?) -> String?,
      uiDisposable: Disposable,
      closePopupAction: (() -> Unit)? = null
  ): DialogPanel =
      panel {
            row {
              configPathTextField(project, pathToErrorMessage, uiDisposable)
                  .bindText(
                      getter = ::currentSwiftFormatConfigPath,
                      setter = { currentSwiftFormatConfigPath = it })
                  .label("Store configuration file in:", LabelPosition.TOP)
            }
            row {
              button("Done") { closePopupAction?.let { it1 -> it1() } }
                  .horizontalAlign(HorizontalAlign.RIGHT)
            }
          }
          .apply {
            isFocusCycleRoot = true
            focusTraversalPolicy = LayoutFocusTraversalPolicy()
          }
}

@Suppress("UnstableApiUsage")
private fun Row.configPathTextField(
    project: Project,
    pathToErrorMessage: (String?) -> String?,
    uiDisposable: Disposable
): Cell<TextFieldWithBrowseButton> =
    textFieldWithBrowseButton(
            "Select Path",
            project,
            object : FileChooserDescriptor(false, true, false, false, false, false) {
              override fun isFileVisible(file: VirtualFile, showHiddenFiles: Boolean): Boolean {
                return if (file.path == project.dotIdeaFolderPath) true
                else file.isDirectory && super.isFileVisible(file, showHiddenFiles)
              }

              override fun isFileSelectable(file: VirtualFile?): Boolean {
                if (file == null) {
                  return false
                }

                return if (file.path == project.dotIdeaFolderPath) {
                  true
                } else {
                  file.isDirectory &&
                      super.isFileSelectable(file) &&
                      ProjectFileIndex.getInstance(project).isInContent(file)
                }
              }
            })
        .applyToComponent {
          preferredSize = Dimension(500, preferredSize.height)

          ComponentValidator(uiDisposable)
              .withValidator(
                  Supplier {
                    val errorMessage = pathToErrorMessage(text)
                    if (errorMessage != null) {
                      ValidationInfo(errorMessage, this)
                    } else {
                      null
                    }
                  })
              .andRegisterOnDocumentListener(textField)
              .installOn(textField)
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

val Project.dotIdeaFolderPath
  get() = stateStore.directoryStorePath.toString()

fun getErrorIfBadFolderPathForStoringInArbitraryFile(
    project: Project,
    path: @SystemIndependent String?
): String? {
  if (project.dotIdeaFolderPath == path) {
    return null
  }

  if (path.isNullOrEmpty()) {
    return "Path not specified"
  }

  if (!Path.of(path).exists()) {
    return "Path does not exist"
  }

  var file = LocalFileSystem.getInstance().findFileByPath(path)
  if (file != null && !file.isDirectory) {
    return "Folder path expected"
  }

  val folderName = PathUtil.getFileName(path)
  val parentPath = PathUtil.getParentPath(path)
  while (file == null && parentPath.isNotEmpty()) {
    if (!PathUtil.isValidFileName(folderName)) {
      return "Folder path expected"
    }

    file = LocalFileSystem.getInstance().findFileByPath(parentPath)
  }

  if (file == null) {
    return "Folder must be within the project"
  }

  if (!file.isDirectory) {
    return "Folder path expected"
  }

  return if (ProjectFileIndex.getInstance(project).getContentRootForFile(file, true) == null) {
    return if (ProjectFileIndex.getInstance(project).getContentRootForFile(file, false) == null) {
      "Folder must be within the project"
    } else {
      "Folder is excluded. Select a folder within the project"
    }
  } else {
    null
  }
}
