// ktlint-disable filename
@file:Suppress("MatchingDeclarationName")

package edu.illinois.cs.cs125.jeed.core

import edu.illinois.cs.cs125.jeed.core.antlr.JavaParser
import edu.illinois.cs.cs125.jeed.core.antlr.JavaParser.CreatorContext
import edu.illinois.cs.cs125.jeed.core.antlr.JavaParser.ExpressionContext
import edu.illinois.cs.cs125.jeed.core.antlr.JavaParser.MethodCallContext
import edu.illinois.cs.cs125.jeed.core.antlr.JavaParser.StatementContext
import edu.illinois.cs.cs125.jeed.core.antlr.JavaParserBaseListener
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.antlr.v4.runtime.tree.TerminalNode

@Suppress("TooManyFunctions", "LargeClass", "MagicNumber", "LongMethod", "ComplexMethod")
class JavaFeatureListener(val source: Source, entry: Map.Entry<String, String>) : JavaParserBaseListener() {
    @Suppress("unused")
    private val contents = entry.value
    private val filename = entry.key

    private var featureStack: MutableList<FeatureValue> = mutableListOf()
    private val currentFeatures: FeatureValue
        get() = featureStack[0]
    var results: MutableMap<String, UnitFeatures> = mutableMapOf()
    private var anonymousClassCount = 0

    private fun enterClassOrInterface(name: String, start: Location, end: Location, anonymous: Boolean = false) {
        val locatedClass = if (source is Snippet && name == source.wrappedClassName) {
            ClassFeatures("", source.snippetRange)
        } else {
            ClassFeatures(
                name,
                SourceRange(filename, source.mapLocation(filename, start), source.mapLocation(filename, end))
            )
        }
        if (featureStack.isNotEmpty()) {
            assert(!currentFeatures.classes.containsKey(locatedClass.name)) {
                "Duplicate class ${locatedClass.name}"
            }
            currentFeatures.classes[locatedClass.name] = locatedClass
        }
        featureStack.add(0, locatedClass)
        if (!anonymous) {
            anonymousClassCount = 0
        }
    }

    private fun enterMethodOrConstructor(name: String, start: Location, end: Location) {
        val locatedMethod =
            if (source is Snippet &&
                "void " + source.looseCodeMethodName == name &&
                (featureStack.getOrNull(0) as? ClassFeatures)?.name == ""
            ) {
                MethodFeatures("", source.snippetRange)
            } else {
                MethodFeatures(
                    name,
                    SourceRange(name, source.mapLocation(filename, start), source.mapLocation(filename, end))
                )
            }
        if (featureStack.isNotEmpty()) {
            assert(!currentFeatures.methods.containsKey(locatedMethod.name)) {
                "Duplicate method ${locatedMethod.name} in ${currentFeatures.name}"
            }
            currentFeatures.methods[locatedMethod.name] = locatedMethod
        }
        featureStack.add(0, locatedMethod)
    }

    private fun exitClassOrInterface() {
        assert(featureStack.isNotEmpty())
        val lastFeatures = featureStack.removeAt(0).finalize()
        assert(lastFeatures is ClassFeatures)
        if (featureStack.isNotEmpty()) {
            currentFeatures.features += lastFeatures.features
        }
    }

    private fun exitMethodOrConstructor() {
        assert(featureStack.isNotEmpty())
        val lastFeatures = featureStack.removeAt(0).finalize()
        assert(lastFeatures is MethodFeatures)
        assert(featureStack.isNotEmpty())
        currentFeatures.features += lastFeatures.features
    }

