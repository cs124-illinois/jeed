@file:Suppress("MatchingDeclarationName", "ktlint:standard:filename", "ktlint:standard:no-wildcard-imports")

package edu.illinois.cs.cs125.jeed.core

import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParser.*
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParserBaseListener
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.antlr.v4.runtime.tree.TerminalNode
import org.jetbrains.kotlin.backend.common.pop

@Suppress("TooManyFunctions")
class KotlinMutationListener(private val parsedSource: Source.ParsedSource) : KotlinParserBaseListener() {
    val lines = parsedSource.contents.lines()
    val mutations: MutableList<Mutation> = mutableListOf()

    private val returnTypeStack: MutableList<String> = mutableListOf()
    private val currentReturnType: String?
        get() = returnTypeStack.lastOrNull()

    override fun enterFunctionDeclaration(ctx: FunctionDeclarationContext) {
        val returnType = ctx.type()?.text ?: ""
        returnTypeStack.add(returnType)
    }

    override fun exitFunctionDeclaration(ctx: FunctionDeclarationContext) {
        check(returnTypeStack.isNotEmpty()) { "Return type stack should not be empty" }
        returnTypeStack.pop()
    }

    private var inSetterOrGetter = false
    override fun enterGetter(ctx: GetterContext) {
        check(!inSetterOrGetter)
        inSetterOrGetter = true
    }

    override fun exitGetter(ctx: GetterContext) {
        check(inSetterOrGetter)
        inSetterOrGetter = false
    }

    override fun enterSetter(ctx: SetterContext) {
        check(!inSetterOrGetter)
        inSetterOrGetter = true
    }

    override fun exitSetter(ctx: SetterContext) {
        check(inSetterOrGetter)
        inSetterOrGetter = false
    }

    override fun enterFunctionBody(ctx: FunctionBodyContext) {
        if (inSetterOrGetter) {
            return
        }
        check(currentReturnType != null)
        val methodLocation = ctx.block()?.toLocation() ?: return
        val methodContents = parsedSource.contents(methodLocation)
        if (RemoveMethod.matches(methodContents, currentReturnType!!, Source.FileType.KOTLIN)) {
            mutations.add(RemoveMethod(methodLocation, methodContents, currentReturnType!!, Source.FileType.KOTLIN))
        }
    }

    override fun enterJumpExpression(ctx: JumpExpressionContext) {
        if (ctx.RETURN() != null && ctx.expression() != null && !inSetterOrGetter) {
            val returnToken = ctx.expression()
            val returnLocation = returnToken.toLocation()
            if (PrimitiveReturn.matches(returnToken.text, currentReturnType!!, Source.FileType.KOTLIN)) {
                mutations.add(
                    PrimitiveReturn(
                        returnLocation,
                        parsedSource.contents(returnLocation),
                        Source.FileType.KOTLIN,
                    ),
                )
            }
            if (TrueReturn.matches(returnToken.text, currentReturnType!!)) {
                mutations.add(TrueReturn(returnLocation, parsedSource.contents(returnLocation), Source.FileType.KOTLIN))
            }
            if (FalseReturn.matches(returnToken.text, currentReturnType!!)) {
                mutations.add(
                    FalseReturn(
                        returnLocation,
                        parsedSource.contents(returnLocation),
                        Source.FileType.KOTLIN,
                    ),
                )
            }
            if (NullReturn.matches(returnToken.text, currentReturnType!!, Source.FileType.KOTLIN)) {
                mutations.add(NullReturn(returnLocation, parsedSource.contents(returnLocation), Source.FileType.KOTLIN))
            }
        }
        ctx.BREAK()?.also {
            mutations.add(
                SwapBreakContinue(
                    ctx.toLocation(),
                    parsedSource.contents(ctx.toLocation()),
                    Source.FileType.KOTLIN,
                ),
            )
        }
        ctx.CONTINUE()?.also {
            mutations.add(
                SwapBreakContinue(
                    ctx.toLocation(),
                    parsedSource.contents(ctx.toLocation()),
                    Source.FileType.KOTLIN,
                ),
            )
        }
    }

