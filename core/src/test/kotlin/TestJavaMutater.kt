@file:Suppress("SpellCheckingInspection")

package edu.illinois.cs.cs125.jeed.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import kotlin.random.Random

@Suppress("LargeClass")
class TestJavaMutater :
    StringSpec({
        "it should find boolean literals to mutate" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    boolean first = true;
    boolean second = false;
  }
}""",
            ).checkMutations<BooleanLiteral> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "true", "false")
                mutations[1].check(contents, "false", "true")
            }
        }
        "it should find char literals to mutate" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    char first = 'a';
    char second = '!';
  }
}""",
            ).checkMutations<CharLiteral> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "'a'")
                mutations[1].check(contents, "'!'")
            }
        }
        "it should find string literals to mutate" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    System.out.println("Hello, world!");
    String s = "";
  }
}""",
            ).checkMutations<StringLiteral> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "\"Hello, world!\"").also {
                    it shouldMatch ".*println\\(\".*".toRegex(RegexOption.DOT_MATCHES_ALL)
                }
                mutations[1].check(contents, "\"\"")
            }
        }
        "it should find lookalike string literals to mutate" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    System.out.println("Hello, world");
    String s = "";
  }
}""",
            ).checkMutations<StringLiteralLookalike> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations[0].check(contents, "\"Hello, world\"").also {
                    it shouldMatch ".*0.*".toRegex(RegexOption.DOT_MATCHES_ALL)
                }
            }
        }
        "it should find lookalike string cases to mutate" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    System.out.println("Hello, world");
    String t = "124 0124";
    String w = "67 test";
    String s = "";
  }
}""",
            ).checkMutations<StringLiteralCase> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "\"Hello, world\"").also {
                    it shouldMatch ".*ello.*".toRegex(RegexOption.DOT_MATCHES_ALL)
                    it shouldMatch ".*orld.*".toRegex(RegexOption.DOT_MATCHES_ALL)
                }
            }
        }
        "it should find string literals to trim" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    System.out.println("Hello, world!");
    String s = "";
  }
}""",
            ).checkMutations<StringLiteralTrim> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations[0].check(contents, """"Hello, world!"""".trimMargin())
            }
        }
        "it should not mutate string escapes" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    System.out.println("\\\\");
  }
}""",
            ).checkMutations<StringLiteral> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations[0].check(contents, """"\\\\"""").also {
                    it shouldMatch ".*\\\\.*".toRegex(RegexOption.DOT_MATCHES_ALL)
                }
            }
            Source.fromJava(
                """
public class Example {
  public static void example() {
    System.out.println("\\t\\");
  }
}""",
            ).checkMutations<StringLiteral> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations[0].check(contents, """"\\t\\"""").also {
                    it shouldMatch ".*\\\\.*".toRegex(RegexOption.DOT_MATCHES_ALL)
                }
            }
            Source.fromJava(
                """
public class Example {
  public static void example() {
    System.out.println("\\t\\");
  }
}""",
            ).checkMutations<StringLiteral> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations[0].check(contents, """"\\t\\"""").also {
                    it shouldMatch ".*\\\\.*".toRegex(RegexOption.DOT_MATCHES_ALL)
                }
            }
            Source.fromJava(
                """
