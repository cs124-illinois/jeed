package edu.illinois.cs.cs125.jeed.core.parser

import io.kotest.core.spec.style.StringSpec
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

val filesToUse = listOf(
    "AnonymousInitializer.kt",
    "BabySteps.kt",
    "ByClauses.kt",
    "CallsInWhen.kt",
    "CallWithManyClosures.kt",
    "CollectionLiterals.kt",
    "CommentsBindingInLambda.kt",
    "CommentsBindingInStatementBlock.kt",
    "Constructors.kt",
    "destructuringInLambdas.kt",
    "DoubleColonWhitespaces.kt",
    "DynamicReceiver.kt",
    "DynamicTypes.kt",
    "EnumCommas.kt",
    "EnumEntrySemicolonInlineMember.kt",
    "EnumEntrySemicolonMember.kt",
    "EnumIn.kt",
    "EnumInline.kt",
    "Enums.kt",
    "EnumShortCommas.kt",
    "EnumShortWithOverload.kt",
    "EOLsInComments.kt",
    "EOLsOnRollback.kt",
    "ExtensionsWithQNReceiver.kt",
    "FloatingPointLiteral.kt",
    "FunctionCalls.kt",
    "FunctionLiterals.kt",
    "FunctionTypes.kt",
    "IfWithPropery.kt",
    "Inner.kt",
    "Interface.kt",
    "LocalDeclarations.kt",
    "ModifierAsSelector.kt",
    "NamedClassObject.kt",
    "NewLinesValidOperations.kt",
    "NotIsAndNotIn.kt",
    "ObjectLiteralAsStatement.kt",
    "PropertyInvokes.kt",
    "QuotedIdentifiers.kt",
    "SemicolonAfterIf.kt",
    "SimpleClassMembers.kt",
    "SimpleExpressions.kt",
    "SimpleModifiers.kt",
    "SoftKeywords.kt",
    "SoftKeywordsInTypeArguments.kt",
    "TraitConstructor.kt",
    "TypeAlias.kt",
    "TypeConstraints.kt",
    "TypeModifiers.kt",
)

fun generateTemplate(file: String) = """"it should parse $file" {
    val contents = object {}::class.java.getResource("/parser/$file")?.readText()
        ?: error("Couldn't load $file")
    Source.fromKotlin(contents).parse()
}"""

class GenerateKotlinTests :
    StringSpec({
        "!it should generate Kotlin parser tests" {
            val parserDirectory =
                object {}::class.java.getResource("/parser")?.path ?: error("Can't load parser example directory")
            val parserFiles = Files
                .walk(Paths.get(parserDirectory))
                .filter { Files.isRegularFile(it) && it.name.endsWith(".kt") }
                .map { it.relativeTo(Path(parserDirectory)) }
                .map { it.pathString }
                .toList()

            val tests = filesToUse.map { parserFile ->
                check(parserFiles.contains(parserFile)) {
                    "Can't find $parserFile"
                }
                generateTemplate(parserFile)
            }.joinToString("\n")

            println(tests)
        }
    })