    override fun enterCompilationUnit(ctx: JavaParser.CompilationUnitContext) {
        val unitFeatures = UnitFeatures(
            filename,
            SourceRange(
                filename,
                Location(ctx.start.line, ctx.start.charPositionInLine),
                Location(ctx.stop.line, ctx.stop.charPositionInLine)
            )
        )
        assert(featureStack.isEmpty())
        featureStack.add(0, unitFeatures)
        ctx.importDeclaration().filter { it.IMPORT() != null }.forEach {
            count(FeatureName.IMPORT, it.toLocation())
        }
        ctx.typeDeclaration()
            .filter { declaration -> declaration.classOrInterfaceModifier().any { it.FINAL() != null } }.forEach {
                count(FeatureName.FINAL_CLASS, it.toLocation())
            }
        ctx.typeDeclaration()
            .filter { declaration -> declaration.classOrInterfaceModifier().any { it.ABSTRACT() != null } }.forEach {
                count(FeatureName.ABSTRACT_CLASS, it.toLocation())
            }
        ctx.typeDeclaration()
            .filter { declaration -> declaration.classDeclaration()?.isSnippetClass() != true }
            .filter { declaration ->
                declaration.classOrInterfaceModifier().any {
                    when (it.text) {
                        "public", "private", "protected" -> true
                        else -> false
                    }
                }
            }
            .forEach {
                count(FeatureName.VISIBILITY_MODIFIERS, it.toLocation())
            }
        for (import in ctx.importDeclaration()) {
            currentFeatures.features.importList.add(import.qualifiedName().text)
        }
    }

    override fun exitCompilationUnit(ctx: JavaParser.CompilationUnitContext) {
        assert(featureStack.size == 1)
        val topLevelFeatures = featureStack.removeAt(0).finalize() as UnitFeatures
        assert(!results.keys.contains(topLevelFeatures.name))
        results[topLevelFeatures.name] = topLevelFeatures
    }

    override fun enterClassDeclaration(ctx: JavaParser.ClassDeclarationContext) {
        if (!ctx.isSnippetClass()) {
            count(FeatureName.CLASS, ctx.toLocation())
        }

        enterClassOrInterface(
            ctx.identifier().text,
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine)
        )

        ctx.classBody().classBodyDeclaration()
            .filter { declaration -> declaration.memberDeclaration()?.fieldDeclaration() != null }
            .forEach { count(FeatureName.CLASS_FIELD, it.toLocation()) }

        ctx.classBody().classBodyDeclaration().filter { declaration ->
            declaration.memberDeclaration()?.methodDeclaration()?.isSnippetMethod() != true
        }.filter { declaration ->
            declaration.modifier().any {
                it.classOrInterfaceModifier().STATIC() != null &&
                    declaration.memberDeclaration().methodDeclaration() != null
            }
        }.forEach { count(FeatureName.STATIC_METHOD, it.toLocation()) }

        ctx.classBody().classBodyDeclaration().filter { declaration ->
            declaration.modifier().any {
                it.classOrInterfaceModifier().STATIC() != null &&
                    declaration.memberDeclaration().fieldDeclaration() != null
            }
        }.forEach { count(FeatureName.STATIC_FIELD, it.toLocation()) }

        ctx.classBody().classBodyDeclaration().filter { declaration ->
            declaration.modifier().any {
                it.classOrInterfaceModifier().FINAL() != null &&
                    declaration.memberDeclaration().methodDeclaration() != null
            }
        }.forEach { count(FeatureName.FINAL_METHOD, it.toLocation()) }

        ctx.classBody().classBodyDeclaration().filter { declaration ->
            declaration.modifier().any {
                it.classOrInterfaceModifier().FINAL() != null &&
                    declaration.memberDeclaration().fieldDeclaration() != null
            }
        }.forEach { count(FeatureName.FINAL_FIELD, it.toLocation()) }

        ctx.classBody().classBodyDeclaration().filter { declaration ->
            declaration.modifier().any {
                it.classOrInterfaceModifier().ABSTRACT() != null &&
                    declaration.memberDeclaration().methodDeclaration() != null
            }
        }.forEach { count(FeatureName.ABSTRACT_METHOD, it.toLocation()) }

        ctx.classBody().classBodyDeclaration().filter { declaration ->
            declaration.modifier().any {
                it.classOrInterfaceModifier().ABSTRACT() != null &&
                    declaration.memberDeclaration().fieldDeclaration() != null
            }
        }.forEach { count(FeatureName.ABSTRACT_FIELD, it.toLocation()) }

        ctx.classBody().classBodyDeclaration()
            .filter { it.memberDeclaration() != null }
            .filter { declaration ->
                declaration.memberDeclaration().methodDeclaration()?.isSnippetMethod() != true
            }.filter { declaration ->
                declaration.modifier().any {
                    when (it.text) {
                        "public", "private", "protected" -> true
                        else -> false
                    }
                }
            }.forEach { count(FeatureName.VISIBILITY_MODIFIERS, it.toLocation()) }

