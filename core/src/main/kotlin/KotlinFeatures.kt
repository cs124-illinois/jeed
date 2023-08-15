
@file:Suppress("MatchingDeclarationName", "ktlint:standard:filename")

package edu.illinois.cs.cs125.jeed.core

import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParser
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParser.AnonymousInitializerContext
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParser.ClassBodyContext
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParser.ControlStructureBodyContext
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParser.EmptyFunctionDeclarationContext
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParser.FunctionBodyContext
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParser.FunctionDeclarationContext
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParser.PropertyDeclarationContext
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParser.SecondaryConstructorContext
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParser.StatementContext
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParserBaseListener
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.antlr.v4.runtime.tree.TerminalNode
import org.jetbrains.kotlin.backend.common.pop

private val basicTypes = setOf("Byte", "Short", "Int", "Long", "Float", "Double", "Char", "Boolean")
private val typeCasts = (basicTypes - setOf("Boolean")).map { "to$it" }.toSet()

internal val seenKotlinFeatures = mutableSetOf<FeatureName>()
internal var watchKotlinFeatures = false

@Suppress("TooManyFunctions", "LargeClass", "MagicNumber", "LongMethod", "ComplexMethod")
class KotlinFeatureListener(val source: Source, entry: Map.Entry<String, String>) : KotlinParserBaseListener() {
    private val parsedSource: Source.ParsedSource

    @Suppress("unused")
    private val contents = entry.value
    private val filename = entry.key

    private var anonymousClassDepth = 0
    private var objectLiteralCounter = 0

    private var featureStack: MutableList<FeatureValue> = mutableListOf()
    private val currentFeatures: FeatureValue
        get() = featureStack[0]
    var results: MutableMap<String, UnitFeatures> = mutableMapOf()

    private val currentFeatureMap: MutableMap<FeatureName, Int>
        get() = currentFeatures.features.featureMap

    private val currentFeatureList: MutableList<LocatedFeature>
        get() = currentFeatures.features.featureList

    private fun count(feature: FeatureName, location: Location) {
        val remappedLocation = try {
            source.mapLocation(filename, location)
        } catch (_: SourceMappingException) {
            return
        }
        currentFeatureMap[feature] = (currentFeatureMap[feature] ?: 0) + 1
        currentFeatureList += LocatedFeature(feature, remappedLocation)
        if (watchKotlinFeatures) {
            seenKotlinFeatures += feature
        }
    }

    private fun add(feature: FeatureName, location: Location) {
        val remappedLocation = try {
            source.mapLocation(filename, location)
        } catch (_: SourceMappingException) {
            return
        }
        currentFeatureList += LocatedFeature(feature, remappedLocation)
    }

    private fun ParserRuleContext.toLocation() = Location(start.line, start.charPositionInLine)

    private fun TerminalNode.toLocation() = Location(symbol.line, symbol.charPositionInLine)

    private fun Token.toLocation() = Location(line, charPositionInLine)

    private fun Token.previousToken(): String {
        for (i in (tokenIndex - 1) downTo 0) {
            parsedSource.tokenStream.get(i).text.trim().also {
                if (it != "") {
                    return it
                }
            }
        }
        return ""
    }

    override fun enterKotlinFile(ctx: KotlinParser.KotlinFileContext) {
        val unitFeatures = UnitFeatures(
            filename,
            SourceRange(
                filename,
                Location(ctx.start.line, ctx.start.charPositionInLine),
                Location(ctx.stop.line, ctx.stop.charPositionInLine),
            ),
        )
        assert(featureStack.isEmpty())
        featureStack.add(0, unitFeatures)
    }

    override fun exitKotlinFile(ctx: KotlinParser.KotlinFileContext?) {
        assert(featureStack.size == 1)
        val topLevelFeatures = featureStack.removeAt(0).finalize() as UnitFeatures
        assert(!results.keys.contains(topLevelFeatures.name))
        results[topLevelFeatures.name] = topLevelFeatures
    }

    private fun enterClassOrInterface(name: String, start: Location, end: Location) {
        val locatedClass = if (source is Snippet && name == source.wrappedClassName) {
            ClassFeatures("", source.snippetRange)
        } else {
            val range = try {
                SourceRange(filename, source.mapLocation(filename, start), source.mapLocation(filename, end))
            } catch (e: SourceMappingException) {
                null
            }
            ClassFeatures(name, range)
        }
        if (featureStack.isNotEmpty()) {
            assert(!currentFeatures.classes.containsKey(locatedClass.name))
            currentFeatures.classes[locatedClass.name] = locatedClass
        }
        initCounter = 0
        featureStack.add(0, locatedClass)
    }

    private fun exitClassOrInterface(name: String, start: Location, end: Location) {
        assert(featureStack.isNotEmpty())
        val lastFeatures = featureStack.removeAt(0).finalize()
        assert(lastFeatures is ClassFeatures)
        lastFeatures.methods["init0"]?.also {
            require(!currentFeatures.methods.containsKey("init"))
            lastFeatures.methods["init"] = lastFeatures.methods
                .filterKeys { it.startsWith("init") }
                .values.map { (it as MethodFeatures).features }
                .reduce { first, second ->
                    first + second
                }.let { features ->
                    val range = try {
                        SourceRange(filename, source.mapLocation(filename, start), source.mapLocation(filename, end))
                    } catch (e: SourceMappingException) {
                        null
                    }
                    MethodFeatures(name, range, features = features)
                }
        }
        if (featureStack.isNotEmpty()) {
            currentFeatures.features += lastFeatures.features
        }
    }

