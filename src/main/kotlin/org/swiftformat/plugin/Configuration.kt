// Intellij bug: https://github.com/Kotlin/kotlinx.serialization/issues/993
@file:Suppress("PROVIDED_RUNTIME_TOO_LOW")

package org.swiftformat.plugin

import kotlinx.serialization.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
data class Configuration(
    var fileScopedDeclarationPrivacy: FileScopedDeclarationPrivacy? = null,
    var indentation: Indentation? = null,
    var indentConditionalCompilationBlocks: Boolean? = null,
    var indentSwitchCaseLabels: Boolean? = null,
    var lineBreakAroundMultilineExpressionChainComponents: Boolean? = null,
    var lineBreakBeforeControlFlowKeywords: Boolean? = null,
    var lineBreakBeforeEachArgument: Boolean? = null,
    var lineBreakBeforeEachGenericRequirement: Boolean? = null,
    var lineLength: Int? = null,
    var maximumBlankLines: Int? = null,
    var prioritizeKeepingFunctionOutputTogether: Boolean? = null,
    var respectsExistingLineBreaks: Boolean? = null,
    @Serializable(with = MutableMapWithNullableBooleanValueSerializer::class)
    var rules: MutableMap<String, Boolean?>? = null,
    var tabWidth: Int? = null,
    var version: Int? = null,
) {
  fun deepCopy(): Configuration {
    return copy(indentation = indentation?.copy(), rules = rules?.toMutableMap())
  }

  fun isDefault(): Boolean {
    fun isRuleDefault(): Boolean {
      var returnValue = true
      if (rules != null) {
        for ((key, value) in rules!!.filterKeys { it in RuleRegistry.formatterRulesKeys }) {
          val defaultValue = RuleRegistry.defaultRules[key] ?: return false
          returnValue = returnValue && (value == defaultValue)
        }
      }

      return returnValue
    }

    return (fileScopedDeclarationPrivacy == null ||
        fileScopedDeclarationPrivacy == defaultConfiguration.fileScopedDeclarationPrivacy) &&
        (indentation == null || indentation == defaultConfiguration.indentation) &&
        (indentConditionalCompilationBlocks == null ||
            indentConditionalCompilationBlocks ==
                defaultConfiguration.indentConditionalCompilationBlocks) &&
        (indentSwitchCaseLabels == null ||
            indentSwitchCaseLabels == defaultConfiguration.indentSwitchCaseLabels) &&
        (lineBreakAroundMultilineExpressionChainComponents == null ||
            lineBreakAroundMultilineExpressionChainComponents ==
                defaultConfiguration.lineBreakAroundMultilineExpressionChainComponents) &&
        (lineBreakBeforeControlFlowKeywords == null ||
            lineBreakBeforeControlFlowKeywords ==
                defaultConfiguration.lineBreakBeforeControlFlowKeywords) &&
        (lineBreakBeforeEachArgument == null ||
            lineBreakBeforeEachArgument == defaultConfiguration.lineBreakBeforeEachArgument) &&
        (lineBreakBeforeEachGenericRequirement == null ||
            lineBreakBeforeEachGenericRequirement ==
                defaultConfiguration.lineBreakBeforeEachGenericRequirement) &&
        (lineLength == null || lineLength == defaultConfiguration.lineLength) &&
        (maximumBlankLines == null ||
            maximumBlankLines == defaultConfiguration.maximumBlankLines) &&
        (prioritizeKeepingFunctionOutputTogether == null ||
            prioritizeKeepingFunctionOutputTogether ==
                defaultConfiguration.prioritizeKeepingFunctionOutputTogether) &&
        (respectsExistingLineBreaks == null ||
            respectsExistingLineBreaks == defaultConfiguration.respectsExistingLineBreaks) &&
        (tabWidth == null || tabWidth == defaultConfiguration.tabWidth) &&
        isRuleDefault()
  }

  fun toJson(): String {
    val prettyJson = Json { prettyPrint = true }
    val a = prettyJson.encodeToString(this)
    return a
  }

  companion object {
    fun fromJson(json: String): Configuration? {
      return try {
        Json.decodeFromString(json)
      } catch (e: Exception) {
        null
      }
    }
  }
}

val Configuration.Companion.defaultConfiguration
  get() = org.swiftformat.plugin.defaultConfiguration