    override fun enterStatement(ctx: StatementContext) {
        val statementLocation = ctx.toLocation()
        if (ctx.start.text == "assert" ||
            ctx.start.text == "check" ||
            ctx.start.text == "require"
        ) {
            mutations.add(
                RemoveRuntimeCheck(
                    statementLocation,
                    parsedSource.contents(statementLocation),
                    Source.FileType.KOTLIN,
                ),
            )
        }
        if (ctx.declaration() == null) {
            val skipIfStatement = try {
                ((ctx.children.first() as ExpressionContext).children.first() as PrimaryExpressionContext).children.first() is IfExpressionContext
            } catch (_: Exception) {
                false
            }
            val skipWhenEntry = try {
                (ctx.parent as ControlStructureBodyContext).parent is WhenEntryContext
            } catch (_: Exception) {
                false
            }
            if (!skipIfStatement && !skipWhenEntry) {
                mutations.add(
                    RemoveStatement(
                        statementLocation,
                        parsedSource.contents(statementLocation),
                        Source.FileType.KOTLIN,
                    ),
                )
            }
        }
    }

    private var insideAnnotation = false
    override fun enterAnnotation(ctx: AnnotationContext?) {
        insideAnnotation = true
    }

    override fun exitAnnotation(ctx: AnnotationContext?) {
        insideAnnotation = false
    }

    private fun ParserRuleContext.toLocation() = Mutation.Location(
        start.startIndex,
        stop.stopIndex,
        lines.filterIndexed { index, _ -> index >= start.line - 1 && index <= stop.line - 1 }
            .joinToString("\n"),
        start.line,
        stop.line,
    )

    private fun List<TerminalNode>.toLocation() = Mutation.Location(
        first().symbol.startIndex,
        last().symbol.stopIndex,
        lines.filterIndexed { index, _ -> index >= first().symbol.line - 1 && index <= last().symbol.line - 1 }
            .joinToString("\n"),
        first().symbol.line,
        last().symbol.line,
    )

    override fun enterLineStringContent(ctx: LineStringContentContext) {
        if (insideAnnotation) {
            return
        }
        ctx.toLocation().also { location ->
            val contents = parsedSource.contents(location)
            mutations.addStringMutations(location, contents, Source.FileType.KOTLIN, false)
        }
    }

    override fun enterMultiLineStringContent(ctx: MultiLineStringContentContext) {
        if (insideAnnotation) {
            return
        }
        ctx.toLocation().also { location ->
            val contents = parsedSource.contents(location)
            mutations.addStringMutations(location, contents, Source.FileType.KOTLIN, false)
        }
    }

    override fun enterLineStringLiteral(ctx: LineStringLiteralContext) {
        if (insideAnnotation) {
            return
        }
        if (ctx.lineStringContent().isEmpty() && ctx.lineStringExpression().isEmpty()) {
            ctx.toLocation().also { location ->
                val contents = parsedSource.contents(location)
                mutations.addStringMutations(location, contents, Source.FileType.KOTLIN)
            }
        }
    }

    private fun LiteralConstantContext.checkDivision(): Boolean {
        var current: RuleContext? = this
        while (current != null) {
            if (current is ExpressionContext && current.multiplicativeOperator()
                    ?.let { it.DIV() != null || it.MOD() != null } == true
            ) {
                return current.expression(1)?.text?.trimParentheses() == text
            }
            if (current is AssignmentContext && current.assignmentAndOperator()
                    ?.let { it.MOD_ASSIGNMENT() != null || it.DIV_ASSIGNMENT() != null } == true
            ) {
                return current.fullExpression()?.text?.trimParentheses() == text
            }
            current = current.parent
        }
        return false
    }