        ctx.classBody().classBodyDeclaration().filter { declaration ->
            declaration.modifier().any {
                it?.text == "@Override"
            }
        }.forEach { count(FeatureName.OVERRIDE, it.toLocation()) }

        ctx.classBody().classBodyDeclaration().filter { declaration ->
            declaration.memberDeclaration()?.classDeclaration() != null
        }.forEach { count(FeatureName.NESTED_CLASS, it.toLocation()) }

        ctx.classBody().classBodyDeclaration().filter { declaration ->
            declaration.modifier().any {
                it.classOrInterfaceModifier().FINAL() != null &&
                    declaration.memberDeclaration().classDeclaration() != null
            }
        }.forEach { count(FeatureName.FINAL_CLASS, it.toLocation()) }

        ctx.classBody().classBodyDeclaration().filter { declaration ->
            declaration.modifier().any {
                it.classOrInterfaceModifier().ABSTRACT() != null &&
                    declaration.memberDeclaration().classDeclaration() != null
            }
        }.forEach { count(FeatureName.ABSTRACT_CLASS, it.toLocation()) }

        ctx.EXTENDS()?.also { count(FeatureName.EXTENDS, ctx.toLocation()) }

        ctx.IMPLEMENTS()?.also { count(FeatureName.IMPLEMENTS, ctx.toLocation()) }

