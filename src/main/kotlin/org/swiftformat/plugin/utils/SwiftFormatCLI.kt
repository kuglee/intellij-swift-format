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
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import java.nio.file.Path
import org.swiftformat.plugin.SwiftFormatSettings
import org.swiftformat.plugin.swiftFormatTool
import org.swiftformat.plugin.utils.openapiext.GeneralCommandLine
import org.swiftformat.plugin.utils.openapiext.execute
import org.swiftformat.plugin.utils.openapiext.isNotSuccess

/** Interact with external `swift-format` process. */
class SwiftFormatCLI(private val swiftFormatExecutablePath: Path) {
  private fun getFormattedText(text: String, project: Project): ProcessOutput {
    val arguments =
        mutableListOf(
            "format",
            "--parallel",
            "--ignore-unparsable-files",
        )

    val settings = SwiftFormatSettings.getInstance(project)

    if (settings.useCustomConfiguration) {
      val configFilePathString = settings.getSwiftFormatConfigFilePath(project)
      if (configFilePathString != null) {
        val configFilePath = Path.of(settings.getSwiftFormatConfigFilePath(project))
        if (configFilePath.exists()) {
          arguments.addAll(listOf("--configuration", configFilePath.toString()))
        }
      }
    }

    return GeneralCommandLine(swiftFormatExecutablePath)
        .withParameters(arguments)
        .execute(swiftFormatTool, project, stdIn = text)
  }

  sealed class SwiftFormatResult(val msg: String, val cause: Throwable? = null) {
    class Success(formattedText: String) : SwiftFormatResult(formattedText)
    class FailedToStart : SwiftFormatResult("Failed to launch swift-format.")
    class UnknownFailure(msg: String? = null, cause: Throwable?) :
        SwiftFormatResult(msg ?: "Something went wrong running swift-format", cause)
  }

  fun formatText(project: Project, text: String): SwiftFormatResult {
    val processOutput =
        try {
          ProgressManager.getInstance()
              .runProcessWithProgressSynchronously<ProcessOutput, ExecutionException>(
                  { getFormattedText(text, project) },
                  "Running swift-format on current file...",
                  true,
                  null)
        } catch (e: ExecutionException) {
          return when (e) {
            is ProcessNotCreatedException -> {
              SwiftFormatResult.FailedToStart()
            }
            else -> SwiftFormatResult.UnknownFailure(cause = e)
          }
        }

    if (processOutput.isNotSuccess)
        return SwiftFormatResult.UnknownFailure(
            "Process output exit code was non-zero", cause = null)

    val formattedText = processOutput.stdout

    return SwiftFormatResult.Success(formattedText)
  }
}
