/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 *
 * Originally from intellij-elm
 */

package org.swiftformat.plugin.utils

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressManager
import java.nio.file.Path
import org.swiftformat.plugin.utils.openapiext.GeneralCommandLine
import org.swiftformat.plugin.utils.openapiext.execute
import org.swiftformat.plugin.utils.openapiext.isNotSuccess

/** Interact with external `swift-format` process. */
class SwiftFormatCLI(private val swiftFormatExecutablePath: Path) {

  private fun getFormattedContentOfDocument(document: Document): ProcessOutput {
    val arguments = listOf("format")

    return GeneralCommandLine(swiftFormatExecutablePath)
        .withParameters(arguments)
        .execute(document.text)
  }

  sealed class SwiftFormatResult(val msg: String, val cause: Throwable? = null) {
    class Success(formattedText: String) : SwiftFormatResult(formattedText)
    class BadSyntax(msg: String? = null, cause: Throwable? = null) :
        SwiftFormatResult(
            msg ?: "swift-format encountered syntax errors that it could not fix", cause)
    class FailedToStart : SwiftFormatResult("Failed to launch swift-format. Is the path correct?")
    class UnknownFailure(msg: String? = null, cause: Throwable?) :
        SwiftFormatResult(msg ?: "Something went wrong running swift-format", cause)
  }

  fun formatDocumentAndSetText(document: Document): SwiftFormatResult {
    val processOutput =
        try {
          ProgressManager.getInstance()
              .runProcessWithProgressSynchronously<ProcessOutput, ExecutionException>(
                  { getFormattedContentOfDocument(document) },
                  "Running swift-format on current file...",
                  true,
                  null)
        } catch (e: ExecutionException) {
          val msg = e.message ?: "unknown"
          return when {
            msg.contains("Fatal error: ", ignoreCase = true) ->
                SwiftFormatResult.BadSyntax(cause = e)
            e is ProcessNotCreatedException -> SwiftFormatResult.FailedToStart()
            else -> SwiftFormatResult.UnknownFailure(cause = e)
          }
        }

    if (processOutput.isNotSuccess)
        return SwiftFormatResult.UnknownFailure(
            "Process output exit code was non-zero", cause = null)

    val formatted = processOutput.stdout

    return SwiftFormatResult.Success(formatted)
  }
}
