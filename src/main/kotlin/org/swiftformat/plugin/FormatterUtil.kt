package org.swiftformat.plugin

import com.intellij.openapi.editor.Document
import org.swiftformat.plugin.utils.SwiftFormatCLI

internal object FormatterUtil {
  fun getReplacements(formatter: SwiftFormatCLI, document: Document): String? {
    val result = formatter.formatDocumentAndSetText(document)

    return (result as? SwiftFormatCLI.SwiftFormatResult.Success)?.msg
  }
}
