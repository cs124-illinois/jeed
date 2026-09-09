// Copyright 2019 Pinterest, Inc.
// Copyright 2016-2019 Stanley Shyiko
//
// Licensed under the MIT License. This file is derived from ktlint, which is distributed under the
// same license Jeed is; see https://github.com/pinterest/ktlint/blob/1.8.0/LICENSE for the full
// text of the notice that this copy carries with it.
//
// A copy of ktlint 1.8.0's
// ktlint-rule-engine-core/src/main/kotlin/com/pinterest/ktlint/rule/engine/core/api/KtlintKotlinCompiler.kt,
// verbatim apart from the way the CompilerConfiguration is built, the opt-in that change requires,
// and the MockProject reference noted below. It lives in ktlint's own package so that it shadows
// the copy inside
// ktlint-rule-engine-core-1.8.0.jar: Jeed's own classes precede dependency jars on the classpath,
// so this object, the KtlintKotlinCompilerKt file facade and the private helper classes below are
// the ones that load.
//
// Why: ktlint 1.8.0 builds its PSI file factory from a bare CompilerConfiguration(). Kotlin 2.4.20
// changed KotlinCoreEnvironment.configureProjectEnvironment to read the compiler extensions off the
// configuration, and a configuration that did not come from CompilerConfiguration.create() has no
// extension storage registered, so class initialization dies with
//
//     java.lang.ExceptionInInitializerError
//     Caused by: java.lang.IllegalStateException: Extensions storage is not registered
//
// and every later call fails with NoClassDefFoundError: Could not initialize class
// com.pinterest.ktlint.rule.engine.core.api.KtlintKotlinCompiler. Nothing in Jeed can reach the
// configuration ktlint constructs, so the file it is constructed in is replaced instead.
//
// This is temporary. Delete this file as soon as a ktlint release is built against Kotlin 2.4.20 or
// later—the `kotlin =` line in ktlint's gradle/libs.versions.toml says which version a release
// used—and let the copy in the jar load again. TestKtLint asserts that this copy is the one being
// loaded, so it fails as soon as the shadow stops winning.

package com.pinterest.ktlint.rule.engine.core.api

import com.pinterest.ktlint.rule.engine.core.api.ElementType.BLOCK
import com.pinterest.ktlint.rule.engine.core.api.ElementType.SCRIPT
import com.pinterest.ktlint.rule.engine.core.api.ElementType.SCRIPT_INITIALIZER
import com.pinterest.ktlint.rule.engine.core.util.cast
import org.jetbrains.kotlin.CoreEnvironmentDeprecation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.create
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.mock.MockComponentManager
import org.jetbrains.kotlin.com.intellij.openapi.diagnostic.DefaultLogger
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.kotlin.com.intellij.pom.PomModel
import org.jetbrains.kotlin.com.intellij.pom.PomModelAspect
import org.jetbrains.kotlin.com.intellij.pom.PomTransaction
import org.jetbrains.kotlin.com.intellij.pom.impl.PomTransactionBase
import org.jetbrains.kotlin.com.intellij.pom.tree.TreeAspect
import org.jetbrains.kotlin.com.intellij.psi.PsiFile
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinLanguage
import sun.reflect.ReflectionFactory
import org.jetbrains.kotlin.com.intellij.openapi.diagnostic.Logger as DiagnosticLogger

private const val IDEA_HOME_PATH_PROPERTY = "idea.home.path"
private const val IDEA_CONFIG_PATH_PROPERTY = "idea.config.path"

/**
 * Embedded Kotlin Compiler configured for use by Ktlint.
 */
public object KtlintKotlinCompiler {
    private val psiFileFactory = initPsiFileFactory()

    /**
     * Create a PSI file with name [psiFileName] and content [text].
     */
    public fun createPsiFileFromText(
        psiFileName: String,
        text: String,
    ): PsiFile = psiFileFactory.createFileFromText(psiFileName, KotlinLanguage.INSTANCE, text)

    /**
     * Create the AST for a given piece of code.
     */
    // For a code snippet which is not necessarily compilable if it was compiled as a standalone file, it is better to compile it as
    // kotlin script.
    public fun createASTNodeFromText(text: String): ASTNode? = createPsiFileFromText("File.kts", text)
        .node
        .findChildByType(SCRIPT)
        ?.findChildByType(BLOCK)
        ?.let { it.findChildByType(SCRIPT_INITIALIZER) ?: it }
}