    @Suppress("ComplexMethod")
    override fun enterLiteralConstant(ctx: LiteralConstantContext) {
        if (insideAnnotation) {
            return
        }
        ctx.BooleanLiteral()?.also {
            ctx.toLocation().also { location ->
                mutations.add(BooleanLiteral(location, parsedSource.contents(location), Source.FileType.KOTLIN))
            }
        }
        val isNegative = try {
            (((ctx.parent as PrimaryExpressionContext).parent as ExpressionContext).parent as ExpressionContext).unaryPrefix(
                0,
            ).text == "-"
        } catch (_: Exception) {
            false
        }

        val isDivision = try {
            ctx.checkDivision()
        } catch (_: Exception) {
            false
        }

        ctx.IntegerLiteral()?.also {
            ctx.toLocation().also { location ->
                val content = parsedSource.contents(location)
                mutations.add(
                    NumberLiteral(
                        location,
                        content,
                        Source.FileType.KOTLIN,
                        base = 10,
                        isNegative = isNegative,
                        isDivision = isDivision,
                    ),
                )
                if (NumberLiteralTrim.matches(content, base = 10, isNegative = isNegative, isDivision = isDivision)) {
                    mutations.add(
                        NumberLiteralTrim(
                            location,
                            content,
                            Source.FileType.KOTLIN,
                            base = 10,
                            isNegative = isNegative,
                            isDivision = isDivision,
                        ),
                    )
                }
            }
        }
        @Suppress("MagicNumber")
        ctx.HexLiteral()?.also {
            ctx.toLocation().also { location ->
                val content = parsedSource.contents(location)
                mutations.add(
                    NumberLiteral(
                        location,
                        content,
                        Source.FileType.KOTLIN,
                        base = 16,
                        isNegative = isNegative,
                        isDivision = isDivision,
                    ),
                )
                if (NumberLiteralTrim.matches(content, 16, isNegative, isDivision)) {
                    mutations.add(
                        NumberLiteralTrim(
                            location,
                            content,
                            Source.FileType.KOTLIN,
                            base = 16,
                            isNegative = isNegative,
                            isDivision = isDivision,
                        ),
                    )
                }
            }
        }
        ctx.BinLiteral()?.also {
            ctx.toLocation().also { location ->
                val content = parsedSource.contents(location)
                mutations.add(
                    NumberLiteral(
                        location,
                        content,
                        Source.FileType.KOTLIN,
                        base = 2,
                        isNegative = isNegative,
                        isDivision = isDivision,
                    ),
                )
                if (NumberLiteralTrim.matches(content, 2, isNegative, isDivision)) {
                    mutations.add(
                        NumberLiteralTrim(
                            location,
                            content,
                            Source.FileType.KOTLIN,
                            base = 2,
                            isNegative = isNegative,
                            isDivision = isDivision,
                        ),
                    )
                }
            }
        }
        ctx.CharacterLiteral()?.also {
            ctx.toLocation().also { location ->
                mutations.add(CharLiteral(location, parsedSource.contents(location), Source.FileType.KOTLIN))
            }
        }
        // reals are doubles and floats
        ctx.RealLiteral()?.also {
            ctx.toLocation().also { location ->
                val content = parsedSource.contents(location)
                mutations.add(
                    NumberLiteral(
                        location,
                        content,
                        Source.FileType.KOTLIN,
                        isDivision = isDivision,
                        isNegative = isNegative,
                    ),
                )
                if (NumberLiteralTrim.matches(content, base = 10, isNegative = isNegative, isDivision = isDivision)) {
                    mutations.add(
                        NumberLiteralTrim(
                            location,
                            content,
                            Source.FileType.KOTLIN,
                            base = 10,
                            isNegative = isNegative,
                            isDivision = isDivision,
                        ),
                    )
                }
            }
        }
        ctx.LongLiteral()?.also {
            ctx.toLocation().also { location ->
                val content = parsedSource.contents(location)
                mutations.add(
                    NumberLiteral(
                        location,
                        content,
                        Source.FileType.KOTLIN,
                        base = 10,
                        isNegative = isNegative,
                        isDivision = isDivision,
                    ),
                )
                if (NumberLiteralTrim.matches(content, base = 10, isNegative = isNegative, isDivision = isDivision)) {
                    mutations.add(
                        NumberLiteralTrim(
                            location,
                            content,
                            Source.FileType.KOTLIN,
                            base = 10,
                            isNegative = isNegative,
                            isDivision = isDivision,
                        ),
                    )
                }
            }
        }
    }