public class Example {
  public static void example() {
    System.out.println("\\t\\te");
  }
}""",
            ).checkMutations<StringLiteral> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations[0].check(contents, """"\\t\\te"""").also {
                    it shouldMatch """.*println\("\\\\t\\\\t.*""".toRegex(RegexOption.DOT_MATCHES_ALL)
                }
            }
        }
        "it should find number literals to mutate" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    System.out.println(1234);
    float f = 1.01f;
  }
}""",
            ).checkMutations<NumberLiteral> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "1234")
                mutations[1].check(contents, "1.01f")
            }
        }
        "it should find number literals to trim" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    System.out.println(1234);
    float f = 1.0f;
    long t = 10L;
    double d = 0.0;
    int first = 01234;
    int second = 0x101;
    int third = 0b101010;
  }
}""",
            ).checkMutations<NumberLiteralTrim> { mutations, contents ->
                mutations shouldHaveSize 4
                mutations[0].check(contents, "1234")
            }
        }
        "it should not trim to zeroes" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    int first = 1000;
    int second = 1;
    int third = 0;
    int fourth = -10;
  }
}""",
            ).checkMutations<NumberLiteralTrim> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].shouldNotBe(contents, "1000", "000")
                mutations[0].estimatedCount shouldBe 1
                mutations[1].shouldNotBe(contents, "10", "0")
                mutations[1].estimatedCount shouldBe 1
            }
        }
        "it should not mutate negatives to zeroes" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    int first = -1;
  }
}""",
            ).checkMutations<NumberLiteral> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations[0].shouldNotBe(contents, "1", "0")
                mutations[0].estimatedCount shouldBe 1
            }
        }
        "it should find increments and decrements to mutate" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    int i = 0;
    int j = 1;
    i++;
    --j;
  }
}""",
            ).checkMutations<IncrementDecrement> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "++", "--")
                mutations[1].check(contents, "--", "++")
            }
        }
        "it should find negatives to invert" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    int i = 0;
    int j = -1;
    int k = -j;
  }
}""",
            ).checkMutations<InvertNegation> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "-", "")
                mutations[1].check(contents, "-", "")
            }
        }
        "it should find math to mutate" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    int i = 0;
    int j = 1;
    int k = i + j;
    k = i - j;
    k = i * j;
    k = i / j;
    int l = i % 10;
    l = i & j;
    l = j | i;
    l = j ^ i;
    l = i << 2;
    l = i >> 2;
    k = i >>> j;
  }
}""",
            ).checkMutations<MutateMath> { mutations, contents ->
                mutations shouldHaveSize 10
                mutations[0].check(contents, "-", "+")
                mutations[1].check(contents, "*", "/")
                mutations[2].check(contents, "/", "*")
                mutations[3].check(contents, "%", "*")
                mutations[4].check(contents, "&", "|")
                mutations[5].check(contents, "|", "&")
                mutations[6].check(contents, "^", "&")
                mutations[7].check(contents, "<<", ">>")
                mutations[8].check(contents, ">>", "<<")
                mutations[9].check(contents, ">>>", "<<")
            }
        }
        "it should mutate plus separately" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    int i = 0;
    int j = 1;
    int k = i + j;
  }
}""",
            ).checkMutations<PlusToMinus> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations[0].check(contents, "+", "-")
            }
        }
        "it should find conditional boundaries to mutate" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    int i = 0;
    if (i < 10) {
      System.out.println("Here");
    } else if (i >= 20) {
      System.out.println("There");
    }
  }
}""",
            ).checkMutations<ConditionalBoundary> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "<", "<=")
                mutations[1].check(contents, ">=", ">")
            }
        }
        "it should find conditionals to negate" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    int i = 0;
    if (i < 10) {
      System.out.println("Here");
    } else if (i >= 20) {
      System.out.println("There");
    } else if (i == 10) {
      System.out.println("Again");
    }
  }
}""",
            ).checkMutations<NegateConditional> { mutations, contents ->
                mutations shouldHaveSize 3
                mutations[0].check(contents, "<", ">=")
                mutations[1].check(contents, ">=", "<")
                mutations[2].check(contents, "==", "!=")
            }
        }
        "it should find primitive returns to mutate" {
            Source.fromJava(
                """
                    public class Example {
  public static void first() {}
  public static int second() {
    return 1;
  }
  public static char third() {
    return 'A';
  }
  public static boolean fourth() {
    return true;
  }
  public static int fifth() {
    return 0;
  }
  public static long sixth() {
    return 0L;
  }
  public static double seventh() {
    return 0.0;
  }
  public static double eighth() {
    return 0.0f;
  }
}""",
            ).checkMutations<PrimitiveReturn> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "1", "0")
                mutations[1].check(contents, "'A'", "0")
            }
        }
        "it should find true returns to mutate" {
            Source.fromJava(
                """
public class Example {
  public static void first() {}
  public static boolean second() {
    it = false;
    return it;
  }
  public static boolean third() {
    return false;
  }
  public static boolean fourth() {
    return true;
  }
}""",
            ).checkMutations<TrueReturn> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "it", "true")
                mutations[1].check(contents, "false", "true")
            }
        }
        "it should find false returns to mutate" {
            Source.fromJava(
                """
public class Example {
  public static void first() {}
  public static boolean second() {
    it = false;
    return it;
  }
  public static boolean third() {
    return false;
  }
  public static boolean fourth() {
    return true;
  }
}""",
            ).checkMutations<FalseReturn> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "it", "false")
                mutations[1].check(contents, "true", "false")
            }
        }
        "it should find null returns to mutate" {
            Source.fromJava(
                """
public class Example {
  public static void first() {}
  public static boolean second() {
    it = false;
    return it;
  }
  public static boolean third() {
    return false;
  }
  public static Object fourth() {
    return new Object();
  }
  public static int[] fifth() {
    return new int[] {};
  }
}""",
            ).checkMutations<NullReturn> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "new Object()", "null")
                mutations[1].check(contents, "new int[] {}", "null")
            }
        }
        "it should find asserts to mutate" {
            Source.fromJava(
                """
public class Example {
  public static void test(int first, int second) {
    assert first > 0;
    assert second >= 0 : "Bad second value";
  }
}""",
            ).checkMutations<RemoveRuntimeCheck> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "assert first > 0;", "")
                mutations[1].check(contents, """assert second >= 0 : "Bad second value";""", "")
            }
        }
        "it should remove entire methods" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first, int second) {
    if (first > second) {
      return first;
    } else {
      return second;
    }
  }
  public static long[] test(int first, int second) {
    return new long[] {1L, 2L, 4L};
  }
}""",
            ).checkMutations<RemoveMethod> { mutations, _ ->
                mutations shouldHaveSize 2
            }
        }
        "it should not remove entire methods if they are already blank" {
            Source.fromJava(
                """