/**
 * Initialize Kotlin Lexer.
 */
@OptIn(CoreEnvironmentDeprecation::class)
private fun initPsiFileFactory(): PsiFileFactory {
    DiagnosticLogger.setFactory(LoggerFactory::class.java)

    // Upstream builds this as `CompilerConfiguration().apply { put(MESSAGE_COLLECTOR_KEY, MessageCollector.NONE) }`, which leaves the
    // extensions storage unregistered. This is the only behavioural change in this file.
    val compilerConfiguration = CompilerConfiguration.create(messageCollector = MessageCollector.NONE)

    val disposable = Disposer.newDisposable()
    // When running ktlint via the ktlint-intellij-plugin in IntelliJ IDEA, an exception is thrown whenever certain properties are not set
    // (https://github.com/nbadal/ktlint-intellij-plugin/issues/614). When initializing the embedded kotlin compiler while running it within
    // a process in IntelliJ IDEA those settings need to have a non-null value.
    val ideaHomePath = System.getProperties().setProperty(IDEA_HOME_PATH_PROPERTY, "/ktlint/$IDEA_HOME_PATH_PROPERTY") as String?
    val ideaConfigPath = System.getProperties().setProperty(IDEA_CONFIG_PATH_PROPERTY, "/ktlint/$IDEA_CONFIG_PATH_PROPERTY") as String?
    try {
        val project =
            KotlinCoreEnvironment
                .createForProduction(
                    disposable,
                    compilerConfiguration,
                    EnvironmentConfigFiles.JVM_CONFIG_FILES,
                ).project
                // Upstream is `.cast<MockProject>().apply { registerFormatPomModel() }`. MockProject cannot be resolved against
                // kotlin-compiler-embeddable 2.4.20: its Kotlin metadata names ComponentManagerEx as a supertype, and that class is not
                // shaded into the jar, so calling anything on a MockProject is MISSING_DEPENDENCY_SUPERCLASS, which is not suppressible.
                // MockComponentManager is MockProject's Java superclass and declares registerService, so the registration goes through it.
                .apply { cast<MockComponentManager>().registerFormatPomModel() }

        return PsiFileFactory.getInstance(project)
    } finally {
        // Dispose explicitly to (possibly) prevent memory leak
        // https://discuss.kotlinlang.org/t/memory-leak-in-kotlincoreenvironment-and-kotlintojvmbytecodecompiler/21950
        // https://youtrack.jetbrains.com/issue/KT-47044
        disposable.dispose()
        // Restore the system settings
        if (ideaHomePath == null) {
            System.clearProperty(IDEA_HOME_PATH_PROPERTY)
        } else {
            System.setProperty(IDEA_HOME_PATH_PROPERTY, ideaHomePath)
        }
        if (ideaConfigPath == null) {
            System.clearProperty(IDEA_CONFIG_PATH_PROPERTY)
        } else {
            System.setProperty(IDEA_CONFIG_PATH_PROPERTY, ideaConfigPath)
        }
    }
}

/**
 * Do not print anything to the stderr when lexer is unable to match input.
 */
private class LoggerFactory : DiagnosticLogger.Factory {
    override fun getLoggerInstance(p: String): DiagnosticLogger = object : DefaultLogger(null) {
        override fun warn(
            message: String?,
            t: Throwable?,
        ) {}

        override fun error(
            message: String?,
            vararg details: String?,
        ) {}
    }
}

private fun MockComponentManager.registerFormatPomModel() {
    registerService(PomModel::class.java, FormatPomModel())
}

private class FormatPomModel :
    UserDataHolderBase(),
    PomModel {
    override fun runTransaction(transaction: PomTransaction) {
        (transaction as PomTransactionBase).run()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : PomModelAspect> getModelAspect(aspect: Class<T>): T? {
        if (aspect == TreeAspect::class.java) {
            // using approach described in https://git.io/vKQTo due to the magical bytecode of TreeAspect
            // (check constructor signature and compare it to the source)
            // (org.jetbrains.kotlin:kotlin-compiler-embeddable:1.0.3)
            val constructor =
                ReflectionFactory
                    .getReflectionFactory()
                    .newConstructorForSerialization(
                        aspect,
                        Any::class.java.getDeclaredConstructor(*arrayOfNulls<Class<*>>(0)),
                    )
            return constructor.newInstance() as T
        }
        return null
    }
}