    private fun KotlinParser.ClassDeclarationContext.isSnippetClass() = source is Snippet &&
        simpleIdentifier().text == source.wrappedClassName

    private fun FunctionDeclarationContext.isSnippetMethod() = source is Snippet &&
        fullName() == source.looseCodeMethodName

    private fun FunctionDeclarationContext.fullName(): String {
        val name = simpleIdentifier().text
        val parameters = functionValueParameters().functionValueParameter()?.joinToString(",") {
            it.parameter().type().text
        }
        val returnType = type()?.text
        return ("$name($parameters)${returnType?.let { ":$returnType" } ?: ""}").let {
            if (anonymousClassDepth > 0) {
                "${it}${"$"}$objectLiteralCounter"
            } else {
                it
            }
        }
    }

    private fun EmptyFunctionDeclarationContext.fullName(): String {
        val name = simpleIdentifier().text
        val parameters = functionValueParameters().functionValueParameter()?.joinToString(",") {
            it.parameter().type().text
        }
        val returnType = type()?.text
        return ("$name($parameters)${returnType?.let { ":$returnType" } ?: ""}").let {
            if (anonymousClassDepth > 0) {
                "${it}${"$"}$objectLiteralCounter"
            } else {
                it
            }
        }
    }

    override fun enterClassDeclaration(ctx: KotlinParser.ClassDeclarationContext) {
        if (!ctx.isSnippetClass()) {
            count(FeatureName.CLASS, ctx.toLocation())
            ctx.modifiers()?.modifier(0)?.classModifier()?.DATA()?.also {
                count(FeatureName.DATA_CLASS, ctx.toLocation())
            }
            ctx.modifiers()?.modifier(0)?.inheritanceModifier()?.OPEN()?.also {
                count(FeatureName.OPEN_CLASS, ctx.toLocation())
            }
            ctx.modifiers()?.modifier(0)?.inheritanceModifier()?.ABSTRACT()?.also {
                count(FeatureName.ABSTRACT_CLASS, ctx.toLocation())
            }
            ctx.classBody()?.classMemberDeclarations()?.classMemberDeclaration()?.find { it.companionObject() != null }
                ?.also {
                    count(FeatureName.HAS_COMPANION_OBJECT, ctx.toLocation())
                }
        }
        enterClassOrInterface(
            ctx.simpleIdentifier().text,
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine),
        )
        ctx.delegationSpecifiers()?.annotatedDelegationSpecifier()?.map { it.delegationSpecifier() }
            ?.forEach { delegationSpecifier ->
                val specifier = delegationSpecifier.text.trim()
                if (specifier.contains("(") && specifier.endsWith(")")) {
                    count(FeatureName.EXTENDS, delegationSpecifier.toLocation())
                } else {
                    count(FeatureName.IMPLEMENTS, delegationSpecifier.toLocation())
                }
                delegationSpecifier.userType()?.simpleUserType()?.firstOrNull()?.simpleIdentifier()?.also {
                    if (it.text == "Comparable") {
                        count(FeatureName.COMPARABLE, it.toLocation())
                    }
                }
            }
        ctx.typeParameters()?.also {
            count(FeatureName.GENERIC_CLASS, ctx.typeParameters().toLocation())
        }
    }

    override fun exitClassDeclaration(ctx: KotlinParser.ClassDeclarationContext) {
        exitClassOrInterface(
            ctx.simpleIdentifier().text,
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine),
        )
    }

    override fun enterEmptyClassDeclaration(ctx: KotlinParser.EmptyClassDeclarationContext) {
        count(FeatureName.CLASS, ctx.toLocation())
        ctx.modifiers()?.modifier(0)?.classModifier()?.DATA()?.also {
            count(FeatureName.DATA_CLASS, ctx.toLocation())
        }
        ctx.modifiers()?.modifier(0)?.inheritanceModifier()?.OPEN()?.also {
            count(FeatureName.OPEN_CLASS, ctx.toLocation())
        }
        ctx.modifiers()?.modifier(0)?.inheritanceModifier()?.ABSTRACT()?.also {
            count(FeatureName.ABSTRACT_CLASS, ctx.toLocation())
        }

        enterClassOrInterface(
            ctx.simpleIdentifier().text,
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine),
        )
        ctx.delegationSpecifiers()?.annotatedDelegationSpecifier()?.map { it.delegationSpecifier() }
            ?.forEach { delegationSpecifier ->
                val specifier = delegationSpecifier.text.trim()
                if (specifier.contains("(") && specifier.endsWith(")")) {
                    count(FeatureName.EXTENDS, delegationSpecifier.toLocation())
                } else {
                    count(FeatureName.IMPLEMENTS, delegationSpecifier.toLocation())
                }
                delegationSpecifier.userType()?.simpleUserType()?.firstOrNull()?.simpleIdentifier()?.also {
                    if (it.text == "Comparable") {
                        count(FeatureName.COMPARABLE, it.toLocation())
                    }
                }
            }
        ctx.typeParameters()?.also {
            count(FeatureName.GENERIC_CLASS, ctx.typeParameters().toLocation())
        }
    }

    override fun exitEmptyClassDeclaration(ctx: KotlinParser.EmptyClassDeclarationContext) {
        exitClassOrInterface(
            ctx.simpleIdentifier().text,
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine),
        )
    }

    override fun enterInterfaceDeclaration(ctx: KotlinParser.InterfaceDeclarationContext) {
        count(FeatureName.INTERFACE, ctx.toLocation())
        ctx.FUN()?.also {
            count(FeatureName.FUNCTIONAL_INTERFACE, ctx.toLocation())
        }
        enterClassOrInterface(
            ctx.simpleIdentifier().text,
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine),
        )
    }

    override fun exitInterfaceDeclaration(ctx: KotlinParser.InterfaceDeclarationContext) {
        exitClassOrInterface(
            ctx.simpleIdentifier().text,
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine),
        )
    }

    private fun enterMethodOrConstructor(name: String, start: Location, end: Location) {
        val locatedMethod =
            if (source is Snippet &&
                source.looseCodeMethodName == name
            ) {
                MethodFeatures("", source.snippetRange)
            } else {
                val range = try {
                    SourceRange(filename, source.mapLocation(filename, start), source.mapLocation(filename, end))
                } catch (e: SourceMappingException) {
                    null
                }
                MethodFeatures(name, range)
            }
        if (featureStack.isNotEmpty()) {
            require(!currentFeatures.methods.containsKey(locatedMethod.name))
            currentFeatures.methods[locatedMethod.name] = locatedMethod
        }
        featureStack.add(0, locatedMethod)
    }

    private fun exitMethodOrConstructor() {
        require(featureStack.isNotEmpty())
        val lastFeatures = featureStack.removeAt(0).finalize()
        require(lastFeatures is MethodFeatures)
        assert(featureStack.isNotEmpty())
        currentFeatures.features += lastFeatures.features
    }

    private var functionBlockDepths = mutableListOf<Int>()

    @Suppress("unused")
    private val currentBlockDepth
        get() = functionBlockDepths.last()

    private var ifDepths = mutableListOf<Int>()
    private val ifDepth
        get() = ifDepths.last()

    private var loopDepths = mutableListOf<Int>()
    private val loopDepth
        get() = loopDepths.last()

    override fun enterFunctionDeclaration(ctx: FunctionDeclarationContext) {
        if (!ctx.isSnippetMethod()) {
            count(FeatureName.METHOD, ctx.toLocation())
        }
        enterMethodOrConstructor(
            ctx.fullName(),
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine),
        )
        functionBlockDepths += 0
        ifDepths += 0
        loopDepths += 0

        if (ctx.parentType() == ParentType.FUNCTION) {
            count(FeatureName.NESTED_METHOD, ctx.toLocation())
        }
        ctx.modifiers()?.modifier()?.find { it.memberModifier()?.OVERRIDE() != null }?.also { modifier ->
            count(FeatureName.OVERRIDE, modifier.toLocation())
        }
        ctx.modifiers()?.modifier()?.find { it.inheritanceModifier()?.OPEN() != null }?.also { modifier ->
            count(FeatureName.OPEN_METHOD, modifier.toLocation())
        }
    }

    override fun enterEmptyFunctionDeclaration(ctx: EmptyFunctionDeclarationContext) {
        if (ctx.modifiers()?.modifier()?.any { it.inheritanceModifier()?.ABSTRACT() != null } == true) {
            count(FeatureName.ABSTRACT_METHOD, ctx.toLocation())
        } else {
            count(FeatureName.METHOD, ctx.toLocation())
        }
        enterMethodOrConstructor(
            ctx.fullName(),
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine),
        )
    }

    override fun exitEmptyFunctionDeclaration(ctx: EmptyFunctionDeclarationContext?) {
        exitMethodOrConstructor()
    }

    override fun exitFunctionDeclaration(ctx: FunctionDeclarationContext) {
        exitMethodOrConstructor()
        val exitingBlockDepth = functionBlockDepths.pop()
        check(exitingBlockDepth == 0)
        val exitingIfDepth = ifDepths.pop()
        check(exitingIfDepth == 0)
        val exitingLoopDepth = loopDepths.pop()
        check(exitingLoopDepth == 0)
    }

    private var initCounter = 0
    override fun enterAnonymousInitializer(ctx: AnonymousInitializerContext) {
        ifDepths += 0
        functionBlockDepths += 0
        loopDepths += 0
        enterMethodOrConstructor(
            "init${initCounter++}",
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine),
        )
    }

    override fun exitAnonymousInitializer(ctx: AnonymousInitializerContext?) {
        exitMethodOrConstructor()
        val exitingBlockDepth = functionBlockDepths.pop()
        check(exitingBlockDepth == 0)
        val exitingIfDepth = ifDepths.pop()
        check(exitingIfDepth == 0)
        val exitingLoopDepth = loopDepths.pop()
        check(exitingLoopDepth == 0)
    }

    private var constructorCounter = 0
    override fun enterSecondaryConstructor(ctx: SecondaryConstructorContext) {
        count(FeatureName.CONSTRUCTOR, ctx.toLocation())
        count(FeatureName.SECONDARY_CONSTRUCTOR, ctx.toLocation())

        ifDepths += 0
        functionBlockDepths += 0
        loopDepths += 0
        enterMethodOrConstructor(
            "constructor${constructorCounter++}",
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine),
        )
    }

    override fun exitSecondaryConstructor(ctx: SecondaryConstructorContext) {
        exitMethodOrConstructor()
        val exitingBlockDepth = functionBlockDepths.pop()
        check(exitingBlockDepth == 0)
        val exitingIfDepth = ifDepths.pop()
        check(exitingIfDepth == 0)
        val exitingLoopDepth = loopDepths.pop()
        check(exitingLoopDepth == 0)
    }

    override fun enterBlock(ctx: KotlinParser.BlockContext) {
        add(FeatureName.BLOCK_START, ctx.LCURL().toLocation())
        add(FeatureName.BLOCK_END, ctx.RCURL().toLocation())

        if (functionBlockDepths.isNotEmpty()) {
            functionBlockDepths[functionBlockDepths.size - 1]++
        }
    }

    override fun exitBlock(ctx: KotlinParser.BlockContext?) {
        if (functionBlockDepths.isNotEmpty()) {
            functionBlockDepths[functionBlockDepths.size - 1]--
        }
    }

    private enum class ParentType {
        FUNCTION, CLASS, NONE
    }

    private fun ParserRuleContext.parentContext(): RuleContext? {
        var currentParent = parent
        while (currentParent != null) {
            when (currentParent) {
                is FunctionBodyContext -> return currentParent
                is ClassBodyContext -> return currentParent
                is AnonymousInitializerContext -> return currentParent
                is SecondaryConstructorContext -> return currentParent
            }
            currentParent = currentParent.parent
        }
        return null
    }

    @Suppress("unused")
    private inline fun <reified T : RuleContext> ParserRuleContext.searchUp(): T? {
        var currentParent = parent
        while (currentParent != null) {
            currentParent = currentParent.parent
            if (currentParent is T) {
                return currentParent
            }
        }
        return null
    }

    private fun ParserRuleContext.parentType() = when (parentContext()) {
        is FunctionBodyContext -> ParentType.FUNCTION
        is AnonymousInitializerContext -> ParentType.FUNCTION
        is SecondaryConstructorContext -> ParentType.FUNCTION
        is ClassBodyContext -> ParentType.CLASS
        else -> ParentType.NONE
    }

    override fun enterVariableDeclaration(ctx: KotlinParser.VariableDeclarationContext) {
        if (ctx.parent is PropertyDeclarationContext && ctx.parentType() == ParentType.FUNCTION) {
            count(FeatureName.LOCAL_VARIABLE_DECLARATIONS, ctx.toLocation())
            count(FeatureName.VARIABLE_ASSIGNMENTS, ctx.toLocation())
            (ctx.parent as PropertyDeclarationContext).VAL()?.also {
                count(FeatureName.FINAL_VARIABLE, it.toLocation())
            }
        }
        val type = ctx.type()?.text?.trim()
        if (type != null) {
            count(FeatureName.EXPLICIT_TYPE, ctx.type().toLocation())
            val baseType = type.removeSuffix("?")
            if (baseType == "String") {
                count(FeatureName.STRING, ctx.type().toLocation())
            }
            if (baseType != type) {
                count(FeatureName.NULLABLE_TYPE, ctx.type().toLocation())
            }
        } else {
            count(FeatureName.TYPE_INFERENCE, ctx.toLocation())
        }
    }

    override fun enterAssignment(ctx: KotlinParser.AssignmentContext) {
        if (ctx.parentType() == ParentType.FUNCTION) {
            count(FeatureName.VARIABLE_REASSIGNMENTS, ctx.toLocation())
        }
    }

    override fun enterPrefixUnaryOperator(ctx: KotlinParser.PrefixUnaryOperatorContext) {
        if (ctx.parentType() == ParentType.FUNCTION) {
            if (ctx.INCR() != null || ctx.DECR() != null) {
                count(FeatureName.VARIABLE_REASSIGNMENTS, (ctx.INCR() ?: ctx.DECR()).toLocation())
                count(FeatureName.UNARY_OPERATORS, (ctx.INCR() ?: ctx.DECR()).toLocation())
            } else if (ctx.EXCL_WS() != null || ctx.EXCL_NO_WS() != null) {
                val location = ctx.EXCL_WS()?.toLocation() ?: ctx.EXCL_NO_WS()?.toLocation()
                count(FeatureName.LOGICAL_OPERATORS, location!!)
            }
        }
    }

    override fun enterPostfixUnaryOperator(ctx: KotlinParser.PostfixUnaryOperatorContext) {
        if (ctx.parentType() == ParentType.FUNCTION) {
            if (ctx.INCR() != null || ctx.DECR() != null) {
                count(FeatureName.VARIABLE_REASSIGNMENTS, (ctx.INCR() ?: ctx.DECR())!!.toLocation())
                count(FeatureName.UNARY_OPERATORS, (ctx.INCR() ?: ctx.DECR())!!.toLocation())
            }
        }
    }

    override fun enterLoopStatement(ctx: KotlinParser.LoopStatementContext) {
        if (ctx.parentType() != ParentType.FUNCTION) {
            return
        }
        ctx.forStatement()?.also {
            count(FeatureName.FOR_LOOPS, ctx.forStatement().toLocation())
            if (loopDepth > 0) {
                count(FeatureName.NESTED_FOR, ctx.forStatement().toLocation())
                count(FeatureName.NESTED_LOOP, ctx.forStatement().toLocation())
            }
        }
        ctx.whileStatement()?.also {
            count(FeatureName.WHILE_LOOPS, ctx.whileStatement().toLocation())
            if (loopDepth > 0) {
                count(FeatureName.NESTED_WHILE, ctx.whileStatement().toLocation())
                count(FeatureName.NESTED_LOOP, ctx.whileStatement().toLocation())
            }
        }
        ctx.doWhileStatement()?.also {
            count(FeatureName.DO_WHILE_LOOPS, ctx.doWhileStatement().toLocation())
            if (loopDepth > 0) {
                count(FeatureName.NESTED_DO_WHILE, ctx.doWhileStatement().toLocation())
                count(FeatureName.NESTED_LOOP, ctx.doWhileStatement().toLocation())
            }
        }
        loopDepths[loopDepths.size - 1]++
    }

    override fun exitLoopStatement(ctx: KotlinParser.LoopStatementContext?) {
        loopDepths[loopDepths.size - 1]--
        check(loopDepth >= 0)
    }

    private var inForStatement = false
    override fun enterForStatement(ctx: KotlinParser.ForStatementContext?) {
        inForStatement = true
    }

    override fun exitForStatement(ctx: KotlinParser.ForStatementContext?) {
        inForStatement = false
    }

    private val bitwiseOperators = listOf("and", "or", "shl", "shr")

    override fun enterSimpleIdentifier(ctx: KotlinParser.SimpleIdentifierContext) {
        val identifier = ctx.Identifier()?.text
        if (identifier == "arrayOf" ||
            identifier == "Array" ||
            identifier?.endsWith("ArrayOf") == true
        ) {
            count(FeatureName.ARRAYS, ctx.toLocation())
            count(FeatureName.ARRAY_LITERAL, ctx.toLocation())
        } else if (inForStatement && identifier == "step") {
            count(FeatureName.FOR_LOOP_STEP, ctx.toLocation())
        } else if (bitwiseOperators.contains(identifier)) {
            count(FeatureName.BITWISE_OPERATORS, ctx.toLocation())
        }
    }

    // Gotta love this grammar
    private fun ControlStructureBodyContext.isIf() = statement()
        ?.expression()
        ?.primaryExpression()
        ?.ifExpression() != null

    private val topIfs = mutableSetOf<Int>()
    private val seenIfStarts = mutableSetOf<Int>()
    override fun enterIfExpression(ctx: KotlinParser.IfExpressionContext) {
        val ifStart = ctx.start.startIndex
        if (ifStart !in seenIfStarts) {
            count(FeatureName.IF_STATEMENTS, ctx.toLocation())
            if (ctx.start.previousToken() == "=") {
                count(FeatureName.IF_EXPRESSIONS, ctx.toLocation())
            }
            seenIfStarts += ifStart
            topIfs += ifStart
            if (ifDepth > 0) {
                count(FeatureName.NESTED_IF, ctx.toLocation())
            }
            ifDepths[ifDepths.size - 1]++
        }

        ctx.controlStructureBody().forEach {
            if (it.isIf()) {
                count(FeatureName.ELSE_IF, it.toLocation())
                seenIfStarts += it.start.startIndex
            }
        }
        if (ctx.ELSE() != null && ctx.controlStructureBody()!!.last().block() != null) {
            count(FeatureName.ELSE_STATEMENTS, ctx.ELSE().toLocation())
        }
    }

    override fun exitIfExpression(ctx: KotlinParser.IfExpressionContext) {
        if (ctx.start.startIndex in topIfs) {
            ifDepths[ifDepths.size - 1]--
            check(ifDepth >= 0)
        }
    }

    private val printStatements = setOf("println", "print")
    private val javaPrintStatements =
        setOf("System.out.println", "System.err.println", "System.out.print", "System.err.print")
    private val unnecessaryJavaPrintStatements = setOf("System.out.println", "System.out.print")
    private val assertStatements = setOf("assert")
    private val requireOrCheckStatements = setOf("require", "check")
    private val javaEqualsStatements = setOf("equals")

    override fun enterPostfixUnaryExpression(ctx: KotlinParser.PostfixUnaryExpressionContext) {
        for (i in 0 until ctx.postfixUnarySuffix().size) {
            val current = ctx.postfixUnarySuffix(i)
            if (current.navigationSuffix() == null) {
                continue
            }
            val next = if (i == ctx.postfixUnarySuffix().size - 1) {
                null
            } else {
                ctx.postfixUnarySuffix(i + 1)
            }
            if (current.navigationSuffix().memberAccessOperator()?.DOT() != null &&
                current.navigationSuffix().simpleIdentifier() != null
            ) {
                count(FeatureName.DOT_NOTATION, current.navigationSuffix().memberAccessOperator().DOT().toLocation())
                if (next?.callSuffix() != null) {
                    val identifier = current.navigationSuffix().simpleIdentifier().text
                    count(FeatureName.DOTTED_METHOD_CALL, next.callSuffix().toLocation())
                    currentFeatures.features.dottedMethodList += identifier
                    if (identifier in typeCasts) {
                        count(FeatureName.PRIMITIVE_CASTING, current.navigationSuffix().simpleIdentifier().toLocation())
                    }
                } else {
                    count(
                        FeatureName.DOTTED_VARIABLE_ACCESS,
                        current.navigationSuffix().memberAccessOperator().DOT().toLocation(),
                    )
                }
            }
        }
    }

    override fun enterComparisonOperator(ctx: KotlinParser.ComparisonOperatorContext) {
        count(FeatureName.COMPARISON_OPERATORS, ctx.toLocation())
    }

    private enum class SeparatorType {
        DOT, COLONCOLON, SAFENAV, UNSAFENAV
    }

    override fun enterExpression(ctx: KotlinParser.ExpressionContext) {
        ctx.DISJ()?.also {
            count(FeatureName.LOGICAL_OPERATORS, ctx.DISJ().toLocation())
        }
        ctx.CONJ()?.also {
            count(FeatureName.LOGICAL_OPERATORS, ctx.CONJ().toLocation())
        }
        ctx.isOperator?.also {
            count(FeatureName.INSTANCEOF, ctx.isOperator.toLocation())
        }
        ctx.asOperator()?.also {
            ctx.type().also {
                if (it.text in basicTypes) {
                    count(FeatureName.PRIMITIVE_CASTING, ctx.asOperator().toLocation())
                } else {
                    count(FeatureName.CASTING, ctx.asOperator().toLocation())
                }
            }
        }
        ctx.elvis()?.also {
            count(FeatureName.ELVIS_OPERATOR, ctx.elvis().toLocation())
        }
        ctx.RANGE()?.also {
            if (inForStatement) {
                count(FeatureName.FOR_LOOP_RANGE, ctx.RANGE().toLocation())
            }
        }
        if (ctx.postfixUnarySuffix().isNotEmpty()) {
            var skipDots = false
            if (ctx.expression().getOrNull(0)?.primaryExpression()?.simpleIdentifier() != null &&
                ctx.postfixUnarySuffix().isNotEmpty() &&
                ctx.postfixUnarySuffix().last().callSuffix() != null &&
                ctx.postfixUnarySuffix().dropLast(1).all { it.navigationSuffix() != null }
            ) {
                val fullMethodCall = ctx.expression().getOrNull(0)?.primaryExpression()?.simpleIdentifier()?.text +
                    ctx.postfixUnarySuffix()
                        .dropLast(1)
                        .joinToString(".") {
                            it.navigationSuffix().simpleIdentifier().text
                        }.let {
                            if (it.isNotBlank()) {
                                ".$it"
                            } else {
                                ""
                            }
                        }
                val location = ctx.expression()[0].primaryExpression().simpleIdentifier().toLocation()
                val lastChunk = fullMethodCall.split(".").last()
                if (printStatements.contains(fullMethodCall)) {
                    count(FeatureName.PRINT_STATEMENTS, location)
                } else if (javaPrintStatements.contains(fullMethodCall)) {
                    count(FeatureName.PRINT_STATEMENTS, location)
                    if (unnecessaryJavaPrintStatements.contains(fullMethodCall)) {
                        count(FeatureName.JAVA_PRINT_STATEMENTS, location)
                    }
                    skipDots = true
                } else if (assertStatements.contains(fullMethodCall)) {
                    count(FeatureName.ASSERT, location)
                } else if (requireOrCheckStatements.contains(fullMethodCall)) {
                    count(FeatureName.REQUIRE_OR_CHECK, location)
                } else if (javaEqualsStatements.contains(lastChunk)) {
                    count(FeatureName.EQUALITY, location)
                    count(FeatureName.JAVA_EQUALITY, location)
                } else if (fullMethodCall == "String") {
                    count(FeatureName.STRING, location)
                }
            }
            if (!skipDots) {
                for (i in 0 until ctx.postfixUnarySuffix().size) {
                    val current = ctx.postfixUnarySuffix(i)
                    if (current.navigationSuffix() == null) {
                        continue
                    }
                    val next = if (i == ctx.postfixUnarySuffix().size - 1) {
                        null
                    } else {
                        ctx.postfixUnarySuffix(i + 1)
                    }

                    val currentSuffix = current.navigationSuffix()

                    val separatorType = currentSuffix.memberAccessOperator()?.let {
                        when {
                            it.DOT() != null -> SeparatorType.DOT
                            it.COLONCOLON() != null -> SeparatorType.COLONCOLON
                            it.SAFENAV() != null -> SeparatorType.SAFENAV
                            it.BANGS_WITH_DOT() != null -> SeparatorType.UNSAFENAV
                            else -> error("Invalid separator")
                        }
                    }

                    if (separatorType != null && currentSuffix.simpleIdentifier() != null) {
                        val location = currentSuffix.memberAccessOperator().toLocation()
                        when (separatorType) {
                            SeparatorType.DOT -> count(FeatureName.DOT_NOTATION, location)
                            SeparatorType.SAFENAV -> count(FeatureName.SAFE_CALL_OPERATOR, location)
                            SeparatorType.UNSAFENAV -> count(FeatureName.UNSAFE_CALL_OPERATOR, location)
                            // Not counting COLONCOLON yet
                            else -> {}
                        }
                        if (next?.callSuffix() != null) {
                            val identifier = current.navigationSuffix().simpleIdentifier().text
                            count(FeatureName.DOTTED_METHOD_CALL, next.callSuffix().toLocation())
                            currentFeatures.features.dottedMethodList += identifier
                            if (identifier in typeCasts) {
                                count(
                                    FeatureName.PRIMITIVE_CASTING,
                                    current.navigationSuffix().simpleIdentifier().toLocation(),
                                )
                            }
                        } else {
                            count(FeatureName.DOTTED_VARIABLE_ACCESS, location)
                        }
                    }
                }
            }
        }
        val indices = ctx.postfixUnarySuffix()?.filter { it.indexingSuffix() != null } ?: listOf()
        if (indices.isNotEmpty()) {
            count(FeatureName.COLLECTION_INDEXING, indices.first().toLocation())
            if (indices.size > 1) {
                count(FeatureName.MULTILEVEL_COLLECTION_INDEXING, indices.first().toLocation())
            }
        }
    }

    override fun enterObjectLiteral(ctx: KotlinParser.ObjectLiteralContext) {
        if (ctx.classBody() != null) {
            anonymousClassDepth++
            objectLiteralCounter++
        }
        count(FeatureName.ANONYMOUS_CLASSES, ctx.toLocation())
    }

    override fun exitObjectLiteral(ctx: KotlinParser.ObjectLiteralContext) {
        if (ctx.classBody() != null) {
            anonymousClassDepth--
        }
    }

    override fun enterImportHeader(ctx: KotlinParser.ImportHeaderContext) {
        count(FeatureName.IMPORT, ctx.toLocation())
        val importName = ctx.identifier().text + if (ctx.DOT() != null) {
            ".*"
        } else {
            ""
        }
        currentFeatures.features.importList += importName
    }

    override fun enterTypeTest(ctx: KotlinParser.TypeTestContext) {
        count(FeatureName.INSTANCEOF, ctx.toLocation())
    }

    override fun enterPropertyDeclaration(ctx: PropertyDeclarationContext) {
        if (ctx.parentType() == ParentType.CLASS) {
            count(FeatureName.CLASS_FIELD, ctx.toLocation())
            ctx.modifiers()?.modifier()?.find { it.inheritanceModifier()?.ABSTRACT() != null }?.also {
                count(FeatureName.ABSTRACT_FIELD, it.toLocation())
            }
            ctx.VAL()?.also {
                count(FeatureName.FINAL_FIELD, ctx.VAL().toLocation())
            }
        }
    }

    override fun enterPrimaryConstructor(ctx: KotlinParser.PrimaryConstructorContext) {
        count(FeatureName.CONSTRUCTOR, ctx.toLocation())
    }

    override fun enterEqualityOperator(ctx: KotlinParser.EqualityOperatorContext) {
        if (ctx.EQEQEQ() != null || ctx.EXCL_EQEQ() != null) {
            count(FeatureName.REFERENCE_EQUALITY, (ctx.EQEQEQ() ?: ctx.EXCL_EQEQ()).toLocation())
        } else {
            count(FeatureName.EQUALITY, ctx.toLocation())
        }
    }

    override fun enterCompanionObject(ctx: KotlinParser.CompanionObjectContext) {
        count(FeatureName.COMPANION_OBJECT, ctx.toLocation())
        ctx.classBody()?.classMemberDeclarations()?.classMemberDeclaration()?.forEach { memberDeclaration ->
            memberDeclaration?.declaration()?.functionDeclaration()?.also {
                count(FeatureName.STATIC_METHOD, it.toLocation())
            }
            memberDeclaration?.declaration()?.propertyDeclaration()?.also {
                count(FeatureName.STATIC_FIELD, it.toLocation())
            }
        }
    }

    override fun enterAssignmentAndOperator(ctx: KotlinParser.AssignmentAndOperatorContext) {
        count(FeatureName.ASSIGNMENT_OPERATORS, ctx.toLocation())
    }

    override fun enterAdditiveOperator(ctx: KotlinParser.AdditiveOperatorContext) {
        count(FeatureName.ARITHMETIC_OPERATORS, ctx.toLocation())
    }

    override fun enterMultiplicativeOperator(ctx: KotlinParser.MultiplicativeOperatorContext) {
        count(FeatureName.ARITHMETIC_OPERATORS, ctx.toLocation())
    }

    override fun enterJumpExpression(ctx: KotlinParser.JumpExpressionContext) {
        if (ctx.BREAK() != null || ctx.BREAK_AT() != null) {
            count(FeatureName.BREAK, (ctx.BREAK() ?: ctx.BREAK_AT()).toLocation())
        } else if (ctx.CONTINUE() != null || ctx.CONTINUE_AT() != null) {
            count(FeatureName.CONTINUE, (ctx.CONTINUE() ?: ctx.CONTINUE_AT()).toLocation())
        } else if (ctx.RETURN() != null || ctx.RETURN_AT() != null) {
            count(FeatureName.RETURN, (ctx.RETURN() ?: ctx.RETURN_AT()).toLocation())
        } else if (ctx.THROW() != null) {
            count(FeatureName.THROW, ctx.THROW().toLocation())
        }
    }

    override fun enterLiteralConstant(ctx: KotlinParser.LiteralConstantContext) {
        if (ctx.NullLiteral() != null) {
            count(FeatureName.NULL, ctx.NullLiteral().toLocation())
        }
    }

    override fun enterStringLiteral(ctx: KotlinParser.StringLiteralContext) {
        count(FeatureName.STRING, ctx.toLocation())
    }

    override fun enterWhenExpression(ctx: KotlinParser.WhenExpressionContext) {
        count(FeatureName.WHEN_STATEMENT, ctx.toLocation())
        if (ctx.start.previousToken() == "=") {
            count(FeatureName.WHEN_EXPRESSIONS, ctx.toLocation())
        }
        add(FeatureName.BLOCK_START, ctx.LCURL().toLocation())
        add(FeatureName.BLOCK_END, ctx.RCURL().toLocation())
    }

    override fun enterWhenEntry(ctx: KotlinParser.WhenEntryContext) {
        if (ctx.ELSE() != null) {
            count(FeatureName.ELSE_STATEMENTS, ctx.ELSE().toLocation())
        }
    }

    override fun enterEnumClassBody(ctx: KotlinParser.EnumClassBodyContext) {
        count(FeatureName.ENUM, ctx.toLocation())
        add(FeatureName.BLOCK_START, ctx.LCURL().toLocation())
        add(FeatureName.BLOCK_END, ctx.RCURL().toLocation())
    }

    override fun enterSetter(ctx: KotlinParser.SetterContext) {
        count(FeatureName.SETTER, ctx.toLocation())
        ifDepths += 0
    }

    override fun exitSetter(ctx: KotlinParser.SetterContext) {
        ifDepths.pop()
    }

    override fun enterGetter(ctx: KotlinParser.GetterContext) {
        count(FeatureName.GETTER, ctx.toLocation())
        ifDepths += 0
    }

    override fun exitGetter(ctx: KotlinParser.GetterContext?) {
        ifDepths.pop()
    }

    override fun enterThisExpression(ctx: KotlinParser.ThisExpressionContext) {
        count(FeatureName.THIS, ctx.toLocation())
    }

    override fun enterSuperExpression(ctx: KotlinParser.SuperExpressionContext) {
        count(FeatureName.SUPER, ctx.toLocation())
    }

    override fun enterTypeParameters(ctx: KotlinParser.TypeParametersContext) {
        ctx.typeParameter().forEach { typeParameter ->
            count(FeatureName.TYPE_PARAMETERS, typeParameter.toLocation())
        }
    }

    override fun enterTypeArguments(ctx: KotlinParser.TypeArgumentsContext) {
        ctx.typeProjection().forEach { typeProjection ->
            count(FeatureName.TYPE_PARAMETERS, typeProjection.toLocation())
        }
    }

    override fun enterVisibilityModifier(ctx: KotlinParser.VisibilityModifierContext) {
        count(FeatureName.VISIBILITY_MODIFIERS, ctx.toLocation())
    }

    override fun enterClassMemberDeclaration(ctx: KotlinParser.ClassMemberDeclarationContext) {
        ctx.declaration()?.classDeclaration()?.also {
            count(FeatureName.NESTED_CLASS, it.toLocation())
        }
        ctx.declaration()?.emptyClassDeclaration()?.also {
            count(FeatureName.NESTED_CLASS, it.toLocation())
        }
    }

    override fun enterClassBody(ctx: ClassBodyContext) {
        add(FeatureName.BLOCK_START, ctx.LCURL().toLocation())
        add(FeatureName.BLOCK_END, ctx.RCURL().toLocation())
    }

    override fun enterInterfaceBody(ctx: KotlinParser.InterfaceBodyContext) {
        add(FeatureName.BLOCK_START, ctx.LCURL().toLocation())
        add(FeatureName.BLOCK_END, ctx.RCURL().toLocation())
    }

    override fun enterTryExpression(ctx: KotlinParser.TryExpressionContext) {
        count(FeatureName.TRY_BLOCK, ctx.toLocation())
        if (ctx.start.previousToken() == "=") {
            count(FeatureName.TRY_EXPRESSIONS, ctx.toLocation())
        }
        ctx.finallyBlock()?.also {
            count(FeatureName.FINALLY, it.toLocation())
        }
    }

    override fun enterObjectDeclaration(ctx: KotlinParser.ObjectDeclarationContext) {
        count(FeatureName.SINGLETON, ctx.toLocation())
        enterClassOrInterface(
            ctx.simpleIdentifier().text,
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine),
        )
    }

    override fun exitObjectDeclaration(ctx: KotlinParser.ObjectDeclarationContext) {
        exitClassOrInterface(
            ctx.simpleIdentifier().text,
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine),
        )
    }

    override fun enterLambdaLiteral(ctx: KotlinParser.LambdaLiteralContext) {
        count(FeatureName.LAMBDA_EXPRESSIONS, ctx.toLocation())
        add(FeatureName.BLOCK_START, ctx.LCURL().toLocation())
        add(FeatureName.BLOCK_END, ctx.RCURL().toLocation())
    }

    override fun enterAnonymousFunction(ctx: KotlinParser.AnonymousFunctionContext) {
        count(FeatureName.ANONYMOUS_FUNCTION, ctx.toLocation())
    }

    override fun enterStatement(ctx: StatementContext) {
        add(FeatureName.STATEMENT_START, ctx.start.toLocation())
        add(FeatureName.STATEMENT_END, ctx.stop.toLocation())
    }

    init {
        parsedSource = source.getParsed(filename)
        // println(parsedSource.tree.format(parsedSource.parser))
        ParseTreeWalker.DEFAULT.walk(this, parsedSource.tree)
    }
}
