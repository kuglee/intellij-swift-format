package org.swiftformat.plugin

import com.google.common.collect.ImmutableMap
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.swiftformat.plugin.utils.SwiftFormatCLI

internal object FormatterUtil {
  fun getReplacements(
      formatter: SwiftFormatCLI,
      text: String,
      project: Project
  ): Map<TextRange, String> {
    return when (val result = formatter.formatText(project, text)) {
      is SwiftFormatCLI.SwiftFormatResult.Success -> {
        ImmutableMap.of(TextRange.allOf(text), result.msg)
      }
      is SwiftFormatCLI.SwiftFormatResult.FailedToStart -> {
        val configureAction =
            "Configure" to
                {
                  ShowSettingsUtil.getInstance()
                      .showSettingsDialog(project, SwiftFormatConfigurable::class.java)
                }
        project.showBalloon(result.msg, NotificationType.ERROR, configureAction)
        ImmutableMap.of()
      }
      // should be a syntax error. Can't really do anything meaningful, if it isn't.
      is SwiftFormatCLI.SwiftFormatResult.UnknownFailure -> {
        ImmutableMap.of()
      }
    }
  }
}
