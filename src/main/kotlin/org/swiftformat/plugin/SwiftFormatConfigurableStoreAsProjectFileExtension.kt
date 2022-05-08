/*
 * Copyright 2022 Gábor Librecz. All Rights Reserved.
 * Copyright 2022 JetBrains s.r.o. and contributors. All Rights Reserved.
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

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.*
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.LayeredIcon
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.popup.PopupState
import com.intellij.util.PathUtil
import com.intellij.util.io.exists
import com.intellij.util.ui.*
import java.awt.Dimension
import java.awt.Insets
import java.nio.file.Path
import java.util.function.Supplier
import javax.swing.*
import org.jetbrains.annotations.SystemIndependent

object Icons {
  val gearWithDropdownIcon = LayeredIcon(AllIcons.General.GearPlain, AllIcons.General.Dropdown)
  val gearWithDropdownDisabledIcon =
      LayeredIcon(
          IconLoader.getDisabledIcon(AllIcons.General.GearPlain),
          IconLoader.getDisabledIcon(AllIcons.General.Dropdown))

  val gearWithDropdownErrorIcon = LayeredIcon(AllIcons.General.Error, AllIcons.General.Dropdown)
}

fun SwiftFormatConfigurable.createStoreAsProjectFileCheckBox(): Row.() -> Cell<JBCheckBox> {
  return {
    val storeAsFileGearButton = createStoreAsFileGearButton()
    val storeAsProjectFileCheckBox =
        checkBox("Store as project file").gap(RightGap.SMALL).applyToComponent {
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
    cell(storeAsFileGearButton).enabledIf(storeAsProjectFileCheckBox.selected)

    storeAsProjectFileCheckBox
  }
}

fun SwiftFormatConfigurable.createStoreAsFileGearButton(): ActionButton {
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

fun SwiftFormatConfigurable.popup(
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

fun SwiftFormatConfigurable.manageStorageFileLocation(state: PopupState<Balloon>? = null) {
  val balloonDisposable = Disposer.newDisposable()
  val pathToErrorMessage = { path: String? ->
    getErrorIfBadFolderPathForStoringInArbitraryFile(project, path)
  }

  var balloon: Balloon? = null
  val popup = popup(pathToErrorMessage, balloonDisposable) { balloon?.hide() }
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

fun Row.configPathTextField(
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
