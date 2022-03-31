package org.swiftformat.plugin

import com.google.common.collect.ImmutableMap
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.swiftformat.plugin.utils.SwiftFormatCLI

internal object FormatterUtil {
  fun getReplacements(
      formatter: SwiftFormatCLI,
      text: String,
      project: Project
  ): Map<TextRange, String> {
    val result = formatter.formatText(project, text)

    if (result is SwiftFormatCLI.SwiftFormatResult.Success) {
      return ImmutableMap.of(TextRange.allOf(text), result.msg)
    }

    return ImmutableMap.of()
  }
}
