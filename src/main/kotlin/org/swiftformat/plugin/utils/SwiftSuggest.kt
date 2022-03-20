/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 *
 * Originally from intellij-elm
 */

package org.swiftformat.plugin.utils

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.isDirectory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Provides suggestions about where `swift-format` may be installed */
object SwiftSuggest {

  /**
   * Suggest path to `swift-format`. This performs file I/O in order to determine that the file
   * exists and that it is executable.
   */
  fun suggestTools(programName: String): Path? = programPath(programName)

  /** Attempt to find the path to [programName]. */
  private fun programPath(programName: String): Path? =
      binDirSuggestions().map { binDir -> binDir.resolve(programName) }.firstOrNull {
        Files.isExecutable(it)
      }

  /** Return a list of directories which may contain the `swift-format` binary. */
  private fun binDirSuggestions() =
      sequenceOf(
              suggestionsFromPath(),
              suggestionsForMac(),
          )
          .flatten()

  private fun suggestionsFromPath(): Sequence<Path> {
    return System.getenv("PATH")
        .orEmpty()
        .splitToSequence(File.pathSeparator)
        .filter { it.isNotEmpty() }
        .map { Paths.get(it.trim()) }
        .filter { it.isDirectory() }
  }

  private fun suggestionsForMac(): Sequence<Path> {
    if (!SystemInfo.isMac) return emptySequence()
    return sequenceOf(Paths.get("/usr/local/bin"), Paths.get("/opt/homebrew/bin"))
  }
}