private val defaultConfiguration =
    Configuration(
        fileScopedDeclarationPrivacy =
            FileScopedDeclarationPrivacy(FileScopedDeclarationPrivacy.AccessLevel.private),
        indentation = Indentation.Spaces(2),
        indentConditionalCompilationBlocks = true,
        indentSwitchCaseLabels = false,
        lineBreakAroundMultilineExpressionChainComponents = false,
        lineBreakBeforeControlFlowKeywords = false,
        lineBreakBeforeEachArgument = false,
        lineBreakBeforeEachGenericRequirement = false,
        lineLength = 100,
        maximumBlankLines = 1,
        prioritizeKeepingFunctionOutputTogether = false,
        respectsExistingLineBreaks = true,
        rules = RuleRegistry.defaultRules,
        tabWidth = 8,
        version = 1)

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
sealed class Indentation(open var count: Int) {
  @Suppress("Unused") private constructor() : this(-1)

  @Serializable
  data class Spaces(@SerialName("spaces") override var count: Int) : Indentation(count)
  @Serializable data class Tabs(@SerialName("tabs") override var count: Int) : Indentation(count)
}

fun Indentation.copy(count: Int = this.count): Indentation =
    when (this) {
      is Indentation.Spaces -> this.copy(count)
      is Indentation.Tabs -> this.copy(count)
    }

object IdentationSerializer : JsonContentPolymorphicSerializer<Indentation>(Indentation::class) {
  override fun selectDeserializer(element: JsonElement) =
      when {
        "tabs" in element.jsonObject -> Indentation.Tabs.serializer()
        else -> Indentation.Spaces.serializer()
      }
}

object MutableMapWithNullableBooleanValueSerializer : KSerializer<MutableMap<String, Boolean?>> {
  private val mapSerializer = MapSerializer(String.serializer(), Boolean.serializer().nullable)

  override val descriptor: SerialDescriptor = mapSerializer.descriptor

  override fun serialize(encoder: Encoder, value: MutableMap<String, Boolean?>) {
    mapSerializer.serialize(encoder, (value.filterValues { it != null }))
  }

  override fun deserialize(decoder: Decoder): MutableMap<String, Boolean?> {
    return mapSerializer.deserialize(decoder).toMutableMap()
  }
}

@Serializable
object RuleRegistry {
  val rules: MutableMap<String, Boolean?> = createRulesMap()
}

@Suppress("unused")
val RuleRegistry.formatterRulesKeys: List<String>
  get() = org.swiftformat.plugin.formatterRulesKeys

@Suppress("unused")
val RuleRegistry.defaultRules: MutableMap<String, Boolean?>
  get() = org.swiftformat.plugin.defaultRules

private fun createRulesMap(
    allPublicDeclarationsHaveDocumentation: Boolean? = null,
    alwaysUseLowerCamelCase: Boolean? = null,
    ambiguousTrailingClosureOverload: Boolean? = null,
    beginDocumentationCommentWithOneLineSummary: Boolean? = null,
    doNotUseSemicolons: Boolean? = null,
    dontRepeatTypeInStaticProperties: Boolean? = null,
    fileScopedDeclarationPrivacy: Boolean? = null,
    fullyIndirectEnum: Boolean? = null,
    groupNumericLiterals: Boolean? = null,
    identifiersMustBeASCII: Boolean? = null,
    neverForceUnwrap: Boolean? = null,
    neverUseForceTry: Boolean? = null,
    neverUseImplicitlyUnwrappedOptionals: Boolean? = null,
    noAccessLevelOnExtensionDeclaration: Boolean? = null,
    noBlockComments: Boolean? = null,
    noCasesWithOnlyFallthrough: Boolean? = null,
    noEmptyTrailingClosureParentheses: Boolean? = null,
    noLabelsInCasePatterns: Boolean? = null,
    noLeadingUnderscores: Boolean? = null,
    noParensAroundConditions: Boolean? = null,
    noVoidReturnOnFunctionSignature: Boolean? = null,
    oneCasePerLine: Boolean? = null,
    oneVariableDeclarationPerLine: Boolean? = null,
    onlyOneTrailingClosureArgument: Boolean? = null,
    orderedImports: Boolean? = null,
    returnVoidInsteadOfEmptyTuple: Boolean? = null,
    useEarlyExits: Boolean? = null,
    useLetInEveryBoundCaseVariable: Boolean? = null,
    useShorthandTypeNames: Boolean? = null,
    useSingleLinePropertyGetter: Boolean? = null,
    useSynthesizedInitializer: Boolean? = null,
    useTripleSlashForDocumentationComments: Boolean? = null,
    useWhereClausesInForLoops: Boolean? = null,
    validateDocumentationComments: Boolean? = null,
): MutableMap<String, Boolean?> {
  return mutableMapOf(
      "AllPublicDeclarationsHaveDocumentation" to allPublicDeclarationsHaveDocumentation,
      "AlwaysUseLowerCamelCase" to alwaysUseLowerCamelCase,
      "AmbiguousTrailingClosureOverload" to ambiguousTrailingClosureOverload,
      "BeginDocumentationCommentWithOneLineSummary" to beginDocumentationCommentWithOneLineSummary,
      "DoNotUseSemicolons" to doNotUseSemicolons,
      "DontRepeatTypeInStaticProperties" to dontRepeatTypeInStaticProperties,
      "FileScopedDeclarationPrivacy" to fileScopedDeclarationPrivacy,
      "FullyIndirectEnum" to fullyIndirectEnum,
      "GroupNumericLiterals" to groupNumericLiterals,
      "IdentifiersMustBeASCII" to identifiersMustBeASCII,
      "NeverForceUnwrap" to neverForceUnwrap,
      "NeverUseForceTry" to neverUseForceTry,
      "NeverUseImplicitlyUnwrappedOptionals" to neverUseImplicitlyUnwrappedOptionals,
      "NoAccessLevelOnExtensionDeclaration" to noAccessLevelOnExtensionDeclaration,
      "NoBlockComments" to noBlockComments,
      "NoCasesWithOnlyFallthrough" to noCasesWithOnlyFallthrough,
      "NoEmptyTrailingClosureParentheses" to noEmptyTrailingClosureParentheses,
      "NoLabelsInCasePatterns" to noLabelsInCasePatterns,
      "NoLeadingUnderscores" to noLeadingUnderscores,
      "NoParensAroundConditions" to noParensAroundConditions,
      "NoVoidReturnOnFunctionSignature" to noVoidReturnOnFunctionSignature,
      "OneCasePerLine" to oneCasePerLine,
      "OneVariableDeclarationPerLine" to oneVariableDeclarationPerLine,
      "OnlyOneTrailingClosureArgument" to onlyOneTrailingClosureArgument,
      "OrderedImports" to orderedImports,
      "ReturnVoidInsteadOfEmptyTuple" to returnVoidInsteadOfEmptyTuple,
      "UseEarlyExits" to useEarlyExits,
      "UseLetInEveryBoundCaseVariable" to useLetInEveryBoundCaseVariable,
      "UseShorthandTypeNames" to useShorthandTypeNames,
      "UseSingleLinePropertyGetter" to useSingleLinePropertyGetter,
      "UseSynthesizedInitializer" to useSynthesizedInitializer,
      "UseTripleSlashForDocumentationComments" to useTripleSlashForDocumentationComments,
      "UseWhereClausesInForLoops" to useWhereClausesInForLoops,
      "ValidateDocumentationComments" to validateDocumentationComments,
  )
}