    override fun enterIfExpression(ctx: IfExpressionContext) {
        val conditionalLocation = ctx.expression().toLocation()
        mutations.add(NegateIf(conditionalLocation, parsedSource.contents(conditionalLocation), Source.FileType.KOTLIN))
        if (ctx.ELSE() == null) {
            mutations.add(RemoveIf(ctx.toLocation(), parsedSource.contents(ctx.toLocation()), Source.FileType.KOTLIN))
        } else {
            if (ctx.controlStructureBody(1) != null) {
                if (ctx.controlStructureBody(1).statement()?.expression() == null) { // not else if, just else
                    val location = Mutation.Location(
                        ctx.ELSE().symbol.startIndex,
                        ctx.controlStructureBody(1).stop.stopIndex,
                        lines.filterIndexed { index, _ ->
                            index >= ctx.ELSE().symbol.line - 1 && index <= ctx.controlStructureBody(1).stop.line - 1
                        }.joinToString("\n"),
                        ctx.ELSE().symbol.line,
                        ctx.controlStructureBody(1).stop.line,
                    )
                    mutations.add(RemoveIf(location, parsedSource.contents(location), Source.FileType.KOTLIN))
                    mutations.add(
                        RemoveIf(
                            ctx.toLocation(),
                            parsedSource.contents(ctx.toLocation()),
                            Source.FileType.KOTLIN,
                        ),
                    )
                } else { // is an else if
                    val location = Mutation.Location(
                        ctx.start.startIndex,
                        ctx.ELSE().symbol.stopIndex,
                        lines.filterIndexed { index, _ ->
                            index >= ctx.start.line - 1 && index <= ctx.ELSE().symbol.line - 1
                        }.joinToString("\n"),
                        ctx.start.line,
                        ctx.ELSE().symbol.line,
                    )
                    mutations.add(RemoveIf(location, parsedSource.contents(location), Source.FileType.KOTLIN))
                }
            }
        }
    }

    private var loopDepth = 0
    private val loopBlockDepths = mutableListOf<Int>()
    private var continueUntil = 0

    private fun StatementContext.isContinue() = expression()?.primaryExpression()?.jumpExpression()?.CONTINUE() != null ||
        expression()?.primaryExpression()?.jumpExpression()?.CONTINUE_AT() != null

    private fun StatementContext.isIfTry() = expression()?.primaryExpression()?.ifExpression() != null ||
        expression()?.primaryExpression()?.tryExpression() != null

    private fun ControlStructureBodyContext.setContinueUntil() {
        continueUntil = 0
        if (block()?.statements()?.isEmpty != false) {
            return
        }
        var sawIf = false
        block().statements().statement().reversed().filter {
            it.declaration() != null || it.assignment() != null || it.expression() != null
        }.find {
            val result = if (it.declaration() != null || it.assignment() != null) {
                true
            } else if (sawIf && it.isIfTry()) {
                true
            } else {
                !it.isContinue() && !it.isIfTry()
            }
            if (it.isIfTry()) {
                sawIf = true
            }
            result
        }?.also {
            continueUntil = it.toLocation().end
        }
    }

    override fun enterLoopStatement(ctx: LoopStatementContext) {
        val location = ctx.toLocation()
        ctx.doWhileStatement()?.also {
            loopDepth++
            loopBlockDepths.add(0, 0)
            ctx.doWhileStatement().controlStructureBody()?.setContinueUntil()
        }
        ctx.forStatement()?.also {
            loopDepth++
            loopBlockDepths.add(0, 0)
            ctx.forStatement().controlStructureBody()?.setContinueUntil()
        }
        ctx.whileStatement()?.also {
            loopDepth++
            loopBlockDepths.add(0, 0)
            ctx.whileStatement().controlStructureBody()?.setContinueUntil()
        }
        mutations.add(RemoveLoop(location, parsedSource.contents(location), Source.FileType.KOTLIN))
    }

    override fun exitLoopStatement(ctx: LoopStatementContext) {
        ctx.doWhileStatement()?.also {
            loopDepth--
            check(loopBlockDepths.removeAt(0) == 0)
        }
        ctx.forStatement()?.also {
            loopDepth--
            check(loopBlockDepths.removeAt(0) == 0)
        }
        ctx.whileStatement()?.also {
            loopDepth--
            check(loopBlockDepths.removeAt(0) == 0)
        }
    }

    override fun enterBlock(ctx: BlockContext?) {
        if (loopDepth > 0) {
            loopBlockDepths[0]++
        }
    }

    override fun exitBlock(ctx: BlockContext) {
        if (loopDepth > 0) {
            val rightCurl = ctx.RCURL()
            val rightCurlLocation = listOf(rightCurl, rightCurl).toLocation()
            val location = rightCurl.toLocation()
            check(location.startLine == location.endLine)
            val previousLine = lines[location.startLine - 2].trim()
            if (previousLine != "break") {
                mutations.add(
                    AddBreak(
                        rightCurlLocation,
                        parsedSource.contents(rightCurlLocation),
                        Source.FileType.KOTLIN,
                    ),
                )
            }
            if (loopBlockDepths[0] > 1 && previousLine != "continue" && location.start <= continueUntil) {
                mutations.add(
                    AddContinue(
                        rightCurlLocation,
                        parsedSource.contents(rightCurlLocation),
                        Source.FileType.KOTLIN,
                    ),
                )
            }
        }
        if (loopDepth > 0) {
            loopBlockDepths[0]--
        }
    }

