// Intellij bug: https://github.com/Kotlin/kotlinx.serialization/issues/993
@file:Suppress("PROVIDED_RUNTIME_TOO_LOW")

package org.swiftformat.plugin

import kotlinx.serialization.*
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
data class Configuration(
    var fileScopedDeclarationPrivacy: FileScopedDeclarationPrivacy =
        FileScopedDeclarationPrivacy(FileScopedDeclarationPrivacy.AccessLevel.private),
    var indentation: Indentation = Spaces(2),
    var indentConditionalCompilationBlocks: Boolean = true,
    var indentSwitchCaseLabels: Boolean = false,
    var lineBreakAroundMultilineExpressionChainComponents: Boolean = false,
    var lineBreakBeforeControlFlowKeywords: Boolean = false,
    var lineBreakBeforeEachArgument: Boolean = false,
    var lineBreakBeforeEachGenericRequirement: Boolean = false,
    var lineLength: Int = 100,
    var maximumBlankLines: Int = 1,
    var prioritizeKeepingFunctionOutputTogether: Boolean = false,
    var respectsExistingLineBreaks: Boolean = true,
    var rules: Map<String, Boolean> = RuleRegistry.rules,
    var tabWidth: Int = 8,
    var version: Int = 1
)

@Serializable
data class FileScopedDeclarationPrivacy(var accessLevel: AccessLevel) {
  @Suppress("Unused", "EnumEntryName")
  @Serializable
  enum class AccessLevel {
    private,
    fileprivate
  }
}

@Serializable(with = IdentationSerializer::class)
sealed class Indentation {
  abstract var count: Int
}

@Serializable data class Spaces(@SerialName("spaces") override var count: Int) : Indentation()

@Serializable data class Tabs(@SerialName("tabs") override var count: Int) : Indentation()

object IdentationSerializer : JsonContentPolymorphicSerializer<Indentation>(Indentation::class) {
  override fun selectDeserializer(element: JsonElement) =
      when {
        "tabs" in element.jsonObject -> Tabs.serializer()
        else -> Spaces.serializer()
      }
}

@Serializable
class RuleRegistry {
  companion object {
    val rules: MutableMap<String, Boolean> =
        mutableMapOf(
            "AllPublicDeclarationsHaveDocumentation" to false,
            "AlwaysUseLowerCamelCase" to true,
            "AmbiguousTrailingClosureOverload" to true,
            "BeginDocumentationCommentWithOneLineSummary" to false,
            "DoNotUseSemicolons" to true,
            "DontRepeatTypeInStaticProperties" to true,
            "FileScopedDeclarationPrivacy" to true,
            "FullyIndirectEnum" to true,
            "GroupNumericLiterals" to true,
            "IdentifiersMustBeASCII" to true,
            "NeverForceUnwrap" to false,
            "NeverUseForceTry" to false,
            "NeverUseImplicitlyUnwrappedOptionals" to false,
            "NoAccessLevelOnExtensionDeclaration" to true,
            "NoBlockComments" to true,
            "NoCasesWithOnlyFallthrough" to true,
            "NoEmptyTrailingClosureParentheses" to true,
            "NoLabelsInCasePatterns" to true,
            "NoLeadingUnderscores" to false,
            "NoParensAroundConditions" to true,
            "NoVoidReturnOnFunctionSignature" to true,
            "OneCasePerLine" to true,
            "OneVariableDeclarationPerLine" to true,
            "OnlyOneTrailingClosureArgument" to true,
            "OrderedImports" to true,
            "ReturnVoidInsteadOfEmptyTuple" to true,
            "UseEarlyExits" to false,
            "UseLetInEveryBoundCaseVariable" to true,
            "UseShorthandTypeNames" to true,
            "UseSingleLinePropertyGetter" to true,
            "UseSynthesizedInitializer" to true,
            "UseTripleSlashForDocumentationComments" to true,
            "UseWhereClausesInForLoops" to false,
            "ValidateDocumentationComments" to false,
        )
  }
}