        ctx.typeParameters()?.also { count(FeatureName.GENERIC_CLASS, ctx.toLocation()) }
    }

    override fun exitClassDeclaration(ctx: JavaParser.ClassDeclarationContext) {
        exitClassOrInterface()
    }

    override fun enterInterfaceDeclaration(ctx: JavaParser.InterfaceDeclarationContext) {
        enterClassOrInterface(
            ctx.identifier().text,
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine)
        )

        count(FeatureName.INTERFACE, ctx.toLocation())

        ctx.interfaceBody().interfaceBodyDeclaration().filter { declaration ->
            declaration.modifier().any {
                it.classOrInterfaceModifier().STATIC() != null &&
                    declaration.interfaceMemberDeclaration().interfaceMethodDeclaration() != null
            }
        }.forEach { count(FeatureName.STATIC_METHOD, it.toLocation()) }

        ctx.interfaceBody().interfaceBodyDeclaration().filter { declaration ->
            declaration.modifier().any {
                it.classOrInterfaceModifier().FINAL() != null &&
                    declaration.interfaceMemberDeclaration().interfaceMethodDeclaration() != null
            }
        }.forEach { count(FeatureName.FINAL_METHOD, it.toLocation()) }

        ctx.interfaceBody().interfaceBodyDeclaration().filter { declaration ->
            declaration.modifier().any {
                it.classOrInterfaceModifier().ABSTRACT() != null &&
                    declaration.interfaceMemberDeclaration().interfaceMethodDeclaration() != null
            }
        }.forEach { count(FeatureName.ABSTRACT_METHOD, it.toLocation()) }

        ctx.interfaceBody().interfaceBodyDeclaration().filter { declaration ->
            declaration.modifier().any {
                when (it.text) {
                    "public", "private", "protected" -> true
                    else -> false
                }
            }
        }.forEach { count(FeatureName.VISIBILITY_MODIFIERS, it.toLocation()) }
    }

    override fun exitInterfaceDeclaration(ctx: JavaParser.InterfaceDeclarationContext?) {
        exitClassOrInterface()
    }

    override fun enterEnumDeclaration(ctx: JavaParser.EnumDeclarationContext) {
        enterClassOrInterface(
            ctx.identifier().text,
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine)
        )
        count(FeatureName.ENUM, ctx.toLocation())
    }

    override fun exitEnumDeclaration(ctx: JavaParser.EnumDeclarationContext) {
        exitClassOrInterface()
    }

    override fun enterRecordDeclaration(ctx: JavaParser.RecordDeclarationContext) {
        enterClassOrInterface(
            ctx.identifier().text,
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine)
        )
        count(FeatureName.RECORD, ctx.toLocation())
    }

    override fun exitRecordDeclaration(ctx: JavaParser.RecordDeclarationContext?) {
        exitClassOrInterface()
    }

    private fun JavaParser.MethodDeclarationContext.fullName(): String {
        val parameters = formalParameters().formalParameterList()?.formalParameter()?.joinToString(",") {
            it.typeType().text
        } ?: ""
        return "${identifier().text}($parameters)"
    }

    private fun JavaParser.ClassDeclarationContext.isSnippetClass() = source is Snippet &&
        identifier().text == source.wrappedClassName

    private fun JavaParser.MethodDeclarationContext.isSnippetMethod() = source is Snippet &&
        fullName() == source.looseCodeMethodName &&
        (featureStack.getOrNull(0) as? ClassFeatures)?.name == ""

    override fun enterMethodDeclaration(ctx: JavaParser.MethodDeclarationContext) {
        if (!ctx.isSnippetMethod()) {
            count(FeatureName.METHOD, ctx.toLocation())
            if (ctx.identifier().text.startsWith("get")) {
                count(FeatureName.GETTER, ctx.toLocation())
            }
            if (ctx.identifier().text.startsWith("set")) {
                count(FeatureName.SETTER, ctx.toLocation())
            }
            ctx.THROWS()?.also {
                count(FeatureName.THROWS, ctx.toLocation())
            }
        }
        ctx.methodBody().block()?.blockStatement()?.filter { statement ->
            statement.localTypeDeclaration()?.classOrInterfaceModifier()?.any {
                it.FINAL() != null && statement.localTypeDeclaration().classDeclaration() != null
            } ?: false
        }?.forEach {
            count(FeatureName.FINAL_CLASS, it.toLocation())
        }

        ctx.methodBody().block()?.blockStatement()?.filter { statement ->
            statement.localTypeDeclaration()?.classOrInterfaceModifier()?.any {
                it.ABSTRACT() != null && statement.localTypeDeclaration().classDeclaration() != null
            } ?: false
        }?.forEach { count(FeatureName.ABSTRACT_CLASS, it.toLocation()) }

        enterMethodOrConstructor(
            "${ctx.typeTypeOrVoid().text} ${ctx.fullName()}",
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine)
        )
    }

    override fun exitMethodDeclaration(ctx: JavaParser.MethodDeclarationContext) {
        exitMethodOrConstructor()
    }

    override fun enterInterfaceMethodDeclaration(ctx: JavaParser.InterfaceMethodDeclarationContext) {
        val parameters = ctx.formalParameters().formalParameterList()?.formalParameter()?.joinToString(",") {
            it.typeType().text
        } ?: ""
        enterMethodOrConstructor(
            "${ctx.typeTypeOrVoid().text} ${ctx.identifier().text}($parameters)",
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine)
        )
        count(FeatureName.METHOD, ctx.toLocation())
    }

    override fun exitInterfaceMethodDeclaration(ctx: JavaParser.InterfaceMethodDeclarationContext) {
        exitMethodOrConstructor()
    }

    override fun enterConstructorDeclaration(ctx: JavaParser.ConstructorDeclarationContext) {
        assert(featureStack.isNotEmpty())
        val currentClass = currentFeatures as ClassFeatures
        val parameters = ctx.formalParameters().formalParameterList()?.formalParameter()?.joinToString(",") {
            it.typeType().text
        } ?: ""
        enterMethodOrConstructor(
            "${currentClass.name}($parameters)",
            Location(ctx.start.line, ctx.start.charPositionInLine),
            Location(ctx.stop.line, ctx.stop.charPositionInLine)
        )

        count(FeatureName.CONSTRUCTOR, ctx.toLocation())

        ctx.THROWS()?.also {
            count(FeatureName.THROWS, ctx.toLocation())
        }
    }

    override fun exitConstructorDeclaration(ctx: JavaParser.ConstructorDeclarationContext?) {
        exitMethodOrConstructor()
    }

    override fun enterLocalVariableDeclaration(ctx: JavaParser.LocalVariableDeclarationContext) {
        ctx.variableDeclarators().variableDeclarator().forEach {
            count(FeatureName.LOCAL_VARIABLE_DECLARATIONS, it.toLocation())
        }
        ctx.variableDeclarators().variableDeclarator().filter {
            it.variableInitializer() != null
        }.forEach { count(FeatureName.VARIABLE_ASSIGNMENTS, it.toLocation()) }

        ctx.variableDeclarators().variableDeclarator().filter {
            it.variableInitializer()?.arrayInitializer() != null
        }.forEach {
            count(FeatureName.ARRAY_LITERAL, it.toLocation())
        }

        val numBrackets = ctx.typeType().text.filter { it == '[' || it == ']' }.length
        when {
            numBrackets > 2 -> count(FeatureName.MULTIDIMENSIONAL_ARRAYS, ctx.toLocation())
            numBrackets > 0 -> count(FeatureName.ARRAYS, ctx.toLocation())
        }
        for (declarator in ctx.variableDeclarators().variableDeclarator()) {
            currentFeatures.features.identifierList.add(declarator.variableDeclaratorId().identifier().text)
        }
    }

    private val seenIfStarts = mutableSetOf<Int>()

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
    }

    private fun ParserRuleContext.toLocation() = Location(start.line, start.charPositionInLine)

    private fun TerminalNode.toLocation() = Location(symbol.line, symbol.charPositionInLine)

    private fun StatementContext.isPrintStatement() = statementExpression?.let {
        @Suppress("ComplexCondition")
        it.text.startsWith("System.out.println(") ||
            it.text.startsWith("System.out.print(") ||
            it.text.startsWith("System.err.println(") ||
            it.text.startsWith("System.err.print(")
    } ?: false

    @Suppress("LongMethod", "ComplexMethod", "NestedBlockDepth")
    override fun enterStatement(ctx: StatementContext) {
        if (ctx.isPrintStatement()) {
            count(FeatureName.PRINT_STATEMENTS, ctx.toLocation())
        }
        ctx.statementExpression?.also {
            if (it.bop?.text == "=") {
                count(FeatureName.VARIABLE_REASSIGNMENTS, ctx.toLocation())
            }
        }
        ctx.FOR()?.also {
            count(FeatureName.FOR_LOOPS, ctx.toLocation())
            if (ctx.forControl().enhancedForControl() != null) {
                count(FeatureName.ENHANCED_FOR, ctx.toLocation())
                count(FeatureName.LOCAL_VARIABLE_DECLARATIONS, ctx.toLocation())
            }
        }
        ctx.WHILE()?.also {
            // Only increment whileLoopCount if it's not a do-while loop
            if (ctx.DO() != null) {
                count(FeatureName.DO_WHILE_LOOPS, ctx.toLocation())
            } else {
                count(FeatureName.WHILE_LOOPS, ctx.toLocation())
            }
        }
        ctx.IF()?.also {
            // Check for else-if chains
            val outerIfStart = ctx.start.startIndex
            if (outerIfStart !in seenIfStarts) {
                // Count if block
                count(FeatureName.IF_STATEMENTS, ctx.toLocation())
                seenIfStarts += outerIfStart
                check(ctx.statement().isNotEmpty())

                if (ctx.statement().size == 2 && ctx.statement(1).block() != null) {
                    // Count else block
                    check(ctx.ELSE() != null)
                    count(FeatureName.ELSE_STATEMENTS, ctx.ELSE().toLocation())
                } else if (ctx.statement().size >= 2) {
                    var statement = ctx.statement(1)
                    while (statement != null) {
                        if (statement.IF() != null) {
                            // If statement contains an IF, it is part of a chain
                            seenIfStarts += statement.start.startIndex
                            count(FeatureName.ELSE_IF, statement.toLocation())
                        } else {
                            count(FeatureName.ELSE_STATEMENTS, statement.toLocation())
                        }
                        statement = statement.statement(1)
                    }
                }
            }
        }
        ctx.TRY()?.also {
            count(FeatureName.TRY_BLOCK, ctx.toLocation())
            ctx.finallyBlock()?.also {
                count(FeatureName.FINALLY, it.toLocation())
            }
        }
        ctx.ASSERT()?.also {
            count(FeatureName.ASSERT, ctx.toLocation())
        }
        ctx.SWITCH()?.also {
            count(FeatureName.SWITCH, ctx.toLocation())
        }
        ctx.THROW()?.also {
            count(FeatureName.THROW, ctx.toLocation())
        }
        ctx.BREAK()?.also {
            count(FeatureName.BREAK, ctx.toLocation())
        }
        ctx.CONTINUE()?.also {
            count(FeatureName.CONTINUE, ctx.toLocation())
        }
        ctx.RETURN()?.also {
            count(FeatureName.RETURN, ctx.toLocation())
        }
        // Count nested loops
        if (ctx.WHILE() != null || ctx.FOR() != null) {
            for (ctxStatement in ctx.statement()) {
                ctxStatement?.block()?.also {
                    val statement = ctxStatement.block().blockStatement()
                    for (block in statement) {
                        val blockStatement = block.statement()
                        blockStatement?.FOR()?.also {
                            count(FeatureName.NESTED_FOR, blockStatement.toLocation())
                            count(FeatureName.NESTED_LOOP, blockStatement.toLocation())
                        }
                        blockStatement?.WHILE()?.also {
                            count(FeatureName.NESTED_LOOP, blockStatement.toLocation())
                            if (block.statement().DO() != null) {
                                count(FeatureName.NESTED_DO_WHILE, blockStatement.toLocation())
                            } else {
                                count(FeatureName.NESTED_WHILE, blockStatement.toLocation())
                            }
                        }
                    }
                }
            }
        }
        if (ctx.IF() != null) {
            for (ctxStatement in ctx.statement()) {
                ctxStatement?.block()?.also {
                    val statement = ctxStatement.block().blockStatement()
                    for (block in statement) {
                        val blockStatement = block.statement()
                        blockStatement?.IF()?.also {
                            count(FeatureName.NESTED_IF, blockStatement.toLocation())
                        }
                    }
                }
            }
        }
    }

    private fun ExpressionContext.inPrintStatement(): Boolean {
        var currentParent = parent
        while (currentParent != null) {
            when (currentParent) {
                is StatementContext -> break
                is MethodCallContext -> break
            }
            currentParent = currentParent.parent
        }
        if (currentParent == null) {
            return false
        }
        return currentParent is StatementContext && currentParent.isPrintStatement()
    }

    override fun enterExpression(ctx: ExpressionContext) {
        @Suppress("SpellCheckingInspection")
        when (ctx.bop?.text) {
            "<", ">", "<=", ">=", "==", "!=" -> count(FeatureName.COMPARISON_OPERATORS, ctx.toLocation())
            "&&", "||" -> count(FeatureName.LOGICAL_OPERATORS, ctx.toLocation())
            "+", "-", "*", "/", "%" -> count(FeatureName.ARITHMETIC_OPERATORS, ctx.toLocation())
            "&", "|", "^" -> count(FeatureName.BITWISE_OPERATORS, ctx.toLocation())
            "+=", "-=", "*=", "/=", "%=" -> {
                count(FeatureName.ASSIGNMENT_OPERATORS, ctx.toLocation())
                count(FeatureName.VARIABLE_REASSIGNMENTS, ctx.toLocation())
            }

            "?" -> count(FeatureName.TERNARY_OPERATOR, ctx.toLocation())
            "instanceof" -> count(FeatureName.INSTANCEOF, ctx.toLocation())
            "." -> {
                if (ctx.identifier() != null) {
                    if (ctx.identifier().text != "length") {
                        if (!ctx.inPrintStatement()) {
                            count(FeatureName.DOT_NOTATION, ctx.toLocation())
                            count(FeatureName.DOTTED_VARIABLE_ACCESS, ctx.toLocation())
                        }
                    }
                } else {
                    if (!ctx.inPrintStatement()) {
                        count(FeatureName.DOT_NOTATION, ctx.toLocation())
                    }
                }
                if (ctx.methodCall() != null) {
                    if (!ctx.inPrintStatement()) {
                        count(FeatureName.DOTTED_METHOD_CALL, ctx.toLocation())
                    }
                    val identifier = ctx.methodCall().identifier()?.text
                    if (identifier != null) {
                        currentFeatures.features.dottedMethodList += identifier
                        if (identifier == "equals") {
                            count(FeatureName.EQUALITY, ctx.toLocation())
                        }
                    }
                }
            }
        }
        when (ctx.prefix?.text) {
            "++", "--" -> {
                count(FeatureName.UNARY_OPERATORS, ctx.toLocation())
                count(FeatureName.VARIABLE_REASSIGNMENTS, ctx.toLocation())
            }

            "~" -> count(FeatureName.BITWISE_OPERATORS, ctx.toLocation())
            "!" -> count(FeatureName.LOGICAL_OPERATORS, ctx.toLocation())
        }
        when (ctx.postfix?.text) {
            "++", "--" -> {
                count(FeatureName.UNARY_OPERATORS, ctx.toLocation())
                count(FeatureName.VARIABLE_REASSIGNMENTS, ctx.toLocation())
            }
        }
        if (ctx.text == "null") {
            count(FeatureName.NULL, ctx.toLocation())
        }
        if (ctx.bop == null) {
            if (ctx.text.contains("<<") || ctx.text.contains(">>")) {
                count(FeatureName.BITWISE_OPERATORS, ctx.toLocation())
            }
            if (ctx.expression().size != 0 && (ctx.text.contains("[") || ctx.text.contains("]"))) {
                count(FeatureName.ARRAY_ACCESS, ctx.toLocation())
            }
        }
        if (ctx.text.startsWith("(" + ctx.typeType()?.text + ")")) {
            if (ctx.typeType()?.primitiveType() != null) {
                count(FeatureName.PRIMITIVE_CASTING, ctx.toLocation())
            } else {
                count(FeatureName.CASTING, ctx.toLocation())
            }
        }
        if (ctx.bop?.text == "==" || ctx.bop?.text == "!=") {
            count(FeatureName.REFERENCE_EQUALITY, ctx.toLocation())
        }
        ctx.NEW()?.also {
            if (ctx.creator()?.arrayCreatorRest() == null && ctx.creator()?.createdName()?.text != "String") {
                count(FeatureName.NEW_KEYWORD, ctx.toLocation())
            }
        }
        ctx.primary()?.THIS()?.also {
            count(FeatureName.THIS, ctx.toLocation())
        }
        ctx.methodCall()?.SUPER()?.also {
            count(FeatureName.SUPER, ctx.toLocation())
        }
        ctx.creator()?.classCreatorRest()?.classBody()?.also {
            count(FeatureName.ANONYMOUS_CLASSES, ctx.toLocation())
        }
        ctx.lambdaExpression()?.also {
            count(FeatureName.LAMBDA_EXPRESSIONS, ctx.toLocation())
        }
    }

    override fun enterTypeType(ctx: JavaParser.TypeTypeContext) {
        ctx.primitiveType()?.also {
            currentFeatures.features.typeList.add(it.text)
        }
        ctx.classOrInterfaceType()?.also {
            currentFeatures.features.typeList.add(it.text)
        }
        ctx.classOrInterfaceType()?.identifier()?.filter {
            it.text == "String"
        }?.forEach { count(FeatureName.STRING, it.toLocation()) }

        ctx.classOrInterfaceType()?.identifier()?.filter {
            it.text == "Stream"
        }?.forEach { count(FeatureName.STREAM, it.toLocation()) }

        ctx.classOrInterfaceType()?.identifier()?.filter {
            it.text == "Comparable"
        }?.forEach { count(FeatureName.COMPARABLE, it.toLocation()) }

        ctx.classOrInterfaceType()?.identifier()?.filter {
            when (it.text) {
                "Boolean", "Byte", "Character", "Float", "Integer", "Long", "Short", "Double" -> true
                else -> false
            }
        }?.forEach { count(FeatureName.BOXING_CLASSES, it.toLocation()) }

        ctx.classOrInterfaceType()?.typeArguments()?.forEach {
            count(FeatureName.TYPE_PARAMETERS, it.toLocation())
        }

        if (ctx.text == "var" || ctx.text == "val") {
            count(FeatureName.TYPE_INFERENCE, ctx.toLocation())
        }
    }

    override fun enterClassCreatorRest(ctx: JavaParser.ClassCreatorRestContext) {
        if (ctx.classBody() != null) {
            val creator = ctx.parent as CreatorContext
            val name = "${creator.createdName().text}_${anonymousClassCount++}"
            enterClassOrInterface(
                name,
                Location(creator.start.line, creator.start.charPositionInLine),
                Location(creator.stop.line, creator.stop.charPositionInLine),
                true
            )
        }
    }

    override fun exitClassCreatorRest(ctx: JavaParser.ClassCreatorRestContext) {
        if (ctx.classBody() != null) {
            val creator = ctx.parent as CreatorContext
            val name = "${creator.createdName().text}_${anonymousClassCount - 1}"
            assert(currentFeatures.name == name)
            exitClassOrInterface()
        }
    }

    init {
        val parsedSource = source.getParsed(filename)
        // println(parsedSource.tree.format(parsedSource.parser))
        ParseTreeWalker.DEFAULT.walk(this, parsedSource.tree)
    }
}