    override fun enterDoWhileStatement(ctx: DoWhileStatementContext) {
        val location = ctx.expression().toLocation()
        mutations.add(NegateWhile(location, parsedSource.contents(location), Source.FileType.KOTLIN))
    }

    override fun enterWhileStatement(ctx: WhileStatementContext) {
        val location = ctx.expression().toLocation()
        val rightCurl = ctx.controlStructureBody().block()?.RCURL()
        if (rightCurl != null) {
            val rightCurlLocation = listOf(rightCurl, rightCurl).toLocation()
            mutations.add(AddBreak(rightCurlLocation, parsedSource.contents(rightCurlLocation), Source.FileType.KOTLIN))
        }
        mutations.add(NegateWhile(location, parsedSource.contents(location), Source.FileType.KOTLIN))
    }

    override fun enterTryExpression(ctx: TryExpressionContext) {
        val location = ctx.toLocation()
        mutations.add(RemoveTry(location, parsedSource.contents(location), Source.FileType.KOTLIN))
    }

    override fun enterPrefixUnaryOperator(ctx: PrefixUnaryOperatorContext) {
        if (IncrementDecrement.matches(ctx.text)) {
            mutations.add(IncrementDecrement(ctx.toLocation(), ctx.text, Source.FileType.KOTLIN))
        }
        if (InvertNegation.matches(ctx.text)) {
            mutations.add(InvertNegation(ctx.toLocation(), ctx.text, Source.FileType.KOTLIN))
        }
    }

    override fun enterPostfixUnaryOperator(ctx: PostfixUnaryOperatorContext) {
        if (IncrementDecrement.matches(ctx.text)) {
            mutations.add(IncrementDecrement(ctx.toLocation(), ctx.text, Source.FileType.KOTLIN))
        }
    }

    override fun enterComparisonOperator(ctx: ComparisonOperatorContext) {
        if (ConditionalBoundary.matches(ctx.text)) {
            mutations.add(ConditionalBoundary(ctx.toLocation(), ctx.text, Source.FileType.KOTLIN))
        }
        if (NegateConditional.matches(ctx.text)) {
            mutations.add(NegateConditional(ctx.toLocation(), ctx.text, Source.FileType.KOTLIN))
        }
    }

    override fun enterMultiplicativeOperator(ctx: MultiplicativeOperatorContext) {
        if (MutateMath.matches(ctx.text)) {
            mutations.add(MutateMath(ctx.toLocation(), ctx.text, Source.FileType.KOTLIN))
        }
    }

    override fun enterAdditiveOperator(ctx: AdditiveOperatorContext) {
        if (PlusToMinus.matches(ctx.text)) {
            mutations.add(PlusToMinus(ctx.toLocation(), ctx.text, Source.FileType.KOTLIN))
        }
        if (MutateMath.matches(ctx.text)) {
            mutations.add(MutateMath(ctx.toLocation(), ctx.text, Source.FileType.KOTLIN))
        }
    }

    override fun enterEqualityOperator(ctx: EqualityOperatorContext) {
        if (NegateConditional.matches(ctx.text)) {
            mutations.add(NegateConditional(ctx.toLocation(), ctx.text, Source.FileType.KOTLIN))
        }
        if (ctx.text == "==") {
            mutations.add(
                ChangeEquals(
                    ctx.toLocation(),
                    parsedSource.contents(ctx.toLocation()),
                    Source.FileType.KOTLIN,
                    "==",
                ),
            )
        }
        if (ctx.text == "===") {
            mutations.add(
                ChangeEquals(
                    ctx.toLocation(),
                    parsedSource.contents(ctx.toLocation()),
                    Source.FileType.KOTLIN,
                    "===",
                ),
            )
        }
    }