public class Example {
  public static void test(int first, int second) {
  }
  public static void test2(int first, int second) { }
  public static void test3(int first, int second) {
  
  
    }
  public static void test4(int first, int second) { return; }
  public static void test4(int first, int second) {
return ;
}
}""",
            ).checkMutations<RemoveMethod> { mutations, _ ->
                mutations shouldHaveSize 0
            }
        }
        "it should negate if statements" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first, int second) {
    if (first > second) {
      return first;
    } else {
      return second;
    }
  }
}""",
            ).checkMutations<NegateIf> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations[0].check(contents, "(first > second)", "(!(first > second))")
            }
        }
        "it should negate while statements" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    int i = 0;
    while (i < first) {
      i++;
    }
  }
}""",
            ).checkMutations<NegateWhile> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations[0].check(contents, "(i < first)", "(!(i < first))")
            }
        }
        "it should remove if statements" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    if (first > 0) {
      System.out.println(1);
    }
    if (first > 0) {
      System.out.println(2);
    } else {
      System.out.println(3);
    }
    if (first > 0) {
      System.out.println(4);
    } else if (first < 0) {
      System.out.println(5);
    } else if (first == 0) {
      System.out.println(6);
    } else {
      if (first < 0) {
        System.out.println(7);
      }
      System.out.println(7);
    }
  }
}""",
            ).checkMutations<RemoveIf> { mutations, contents ->
                mutations shouldHaveSize 8
                mutations[0].check(
                    contents,
                    """if (first > 0) {
      System.out.println(1);
    }""",
                    "",
                )
            }
        }
        "it should flip and and or" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    if (first > 0 && first < 0) {
      System.out.println(1);
    }
  }
}""",
            ).checkMutations<SwapAndOr> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations[0].check(contents, "&&", "||")
            }
        }
        "it should swap break and continue" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    for (int i = 0; i < 10; i++) {
      if (i < 5) {
        continue;
      }
      if (i > 7) {
        break;
      }
    }
  }
}""",
            ).checkMutations<SwapBreakContinue> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "continue", "break")
            }
        }
        "it should remove plus and minus 1" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    int i = 0;
    int j = 0;
    int i = i + 1;
    int j = j - 1;
  }
}""",
            ).checkMutations<PlusOrMinusOneToZero> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "1", "0")
                mutations[1].check(contents, "1", "0")
            }
        }
        "it should remove plus and minus 1 with number literal" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    int i = i + 1;
    int j = j - 1;
  }
}""",
            ).checkMutations<NumberLiteral> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "1", "0")
                mutations[1].check(contents, "1", "0")
            }
        }
        "it should remove loops correctly" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    for (int i = 0; i < first; i++) { }
    while (true) { }
    for (int i : new int[] {1, 2, 4}) { }
    do {} while (true);
  }
}""",
            ).checkMutations<RemoveLoop> { mutations, contents ->
                mutations shouldHaveSize 4
                mutations[0].check(contents, "for (int i = 0; i < first; i++) { }", "")
            }
        }
        "it should add breaks to for loops correctly" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    for (int i = 0; i < first; i++) {
        i += 1;
    }
    for (int i : new int[] {1, 2, 4}) { }
  }
}""",
            ).checkMutations<AddBreak> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations.forEach {
                    it.check(contents, "}", "break; }")
                }
            }
        }
        "it should add breaks to for loops with nesting correctly" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    for (int i = 0; i < first; i++) {
        if (i > 2) {
            i += 2;
        }
        i += 1;
    }
    for (int i : new int[] {1, 2, 4}) { }
  }
}""",
            ).checkMutations<AddBreak> { mutations, contents ->
                mutations shouldHaveSize 3
                mutations.forEach {
                    it.check(contents, "}", "break; }")
                }
            }
        }
        "it should add breaks to for loops with nesting correctly and avoid double break" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    for (int i = 0; i < first; i++) {
        if (i > 2) {
            i += 2;
            break;
        }
        if (i < 1) {
            i += 0;
        }
        Modify first = (value) -> {
          value + 1;
        };
        i += 1;
    }
    for (int i : new int[] {1, 2, 4}) { }
  }
}""",
            ).checkMutations<AddBreak> { mutations, contents ->
                mutations shouldHaveSize 3
                mutations.forEach {
                    it.check(contents, "}", "break; }")
                }
            }
        }
        "it should add continue to for loops with nesting correctly and avoid double continue" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    for (int i = 0; i < first; i++) {
        if (i > 2) {
            i += 2;
            continue;
        }
        if (i < 1) {
            i += 0;
        }
        Modify first = (value) -> {
          value + 1;
        };
        i += 1;
    }
    for (int i : new int[] {1, 2, 4}) { }
  }
}""",
            ).checkMutations<AddContinue> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations.forEach {
                    it.check(contents, "}", "continue; }")
                }
            }
        }
        "it should not add continue in last statement in for loop" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    for (int i = 0; i < first; i++) {
        if (i > 2) {
            i += 2;
            continue;
        }
        if (i < 1) {
            i += 0;
        }
        if (i > 3) {
            i += 1;
        } else {
            i += 2;
        }
        continue;
    }
    for (int i : new int[] {1, 2, 4}) { }
  }
}""",
            ).checkMutations<AddContinue> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations.forEach {
                    it.check(contents, "}", "continue; }")
                }
            }
        }
        "it should not add continue in last try statement in for loop" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    for (int i = 0; i < first; i++) {
        if (i > 2) {
            i += 2;
            continue;
        }
        if (i < 1) {
            i += 0;
        }
        try {
          i = 0;
        } catch (Exception e) {
          System.out.println(e);
        }
        continue;
    }
    for (int i : new int[] {1, 2, 4}) { }
  }
}""",
            ).checkMutations<AddContinue> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations.forEach {
                    it.check(contents, "}", "continue; }")
                }
            }
        }
        "it should not add continue in last statement in while loop" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    while (i < 10) {
        if (i > 2) {
            i += 2;
            continue;
        }
        if (i < 1) {
            i += 0;
        }
        if (i > 3) {
            i += 1;
        }
        continue;
    }
    for (int i : new int[] {1, 2, 4}) { }
  }
}""",
            ).checkMutations<AddContinue> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations.forEach {
                    it.check(contents, "}", "continue; }")
                }
            }
        }
        "it should add breaks to while loops correctly" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    int i = 0;
    while (true) {
        i += 1;
    }
    do {
        i += 1;
    } while (true);
  }
}""",
            ).checkMutations<AddBreak> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "}", "break; }")
                mutations[1].check(contents, "}", "break; }")
            }
        }
        "it should mutate nested loops correctly" {
            Source.fromJava(
                """
