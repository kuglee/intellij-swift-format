package org.swiftformat.plugin

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import org.swiftformat.plugin.utils.SwiftFormatCLI

internal object FormatterUtil {
  fun getReplacements(formatter: SwiftFormatCLI, document: Document, project: Project): String? {
    val result = formatter.formatDocumentAndSetText(project, document)

    return (result as? SwiftFormatCLI.SwiftFormatResult.Success)?.msg
  }
}