    override fun enterExpression(ctx: ExpressionContext) {
        ctx.DISJ()?.also {
            if (SwapAndOr.matches(ctx.DISJ().text)) {
                mutations.add(SwapAndOr(ctx.DISJ().toLocation(), ctx.DISJ().text, Source.FileType.KOTLIN))
                val (frontLocation, backLocation) = ctx.locationPair()
                mutations.add(RemoveAndOr(frontLocation, parsedSource.contents(frontLocation), Source.FileType.KOTLIN))
                mutations.add(RemoveAndOr(backLocation, parsedSource.contents(backLocation), Source.FileType.KOTLIN))
            }
        }
        ctx.CONJ()?.also {
            if (SwapAndOr.matches(ctx.CONJ().text)) {
                mutations.add(SwapAndOr(ctx.CONJ().toLocation(), ctx.CONJ().text, Source.FileType.KOTLIN))
                val (frontLocation, backLocation) = ctx.locationPair()
                mutations.add(RemoveAndOr(frontLocation, parsedSource.contents(frontLocation), Source.FileType.KOTLIN))
                mutations.add(RemoveAndOr(backLocation, parsedSource.contents(backLocation), Source.FileType.KOTLIN))
            }
        }
        ctx.additiveOperator()?.also {
            val (frontLocation, backLocation) = ctx.locationPair()
            mutations.add(RemovePlus(frontLocation, parsedSource.contents(frontLocation), Source.FileType.KOTLIN))
            mutations.add(RemovePlus(backLocation, parsedSource.contents(backLocation), Source.FileType.KOTLIN))
            val text = parsedSource.contents(ctx.expression(1).toLocation())
            if (text == "1") {
                mutations.add(
                    PlusOrMinusOneToZero(
                        ctx.expression(1).toLocation(),
                        parsedSource.contents(ctx.expression(1).toLocation()),
                        Source.FileType.KOTLIN,
                    ),
                )
            }
        }
        if (ctx.postfixUnarySuffix().isNotEmpty()) {
            val identifier = ctx.expression().getOrNull(0)?.primaryExpression()?.simpleIdentifier()
            val arguments = ctx.postfixUnarySuffix()?.firstOrNull()?.callSuffix()?.valueArguments() ?: return
            if ((
                    identifier?.text == "arrayOf" ||
                        identifier?.text == "listOf" ||
                        identifier?.text?.endsWith("ArrayOf") == true
                    ) &&
                arguments.valueArgument().size > 1
            ) {
                val start = arguments.valueArgument().first().toLocation()
                val end = arguments.valueArgument().last().toLocation()
                val location = Mutation.Location(
                    start.start,
                    end.end,
                    lines.filterIndexed { index, _ -> index >= start.startLine - 1 && index <= end.endLine - 1 }
                        .joinToString("\n"),
                    start.startLine,
                    end.endLine,
                )
                val contents = parsedSource.contents(location)
                val parts = arguments.valueArgument().map {
                    parsedSource.contents(it.toLocation())
                }
                mutations.add(ModifyArrayLiteral(location, contents, Source.FileType.KOTLIN, parts))
            }
        }
    }

    override fun enterNavigationSuffix(ctx: NavigationSuffixContext) {
        if (ctx.memberAccessOperator()?.DOT() == null || ctx.simpleIdentifier() == null) {
            return
        }
        if (ctx.simpleIdentifier().text == "size" || ctx.simpleIdentifier().text == "length") {
            mutations.add(
                ChangeLengthAndSize(
                    ctx.simpleIdentifier().toLocation(),
                    parsedSource.contents(ctx.simpleIdentifier().toLocation()),
                    Source.FileType.KOTLIN,
                ),
            )
        }
    }

    private fun ExpressionContext.locationPair(): Pair<Mutation.Location, Mutation.Location> {
        val front = expression(0)
        val back = expression(1)
        val frontLocation = Mutation.Location(
            front.start.startIndex,
            back.start.startIndex - 1,
            lines
                .filterIndexed { index, _ -> index >= front.start.line - 1 && index <= back.start.line - 1 }
                .joinToString("\n"),
            front.start.line,
            back.start.line,
        )
        val backLocation = Mutation.Location(
            front.stop.stopIndex + 1,
            back.stop.stopIndex,
            lines
                .filterIndexed { index, _ -> index >= front.stop.line - 1 && index <= back.stop.line - 1 }
                .joinToString("\n"),
            front.start.line,
            back.stop.line,
        )
        return Pair(frontLocation, backLocation)
    }

    private fun TerminalNode.toLocation() = Mutation.Location(symbol.startIndex, symbol.stopIndex, lines[symbol.line - 1], symbol.line, symbol.line)

    init {
        // println(parsedSource.tree.format(parsedSource.parser))
        ParseTreeWalker.DEFAULT.walk(this, parsedSource.tree)
        check(loopDepth == 0)
        check(loopBlockDepths.isEmpty())
    }
}