public class Question {
  boolean arrayContainsDuplicate(int[] values) {
    if (values == null) {
      return false;
    }
    for (int i = 0; i < values.length - 1; i++) {
      for (int j = i + 1; j < values.length; j++) {
        if (values[i] == values[j]) {
          return true;
        }
      }
    }
    return false;
  }
}""",
            ).allMutations()
        }
        "it should remove and-ors correctly" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    if (true && false) { }
    if (false || true) { }
  }
}""",
            ).checkMutations<RemoveAndOr> { mutations, contents ->
                mutations shouldHaveSize 4
                mutations[0].check(contents, "true && ", "")
                mutations[1].check(contents, " && false", "")
            }
        }
        "it should remove try correctly" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    try {
      int value = 0;
    } catch (Exception e) { }
  }
}""",
            ).checkMutations<RemoveTry> { mutations, _ ->
                mutations shouldHaveSize 1
            }
        }
        "it should remove statements correctly" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    int i = 0;
    i = 1;
    i++;
    if (i > 0) {
      i++;
    }
  }
}""",
            ).checkMutations<RemoveStatement> { mutations, _ ->
                mutations shouldHaveSize 3
            }
        }
        "it should remove plus correctly" {
            Source.fromJava(
                """
public class Example {
  public static int test(int first) {
    int i = 1 + 2;
    i = 3 + 4;
  }
}""",
            ).checkMutations<RemovePlus> { mutations, contents ->
                mutations shouldHaveSize 4
                mutations[0].check(contents, "1 + ", "")
                mutations[1].check(contents, " + 2", "")
            }
        }
        "it should remove binary operators" {
            Source.fromJava(
                """
public class Example {
  public static void example() {
    int i = 0;
    int j = 1;
    int k = i + j;
    k = i - j;
    k = i * j;
    k = i / j;
    int l = i % 10;
    l = i & j;
    l = j | i;
    l = j ^ i;
    l = i << 2;
    l = i >> 2;
    k = i >>> j;
  }
}""",
            ).checkMutations<RemoveBinary> { mutations, _ ->
                mutations shouldHaveSize 20
            }
        }
        "it should complete the change equals mutation" {
            Source.fromJava(
                """
public class Example {
  public void equalsTester() {
    String example1 = "test";
    String example2 = new String("test");
    String example3 = "test1";
    example3 = example3.substring(0, 4);
    System.out.println(example1 == example3);
    System.out.println(example2.equals(example3));
  }
}
""".trim(),
            ).checkMutations<ChangeEquals> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "example1 == example3", "(example1).equals(example3)")
                mutations[1].check(contents, "example2.equals(example3)", "(example2) == (example3)")
            }
        }
        "it should change length and size" {
            Source.fromJava(
                """
public class Example {
  public void equalsTester() {
    int[] array = new int[10];
    for (int i = 0; i < array.length; i++) {
      System.out.println(i);
    }
    String s = "test";
    for (int i = 0; i < s.length(); i++) {
      System.out.println(i);
    }
    List<String> t = Arrays.asList(1, 2, 4);
    for (int i = 0; i < t.size(); i++) {
      System.out.println(i);
    }
    int another = t.size(10); // Ignore size and length with parameters
    int testing = s.length("foo");
  }
}
""".trim(),
            ).checkMutations<ChangeLengthAndSize> { mutations, contents ->
                mutations shouldHaveSize 3
                mutations[0].check(contents, "length")
                mutations[1].check(contents, "length()")
                mutations[2].check(contents, "size()")
            }
        }
        "it should mutate array literals" {
            Source.fromJava(
                """
public class Example {
  public void test() {
    int[] ignore = new int[] {1};
    int[] values = new int[] {1, 2, 4};
    int[][] values = new int[][] {{1, 2}, {4, 5}};
  }
}
""".trim(),
            ).checkMutations<ModifyArrayLiteral> { mutations, contents ->
                mutations shouldHaveSize 4
                mutations[0].check(contents, "1, 2, 4")
                mutations[1].check(contents, "{1, 2}, {4, 5}").also {
                    it shouldMatch ".*\\{1, 2}.*".toRegex(RegexOption.DOT_MATCHES_ALL)
                }
            }
        }
        "it should remove blank lines correctly" {
            val source = Source.fromJava(
                """
public class Example {
  public static void test(int first, int second) {
    assert first > 0;
    assert second >= 0 : "Bad second value";
  }
}""".trim(),
            )
            source.allMutations(types = setOf(Mutation.Type.REMOVE_RUNTIME_CHECK)).also { mutations ->
                mutations shouldHaveSize 2
                mutations[0].contents.lines() shouldHaveSize 5
                mutations[0].contents.lines().filter { it.isBlank() } shouldHaveSize 0
                mutations[1].contents.lines() shouldHaveSize 5
                mutations[1].contents.lines().filter { it.isBlank() } shouldHaveSize 0
            }
        }
        "it should ignore suppressed mutations" {
            Source.fromJava(
                """
public class Example {
  public static Object fourth() {
    if (true) {
      System.out.println("Here");
    }
    return new Object(); // mutate-disable
  }
}""",
            ).allMutations().also { mutations ->
                mutations shouldHaveSize 7
                mutations[0].cleaned().also {
                    it["Main.java"] shouldNotContain "mutate-disable"
                }
            }
        }
        "it should ignore suppressed mutations on next lines" {
            Source.fromJava(
                """
public class Example {
  public static Object fourth() {
    if (true) {
      System.out.println("Here");
    }
    // mutate-disable
    return new Object();
  }
}""",
            ).allMutations().also { mutations ->
                mutations shouldHaveSize 7
                mutations[0].cleaned().also {
                    it["Main.java"] shouldNotContain "mutate-disable"
                }
            }
        }
        "it should ignore specific suppressed mutations" {
            Source.fromJava(
                """
public class Example {
  public static int fourth(int first, int second) {
    if (first > second) { // mutate-disable-conditional-boundary
      return first;
    } else {
      // mutate-disable-primitive-return
      return second;
    }
  }
}""",
            ).allMutations().also { mutations ->
                mutations shouldHaveSize 6
                mutations[0].cleaned().also {
                    it["Main.java"] shouldNotContain "mutate-disable-conditional-boundary"
                    it["Main.java"] shouldNotContain "mutate-disable-primitive-return"
                }
            }
        }
        "it should apply multiple mutations" {
            Source.fromJava(
                """
public class Example {
  public static void greeting() {
    int i = 0;
    System.out.println("Hello, world!");
  }
}""",
            ).also { source ->
                source.mutater(seed = 124)
                    .also { mutater ->
                        mutater.appliedMutations shouldHaveSize 0
                        val modifiedSource = mutater.apply().contents
                        source.contents shouldNotBe modifiedSource
                        mutater.appliedMutations shouldHaveSize 1
                        mutater.size shouldBe 1
                        val anotherModifiedSource = mutater.apply().contents
                        setOf(source.contents, modifiedSource, anotherModifiedSource) shouldHaveSize 3
                        mutater.size shouldBe 0
                    }
                source.mutate().also { mutatedSource ->
                    source.contents shouldNotBe mutatedSource.contents
                    mutatedSource.mutations shouldHaveSize 1
                }
                source.mutate(limit = Int.MAX_VALUE).also { mutatedSource ->
                    source.contents shouldNotBe mutatedSource.contents
                    mutatedSource.unappliedMutations shouldBe 0
                }
                source.allMutations(random = Random(124)).also { mutatedSources ->
                    mutatedSources shouldHaveSize 7
                    mutatedSources.map { it.contents }.toSet() shouldHaveSize 7
                }
            }
        }
        "it should handle overlapping mutations" {
            Source.fromJava(
                """
public class Example {
  public static int testing() {
    return 10;
  }
}""",
            ).also { source ->
                source.mutater(types = ALL - setOf(Mutation.Type.REMOVE_METHOD)).also { mutater ->
                    mutater.size shouldBe 3
                    mutater.apply()
                    mutater.size shouldBe 0
                }
            }
        }
        "it should shift mutations correctly" {
            Source.fromJava(
                """
public class Example {
  public static int testing() {
    boolean it = true;
    return 10;
  }
}""",
            ).also { source ->
                source.mutater(shuffle = false, types = ALL - setOf(Mutation.Type.REMOVE_METHOD)).also { mutater ->
                    mutater.size shouldBe 4
                    mutater.apply()
                    mutater.size shouldBe 3
                    mutater.apply()
                    mutater.size shouldBe 0
                }
                source.allMutations(types = ALL - setOf(Mutation.Type.REMOVE_METHOD), random = Random(124))
                    .also { mutations ->
                        mutations shouldHaveSize 4
                        mutations.map { it.contents }.toSet() shouldHaveSize 3
                    }
            }
        }
        "it should return predictable mutations" {
            Source.fromJava(
                """
public class Example {
  public static int testing() {
    boolean it = true;
    return 10;
  }
}""",
            ).also { source ->
                val first = source.allMutations(random = Random(seed = 10))
                val second = source.allMutations(random = Random(seed = 10))
                first.size shouldBe second.size
                first.zip(second).forEach { (first, second) ->
                    first.contents shouldBe second.contents
                }
            }
        }
        "it should apply mutations correctly with Strings" {
            Source.fromJava(
                """
public class Example {
  String reformatName(String input) {
    if (input == null) {
      return null;
    }
    String[] parts = input.split(",");
    return parts[1].trim() + " " + parts[0].trim();
  }
}""",
            ).also { source ->
                source.allMutations()
            }
        }
        "it should apply stream mutations" {
            Source.fromJava(
                """
public class Example {
  String testStream() {
    String test = "foobarfoobarfoobarfoobar";
    return test;
  }
}""",
            ).also { source ->
                source.mutationStream(random = Random(124)).toList().size shouldBe 987
            }
        }
        "it should apply all fixed mutations" {
            Source.fromJava(
                """
public class Example {
  String testStream() {
    String test = "foobarfoobarfoobarfoobar"; // 5 mutations
    if (test.length() > 4) { // 3 + 3 mutations
      return "blah"; // 5 mutations
    }
    return test; // 1 mutation
  }
}""",
            ).allFixedMutations(random = Random(124)).also { mutations ->
                mutations.size shouldBe 29
            }
        }
        "it should end stream mutations when out of things to mutate" {
            Source.fromJava(
                """
public class Example {
  int testStream() {
    int i = 0;
    i++;
    return i;
  }
}""",
            ).also { source ->
                source.mutationStream().take(1024).toList().size shouldBe 5
            }
        }
        "it should not mutate annotations" {
            Source.fromJava(
                """
public class Example {
  @Suppress("unused")
  void reformatName(String input) {
    return;
  }
}""",
            ).also { source ->
                source.allMutations() shouldHaveSize 0
            }
        }
        "it should mark mutations cleanly" {
            Source.fromJava(
                """
public class Example {
  void reformatName(String input) {
    if (input == null) {
      return;
    }
    System.out.println("Hello, " + input);
  }
}""",
            ).allMutations().also { mutations ->
                mutations shouldHaveSize 13
                mutations.forEach { mutatedSource ->
                    mutatedSource.marked().checkstyle(CheckstyleArguments(failOnError = true))
                }
            }
        }
        "it should mark mutations cleanly part 2" {
            Source.fromJava(
                """
public class Example {
    int[] example(int[] values) {
        int larger = values[0];
        if (values[2] > larger) {
            larger = values[2];
        }
        values[0] = larger;
        values[1] = larger;
        values[2] = larger;
        return values;
    }
}""",
            ).allMutations().also { mutations ->
                mutations shouldHaveSize 16
                mutations.forEach { mutatedSource ->
                    mutatedSource.marked()
                }
            }
        }
        "it should handle double marks" {
            Source.fromJava(
                """
public class Example {
  public String startWord(String input, String word) {
    if (input.length > 4
        && word.length > 5
        && word.length > 4) {
      System.out.println("Here");
    }
    if (input.length() > 0 && input.substring(1).startsWith(word.substring(1))) {
      return input.substring(0, word.length());
    } else {
      return "";
    }
  }
}""",
            ).allMutations().onEach { mutatedSource ->
                mutatedSource.marked().checkstyle().also { errors ->
                    errors.errors.filter { it.key != "block.noStatement" } shouldHaveSize 0
                }
            }
        }

        "should not fail with new switch syntax" {
            Source.fromJava(
                """
public class Example {
    public boolean shouldMakeCoffee(String situation) {
        return switch (situation) {
          case "Morning", "Cramming" -> true;
          case "Midnight" -> true;
          default -> false;
        };
    }
}""",
            ).allMutations().map {
                try {
                    Source.fromJava(it.contents).complexity()
                } catch (e: Exception) {
                    println(it.mutations.first().mutation.mutationType)
                    println(it.contents)
                    throw e
                }
            }
        }

        "should not fail with another new switch syntax" {
            Source.fromJava(
                """
public class Example {
    public void test() {
        int foo = 3;
        boolean boo = switch (foo) {
          case 1, 2, 3 -> true;
          default -> false;
        };
    }
}""",
            ).allMutations().map {
                try {
                    Source.fromJava(it.contents).complexity()
                } catch (e: Exception) {
                    println(it.mutations.first().mutation.mutationType)
                    println(it.contents)
                    throw e
                }
            }
        }

        "should not fail on unused variables" {
            Source.fromJava(
                """
            public class Example {
                public void test() {
                    var _ = 5;
                    int a = 10, _ = 12;
                    try (var _ = somethingDisposable()) {}
                    for (int i = 0, _ = unused(); ;) {}
                    for (var _ : things) {}
                    try {
                        boom();
                    } catch (Exception _) {}
                    Consumer<String> c = _ -> System.out.println("unused");
                    BiConsumer<String, Integer> b1 = (_, _) -> unused();
                    BiConsumer<String, Integer> b2 = (String _, Integer i) -> count(i);
                    BiConsumer<String, Integer> b3 = (var _, var i) -> count(i);
                }
            }
                """.trimIndent(),
            ).allMutations().map {
                Source.fromJava(it.contents).complexity()
            }
        }

        "it should handle double marks again" {
            Source.fromJava(
                """
public class Question {
  char gameOver(char[][] board) {
    for (int i = 0; i < 3; i++) {
      if (board[i][0] != ' '
            && board[i][0] == board[i][1]
            && board[i][0] == board[i][2]) {
        return board[i][0];
      }
    }
    for (int i = 0; i < 3; i++) {
      if (board[0][i] != ' '
            && board[0][i] == board[1][i]
            && board[0][i] == board[2][i]) {
        return board[0][i];
      }
    }
    return ' ';
  }
}
""",
            ).allMutations().forEach { mutatedSource ->
                mutatedSource.marked().checkstyle(CheckstyleArguments()).also { errors ->
                    errors.errors.filter {
                        it.key != "block.noStatement" && it.key != "indentation.child.error" && it.key != "line.break.before"
                    } shouldHaveSize 0
                }
            }
        }
        "it should work on for without braces" {
            Source.fromJava(
                """
public class Question {
  public static void test() {
    int x = 1;
    for(int i: values)
      x*=i;
    System.out.println(x);
  }
}
                """.trimMargin(),
            ).allMutations()
        }
        "it should work on if-else without braces" {
            Source.fromJava(
                """
public class Question {
  public static void test() {
    if (friendsCount > 500)
      System.out.println("Adopt a Dog Today!");
    else
      System.out.println("Buy Cat Food At 20% Off");
  }
}
                """.trimMargin(),
            ).allMutations()
        }
        "it should work on exponents" {
            Source.fromJava(
                """
public class Question {
  public static void test() {
    int test = (int) 2e32;
    float another = 2e-20F;
  }
}
                """.trimMargin(),
            ).also { it.compile() }.allMutations()
        }
        "it should work on equals v dot-equals" {
            Source.fromJava(
                """
public class Question {
  public static int whatever(int value) {
    if (value % 2 == 0) {
      return value * 2;
    }
    assert false;
    return -1;
  }
}
                """.trimMargin(),
            ).checkMutations<ChangeEquals> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations[0].apply(contents).googleFormat()
            }
        }
        "it should work on negative numbers" {
            Source.fromJava(
                """
public class Question {
  public static void conditional(double latitude, double longitude) {
    if (longitude == -122.076817) {
      System.out.println("Center of the Universe");
    } else {
      System.out.println("Somewhere else");
    }
    double test = 0.1;
    double there = 1.1;
    double wow = 1.0;
    double another = .1;
    double finalTest = 6.;
    double oneMoreTest = 1.00;
    double additionalTest = 1.01;
  }
}
                """.trimMargin(),
            ).checkMutations<NumberLiteralTrim> { mutations, contents ->
                mutations shouldHaveSize 3
                mutations.forEach { mutation ->
                    mutation.apply(contents, Random(124)).also {
                        it.googleFormat()
                    }
                }
            }
        }
        "it should not mutate modulo to zero with number literal" {
            Source.fromJava(
                """
public class Question {
  public static void test() {
    int value = value % 1;
  }
}
                """.trimMargin(),
            ).checkMutations<NumberLiteral> { mutations, contents ->
                mutations shouldHaveSize 1
                repeat(32) {
                    mutations.first().apply(contents).also { mutated ->
                        mutated shouldNotContain "value % 0"
                    }
                    mutations.first().reset()
                }
            }
            Source.fromJava(
                """
public class Question {
  public static void test() {
    value /= 1;
  }
}
                """.trimMargin(),
            ).checkMutations<NumberLiteral> { mutations, contents ->
                mutations shouldHaveSize 1
                repeat(32) {
                    mutations.first().apply(contents).also { mutated ->
                        mutated shouldNotContain "value /= 0"
                    }
                    mutations.first().reset()
                }
            }
            Source.fromJava(
                """
public class Question {
  public static void test() {
    int value = value / 1;
  }
}
                """.trimMargin(),
            ).checkMutations<NumberLiteral> { mutations, contents ->
                mutations shouldHaveSize 1
                repeat(32) {
                    mutations.first().apply(contents).also { mutated ->
                        mutated shouldNotContain "value / 0"
                    }
                    mutations.first().reset()
                }
            }
            Source.fromJava(
                """
public class Question {
  public static void test() {
    int value = value % ((1));
  }
}
                """.trimMargin(),
            ).checkMutations<NumberLiteral> { mutations, contents ->
                mutations shouldHaveSize 1
                repeat(32) {
                    mutations.first().apply(contents).also { mutated ->
                        mutated shouldNotContain "value % ((0))"
                    }
                    mutations.first().reset()
                }
            }
        }
        "it should not mutate modulo to zero with number literal trim" {
            Source.fromJava(
                """
public class Question {
  public static void test() {
    int value = value % 10;
  }
}
                """.trimMargin(),
            ).checkMutations<NumberLiteralTrim> { mutations, contents ->
                mutations shouldHaveSize 1
                repeat(32) {
                    mutations.first().apply(contents).also { mutated ->
                        mutated shouldNotContain "value % 0"
                    }
                    mutations.first().reset()
                }
            }
            Source.fromJava(
                """
public class Question {
  public static void test() {
    int value = value / 10;
  }
}
                """.trimMargin(),
            ).checkMutations<NumberLiteralTrim> { mutations, contents ->
                mutations shouldHaveSize 1
                repeat(32) {
                    mutations.first().apply(contents).also { mutated ->
                        mutated shouldNotContain "value / 0"
                    }
                    mutations.first().reset()
                }
            }
            Source.fromJava(
                """
public class Question {
  public static void test() {
    int value = value % (10);
  }
}
                """.trimMargin(),
            ).checkMutations<NumberLiteralTrim> { mutations, contents ->
                mutations shouldHaveSize 1
                repeat(32) {
                    mutations.first().apply(contents).also { mutated ->
                        mutated shouldNotContain "value % (0)"
                    }
                    mutations.first().reset()
                }
            }
        }
    })

inline fun <reified T : Mutation> Source.checkMutations(
    checker: (mutations: List<Mutation>, contents: String) -> Unit,
) = getParsed(name).also { parsedSource ->
    checker(Mutation.find<T>(parsedSource, type.toFileType()), contents)
}

fun Mutation.check(contents: String, original: String, modified: String? = null, seed: Int = 124): String {
    original shouldNotBe modified
    applied shouldBe false
    this.original shouldBe original
    this.modified shouldBe null
    val toReturn = apply(contents, Random(seed))
    applied shouldBe true
    this.original shouldBe original
    this.modified shouldNotBe original
    modified?.also { this.modified shouldBe modified }
    return toReturn
}

fun Mutation.shouldNotBe(contents: String, original: String, shouldNotBe: String) {
    repeat(32) {
        apply(contents, Random)
        this.original shouldBe original
        this.modified shouldNotBe original
        this.modified shouldNotBe shouldNotBe
        this.reset()
    }
}
