fun foo() {
  when (a) {
    a.foo -> a
    a.foo() -> a
    a.foo<T> -> a
    a.foo<T>(a) -> a
    a.foo<T>(a, d) -> a
    // a.{bar} -> a // not valid Kotlin
    // a.{!bar} -> a // not valid Kotlin
    // a.{ -> !bar} -> a // not valid Kotlin
    else -> a
  }
}


