/*
 * Copyright 2022 Gábor Librecz. All Rights Reserved.
 * Copyright 2015 Google Inc. All Rights Reserved.
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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.ChangedRangesInfo
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.CheckUtil
import com.intellij.util.IncorrectOperationException
import java.nio.file.Path
import java.util.*
import org.swiftformat.plugin.utils.SwiftFormatCLI

/**
 * A [CodeStyleManager] implementation which formats .swift files with swift-format. Formatting of
 * all other types of files is delegated to IntelliJ's default implementation.
 */
internal class SwiftFormatCodeStyleManager(original: CodeStyleManager) :
    CodeStyleManagerDecorator(original) {
  @Throws(IncorrectOperationException::class)
  override fun reformatText(file: PsiFile, startOffset: Int, endOffset: Int) {
    if (overrideFormatterForFile(file)) {
      formatInternal(file)
    } else {
      super.reformatText(file, startOffset, endOffset)
    }
  }

  @Throws(IncorrectOperationException::class)
  override fun reformatText(file: PsiFile, ranges: Collection<TextRange>) {
    if (overrideFormatterForFile(file)) {
      formatInternal(file)
    } else {
      super.reformatText(file, ranges)
    }
  }

  @Throws(IncorrectOperationException::class)
  override fun reformatTextWithContext(psiFile: PsiFile, changedRangesInfo: ChangedRangesInfo) {
    val ranges: MutableList<TextRange> = ArrayList()
    if (changedRangesInfo.insertedRanges != null) {
      ranges.addAll(changedRangesInfo.insertedRanges)
    }
    ranges.addAll(changedRangesInfo.allChangedRanges)
    reformatTextWithContext(psiFile, ranges)
  }

  @Throws(IncorrectOperationException::class)
  override fun reformatTextWithContext(file: PsiFile, ranges: Collection<TextRange>) {
    if (overrideFormatterForFile(file)) {
      formatInternal(file)
    } else {
      super.reformatTextWithContext(file, ranges)
    }
  }

  @Throws(IncorrectOperationException::class)
  override fun reformatRange(
      element: PsiElement,
      startOffset: Int,
      endOffset: Int,
      canChangeWhiteSpacesOnly: Boolean
  ): PsiElement {
    // Only handle elements that are PsiFile for now -- otherwise we need to search for some
    // element within the file at new locations given the original startOffset and endOffsets
    // to serve as the return value.
    val file = if (element is PsiFile) element else null
    return if (file != null && canChangeWhiteSpacesOnly && overrideFormatterForFile(file)) {
      formatInternal(file)
      file
    } else {
      super.reformatRange(element, startOffset, endOffset, canChangeWhiteSpacesOnly)
    }
  }

  /** Return whether this formatter can handle formatting the given file. */
  private fun overrideFormatterForFile(file: PsiFile): Boolean {
    return ((file.fileType.name == "Swift" || file.virtualFile.extension == "swift") &&
        SwiftFormatSettings.getInstance(project).isEnabled)
  }

  private fun formatInternal(file: PsiFile) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    val documentManager = PsiDocumentManager.getInstance(project)
    documentManager.commitAllDocuments()
    CheckUtil.checkWritable(file)
    val document = documentManager.getDocument(file) ?: return
    // If there are postponed PSI changes (e.g., during a refactoring), just abort.
    // If we apply them now, then the incoming text ranges may no longer be valid.
    if (documentManager.isDocumentBlockedByPsi(document)) {
      return
    }
    format(document)
  }

  /**
   * Format the ranges of the given document.
   *
   * Overriding methods will need to modify the document with the result of the external formatter
   * (usually using [.performReplacements]).
   */
  private fun format(document: Document) {
    val settings = SwiftFormatSettings.getInstance(project)
    val formatter = SwiftFormatCLI(Path.of(settings.swiftFormatPath))

    performReplacements(document, FormatterUtil.getReplacements(formatter, document.text, project))
  }

  private fun performReplacements(document: Document, replacements: Map<TextRange, String>) {
    if (replacements.isEmpty()) {
      return
    }

    val sorted =
        TreeMap<TextRange, String>(Comparator.comparing { obj: TextRange -> obj.startOffset })
    sorted.putAll(replacements)
    WriteCommandAction.runWriteCommandAction(project) {
      for ((key, value) in sorted.descendingMap()) {
        document.replaceString(key.startOffset, key.endOffset, value)
      }
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }
  }
}
