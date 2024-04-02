package edu.illinois.cs.cs125.jeed.core

import com.google.googlejavaformat.java.Formatter

fun String.googleFormat(): String = Formatter().formatSource(this)

fun Source.googleFormat(): Source {
    check(type == Source.SourceType.JAVA) { "Can only google format Java sources" }
    check(this !is Snippet) { "Can't reformat snippets" }
    return Source(sources.mapValues { (_, contents) -> contents.googleFormat() })
}
