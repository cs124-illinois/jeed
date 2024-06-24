package edu.illinois.cs.cs125.jeed.core.parser

import edu.illinois.cs.cs125.jeed.core.Source
import io.kotest.core.spec.style.StringSpec

class KotlinGrammarTests :
    StringSpec({
        "it should parse AnonymousInitializer.kt" {
            val contents = object {}::class.java.getResource("/parser/AnonymousInitializer.kt")?.readText()
                ?: error("Couldn't load AnonymousInitializer.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse BabySteps.kt" {
            val contents = object {}::class.java.getResource("/parser/BabySteps.kt")?.readText()
                ?: error("Couldn't load BabySteps.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse ByClauses.kt" {
            val contents = object {}::class.java.getResource("/parser/ByClauses.kt")?.readText()
                ?: error("Couldn't load ByClauses.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse CallsInWhen.kt" {
            // FIXED: Removed invalid syntax
            val contents = object {}::class.java.getResource("/parser/CallsInWhen.kt")?.readText()
                ?: error("Couldn't load CallsInWhen.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse CallWithManyClosures.kt" {
            val contents = object {}::class.java.getResource("/parser/CallWithManyClosures.kt")?.readText()
                ?: error("Couldn't load CallWithManyClosures.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse CollectionLiterals.kt" {
            val contents = object {}::class.java.getResource("/parser/CollectionLiterals.kt")?.readText()
                ?: error("Couldn't load CollectionLiterals.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse CommentsBindingInLambda.kt" {
            val contents = object {}::class.java.getResource("/parser/CommentsBindingInLambda.kt")?.readText()
                ?: error("Couldn't load CommentsBindingInLambda.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse CommentsBindingInStatementBlock.kt" {
            val contents = object {}::class.java.getResource("/parser/CommentsBindingInStatementBlock.kt")?.readText()
                ?: error("Couldn't load CommentsBindingInStatementBlock.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse Constructors.kt" {
            // FIXED: added constructor keyword
            val contents = object {}::class.java.getResource("/parser/Constructors.kt")?.readText()
                ?: error("Couldn't load Constructors.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse destructuringInLambdas.kt" {
            val contents = object {}::class.java.getResource("/parser/destructuringInLambdas.kt")?.readText()
                ?: error("Couldn't load destructuringInLambdas.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse DoubleColonWhitespaces.kt" {
            val contents = object {}::class.java.getResource("/parser/DoubleColonWhitespaces.kt")?.readText()
                ?: error("Couldn't load DoubleColonWhitespaces.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse DynamicReceiver.kt" {
            // FIXED: Removed ?. for extension methods as invalid, empty top-level methods
            val contents = object {}::class.java.getResource("/parser/DynamicReceiver.kt")?.readText()
                ?: error("Couldn't load DynamicReceiver.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse DynamicTypes.kt" {
            // FIXED: Removed empty top-level method
            val contents = object {}::class.java.getResource("/parser/DynamicTypes.kt")?.readText()
                ?: error("Couldn't load DynamicTypes.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse EnumCommas.kt" {
            val contents = object {}::class.java.getResource("/parser/EnumCommas.kt")?.readText()
                ?: error("Couldn't load EnumCommas.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse EnumEntrySemicolonInlineMember.kt" {
            val contents = object {}::class.java.getResource("/parser/EnumEntrySemicolonInlineMember.kt")?.readText()
                ?: error("Couldn't load EnumEntrySemicolonInlineMember.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse EnumEntrySemicolonMember.kt" {
            val contents = object {}::class.java.getResource("/parser/EnumEntrySemicolonMember.kt")?.readText()
                ?: error("Couldn't load EnumEntrySemicolonMember.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse EnumIn.kt" {
            val contents = object {}::class.java.getResource("/parser/EnumIn.kt")?.readText()
                ?: error("Couldn't load EnumIn.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse EnumInline.kt" {
            val contents = object {}::class.java.getResource("/parser/EnumInline.kt")?.readText()
                ?: error("Couldn't load EnumInline.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse Enums.kt" {
            val contents = object {}::class.java.getResource("/parser/Enums.kt")?.readText()
                ?: error("Couldn't load Enums.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse EnumShortCommas.kt" {
            val contents = object {}::class.java.getResource("/parser/EnumShortCommas.kt")?.readText()
                ?: error("Couldn't load EnumShortCommas.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse EnumShortWithOverload.kt" {
            val contents = object {}::class.java.getResource("/parser/EnumShortWithOverload.kt")?.readText()
                ?: error("Couldn't load EnumShortWithOverload.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse EOLsInComments.kt" {
            val contents = object {}::class.java.getResource("/parser/EOLsInComments.kt")?.readText()
                ?: error("Couldn't load EOLsInComments.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse EOLsOnRollback.kt" {
            // FIXED: empty method
            val contents = object {}::class.java.getResource("/parser/EOLsOnRollback.kt")?.readText()
                ?: error("Couldn't load EOLsOnRollback.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse ExtensionsWithQNReceiver.kt" {
            val contents = object {}::class.java.getResource("/parser/ExtensionsWithQNReceiver.kt")?.readText()
                ?: error("Couldn't load ExtensionsWithQNReceiver.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse FloatingPointLiteral.kt" {
            val contents = object {}::class.java.getResource("/parser/FloatingPointLiteral.kt")?.readText()
                ?: error("Couldn't load FloatingPointLiteral.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse FunctionCalls.kt" {
            val contents = object {}::class.java.getResource("/parser/FunctionCalls.kt")?.readText()
                ?: error("Couldn't load FunctionCalls.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse FunctionLiterals.kt" {
            val contents = object {}::class.java.getResource("/parser/FunctionLiterals.kt")?.readText()
                ?: error("Couldn't load FunctionLiterals.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse FunctionTypes.kt" {
            // FIXED: No support for context receivers yet
            val contents = object {}::class.java.getResource("/parser/FunctionTypes.kt")?.readText()
                ?: error("Couldn't load FunctionTypes.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse IfWithPropery.kt" {
            val contents = object {}::class.java.getResource("/parser/IfWithPropery.kt")?.readText()
                ?: error("Couldn't load IfWithPropery.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse Inner.kt" {
            val contents = object {}::class.java.getResource("/parser/Inner.kt")?.readText()
                ?: error("Couldn't load Inner.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse Interface.kt" {
            val contents = object {}::class.java.getResource("/parser/Interface.kt")?.readText()
                ?: error("Couldn't load Interface.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse LocalDeclarations.kt" {
            // FIXED: Bogus out modifier on val
            val contents = object {}::class.java.getResource("/parser/LocalDeclarations.kt")?.readText()
                ?: error("Couldn't load LocalDeclarations.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse ModifierAsSelector.kt" {
            val contents = object {}::class.java.getResource("/parser/ModifierAsSelector.kt")?.readText()
                ?: error("Couldn't load ModifierAsSelector.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse NamedClassObject.kt" {
            val contents = object {}::class.java.getResource("/parser/NamedClassObject.kt")?.readText()
                ?: error("Couldn't load NamedClassObject.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse NewLinesValidOperations.kt" {
            val contents = object {}::class.java.getResource("/parser/NewLinesValidOperations.kt")?.readText()
                ?: error("Couldn't load NewLinesValidOperations.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse NotIsAndNotIn.kt" {
            val contents = object {}::class.java.getResource("/parser/NotIsAndNotIn.kt")?.readText()
                ?: error("Couldn't load NotIsAndNotIn.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse ObjectLiteralAsStatement.kt" {
            val contents = object {}::class.java.getResource("/parser/ObjectLiteralAsStatement.kt")?.readText()
                ?: error("Couldn't load ObjectLiteralAsStatement.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse PropertyInvokes.kt" {
            val contents = object {}::class.java.getResource("/parser/PropertyInvokes.kt")?.readText()
                ?: error("Couldn't load PropertyInvokes.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse QuotedIdentifiers.kt" {
            val contents = object {}::class.java.getResource("/parser/QuotedIdentifiers.kt")?.readText()
                ?: error("Couldn't load QuotedIdentifiers.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse SemicolonAfterIf.kt" {
            val contents = object {}::class.java.getResource("/parser/SemicolonAfterIf.kt")?.readText()
                ?: error("Couldn't load SemicolonAfterIf.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse SimpleClassMembers.kt" {
            val contents = object {}::class.java.getResource("/parser/SimpleClassMembers.kt")?.readText()
                ?: error("Couldn't load SimpleClassMembers.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse SimpleExpressions.kt" {
            val contents = object {}::class.java.getResource("/parser/SimpleExpressions.kt")?.readText()
                ?: error("Couldn't load SimpleExpressions.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse SimpleModifiers.kt" {
            // FIXED: removed bogus keywords
            val contents = object {}::class.java.getResource("/parser/SimpleModifiers.kt")?.readText()
                ?: error("Couldn't load SimpleModifiers.kt")
            Source.fromKotlin(contents).parse()
        }
        "!it should parse SoftKeywords.kt" {
            // FIXED: So junked up with nonsense not worth trying
            val contents = object {}::class.java.getResource("/parser/SoftKeywords.kt")?.readText()
                ?: error("Couldn't load SoftKeywords.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse SoftKeywordsInTypeArguments.kt" {
            val contents = object {}::class.java.getResource("/parser/SoftKeywordsInTypeArguments.kt")?.readText()
                ?: error("Couldn't load SoftKeywordsInTypeArguments.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse TraitConstructor.kt" {
            val contents = object {}::class.java.getResource("/parser/TraitConstructor.kt")?.readText()
                ?: error("Couldn't load TraitConstructor.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse TypeAlias.kt" {
            val contents = object {}::class.java.getResource("/parser/TypeAlias.kt")?.readText()
                ?: error("Couldn't load TypeAlias.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse TypeConstraints.kt" {
            val contents = object {}::class.java.getResource("/parser/TypeConstraints.kt")?.readText()
                ?: error("Couldn't load TypeConstraints.kt")
            Source.fromKotlin(contents).parse()
        }
        "it should parse TypeModifiers.kt" {
            val contents = object {}::class.java.getResource("/parser/TypeModifiers.kt")?.readText()
                ?: error("Couldn't load TypeModifiers.kt")
            Source.fromKotlin(contents).parse()
        }
    })
