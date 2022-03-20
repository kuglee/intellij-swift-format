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

import com.intellij.formatting.FormattingMode
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.*
import com.intellij.util.IncorrectOperationException
import com.intellij.util.ThrowableRunnable
import java.lang.Runnable
import kotlin.Throws

/**
 * Decorates the [CodeStyleManager] abstract class by delegating to a concrete implementation
 * instance (likely IntelliJ's default instance).
 */
internal open class CodeStyleManagerDecorator(val delegate: CodeStyleManager) :
    CodeStyleManager(), FormattingModeAwareIndentAdjuster {
  override fun getProject(): Project {
    return delegate.project
  }

  @Throws(IncorrectOperationException::class)
  override fun reformat(element: PsiElement): PsiElement {
    return delegate.reformat(element)
  }

  @Throws(IncorrectOperationException::class)
  override fun reformat(element: PsiElement, canChangeWhiteSpacesOnly: Boolean): PsiElement {
    return delegate.reformat(element, canChangeWhiteSpacesOnly)
  }

  @Throws(IncorrectOperationException::class)
  override fun reformatRange(element: PsiElement, startOffset: Int, endOffset: Int): PsiElement {
    return delegate.reformatRange(element, startOffset, endOffset)
  }

  @Throws(IncorrectOperationException::class)
  override fun reformatRange(
      element: PsiElement,
      startOffset: Int,
      endOffset: Int,
      canChangeWhiteSpacesOnly: Boolean
  ): PsiElement {
    return delegate.reformatRange(element, startOffset, endOffset, canChangeWhiteSpacesOnly)
  }

  @Throws(IncorrectOperationException::class)
  override fun reformatText(file: PsiFile, startOffset: Int, endOffset: Int) {
    delegate.reformatText(file, startOffset, endOffset)
  }

  @Throws(IncorrectOperationException::class)
  override fun reformatText(file: PsiFile, ranges: Collection<TextRange>) {
    delegate.reformatText(file, ranges)
  }

  @Throws(IncorrectOperationException::class)
  override fun reformatTextWithContext(psiFile: PsiFile, changedRangesInfo: ChangedRangesInfo) {
    delegate.reformatTextWithContext(psiFile, changedRangesInfo)
  }

  @Throws(IncorrectOperationException::class)
  override fun reformatTextWithContext(file: PsiFile, ranges: Collection<TextRange>) {
    delegate.reformatTextWithContext(file, ranges)
  }

  @Throws(IncorrectOperationException::class)
  override fun adjustLineIndent(file: PsiFile, rangeToAdjust: TextRange) {
    delegate.adjustLineIndent(file, rangeToAdjust)
  }

  @Throws(IncorrectOperationException::class)
  override fun adjustLineIndent(file: PsiFile, offset: Int): Int {
    return delegate.adjustLineIndent(file, offset)
  }

  override fun adjustLineIndent(document: Document, offset: Int): Int {
    return delegate.adjustLineIndent(document, offset)
  }

  override fun scheduleIndentAdjustment(document: Document, offset: Int) {
    delegate.scheduleIndentAdjustment(document, offset)
  }

  override fun isLineToBeIndented(file: PsiFile, offset: Int): Boolean {
    return delegate.isLineToBeIndented(file, offset)
  }

  override fun getLineIndent(file: PsiFile, offset: Int): String? {
    return delegate.getLineIndent(file, offset)
  }

  override fun getLineIndent(file: PsiFile, offset: Int, mode: FormattingMode): String? {
    return delegate.getLineIndent(file, offset, mode)
  }

  override fun getLineIndent(document: Document, offset: Int): String? {
    return delegate.getLineIndent(document, offset)
  }

  override fun getIndent(text: String, fileType: FileType): Indent {
    return delegate.getIndent(text, fileType)
  }

  override fun fillIndent(indent: Indent, fileType: FileType): String {
    return delegate.fillIndent(indent, fileType)
  }

  override fun zeroIndent(): Indent {
    return delegate.zeroIndent()
  }

  @Throws(IncorrectOperationException::class)
  override fun reformatNewlyAddedElement(block: ASTNode, addedElement: ASTNode) {
    delegate.reformatNewlyAddedElement(block, addedElement)
  }

  override fun isSequentialProcessingAllowed(): Boolean {
    return delegate.isSequentialProcessingAllowed
  }

  override fun performActionWithFormatterDisabled(r: Runnable) {
    delegate.performActionWithFormatterDisabled(r)
  }

  override fun <T : Throwable?> performActionWithFormatterDisabled(r: ThrowableRunnable<T>) {
    delegate.performActionWithFormatterDisabled(r)
  }

  override fun <T> performActionWithFormatterDisabled(r: Computable<T>): T {
    return delegate.performActionWithFormatterDisabled(r)
  }

  override fun getSpacing(file: PsiFile, offset: Int): Int {
    return delegate.getSpacing(file, offset)
  }

  override fun getMinLineFeeds(file: PsiFile, offset: Int): Int {
    return delegate.getMinLineFeeds(file, offset)
  }

  override fun runWithDocCommentFormattingDisabled(file: PsiFile, runnable: Runnable) {
    delegate.runWithDocCommentFormattingDisabled(file, runnable)
  }

  override fun getDocCommentSettings(file: PsiFile): DocCommentSettings {
    return delegate.getDocCommentSettings(file)
  }

  // From FormattingModeAwareIndentAdjuster
  /** Uses same fallback as [CodeStyleManager.getCurrentFormattingMode]. */
  override fun getCurrentFormattingMode(): FormattingMode {
    return if (delegate is FormattingModeAwareIndentAdjuster) {
      (delegate as FormattingModeAwareIndentAdjuster).currentFormattingMode
    } else FormattingMode.REFORMAT
  }

  override fun adjustLineIndent(document: Document, offset: Int, mode: FormattingMode): Int {
    return if (delegate is FormattingModeAwareIndentAdjuster) {
      (delegate as FormattingModeAwareIndentAdjuster).adjustLineIndent(document, offset, mode)
    } else offset
  }

  override fun scheduleReformatWhenSettingsComputed(file: PsiFile) {
    delegate.scheduleReformatWhenSettingsComputed(file)
  }
}