private val formatterRulesKeys =
    listOf(
        "DoNotUseSemicolons",
        "FileScopedDeclarationPrivacy",
        "FullyIndirectEnum",
        "GroupNumericLiterals",
        "NoAccessLevelOnExtensionDeclaration",
        "NoCasesWithOnlyFallthrough",
        "NoEmptyTrailingClosureParentheses",
        "NoLabelsInCasePatterns",
        "NoParensAroundConditions",
        "NoVoidReturnOnFunctionSignature",
        "OneCasePerLine",
        "OneVariableDeclarationPerLine",
        "OrderedImports",
        "ReturnVoidInsteadOfEmptyTuple",
        "UseEarlyExits",
        "UseShorthandTypeNames",
        "UseSingleLinePropertyGetter",
        "UseTripleSlashForDocumentationComments",
        "UseWhereClausesInForLoops",
    )

private val defaultRules =
    createRulesMap(
        allPublicDeclarationsHaveDocumentation = false,
        alwaysUseLowerCamelCase = true,
        ambiguousTrailingClosureOverload = true,
        beginDocumentationCommentWithOneLineSummary = false,
        doNotUseSemicolons = true,
        dontRepeatTypeInStaticProperties = true,
        fileScopedDeclarationPrivacy = true,
        fullyIndirectEnum = true,
        groupNumericLiterals = true,
        identifiersMustBeASCII = true,
        neverForceUnwrap = false,
        neverUseForceTry = false,
        neverUseImplicitlyUnwrappedOptionals = false,
        noAccessLevelOnExtensionDeclaration = true,
        noBlockComments = true,
        noCasesWithOnlyFallthrough = true,
        noEmptyTrailingClosureParentheses = true,
        noLabelsInCasePatterns = true,
        noLeadingUnderscores = false,
        noParensAroundConditions = true,
        noVoidReturnOnFunctionSignature = true,
        oneCasePerLine = true,
        oneVariableDeclarationPerLine = true,
        onlyOneTrailingClosureArgument = true,
        orderedImports = true,
        returnVoidInsteadOfEmptyTuple = true,
        useEarlyExits = false,
        useLetInEveryBoundCaseVariable = true,
        useShorthandTypeNames = true,
        useSingleLinePropertyGetter = true,
        useSynthesizedInitializer = true,
        useTripleSlashForDocumentationComments = true,
        useWhereClausesInForLoops = false,
        validateDocumentationComments = true,
    )
