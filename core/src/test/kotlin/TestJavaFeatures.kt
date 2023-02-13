package edu.illinois.cs.cs125.jeed.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

@Suppress("LargeClass")
class TestJavaFeatures : StringSpec({
    "should count variable declarations in snippets" {
        Source.fromJavaSnippet(
            """
int i = 0;
int j;
i = 4;
i += 1;
i++;
--j;
"""
        ).features().check {
            featureMap[FeatureName.LOCAL_VARIABLE_DECLARATIONS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.LOCAL_VARIABLE_DECLARATIONS, listOf(1, 2))

            featureMap[FeatureName.VARIABLE_ASSIGNMENTS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.VARIABLE_ASSIGNMENTS, listOf(1))

            featureMap[FeatureName.VARIABLE_REASSIGNMENTS] shouldBe 4
            featureList should haveFeatureAt(FeatureName.VARIABLE_REASSIGNMENTS, (3..6).toList())

            featureMap[FeatureName.UNARY_OPERATORS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.UNARY_OPERATORS, listOf(5, 6))
        }.check("") {
            featureMap[FeatureName.CLASS] shouldBe 0
            featureList should haveFeatureAt(FeatureName.CLASS, listOf())

            featureMap[FeatureName.METHOD] shouldBe 0
            featureList should haveFeatureAt(FeatureName.METHOD, listOf())

            featureMap[FeatureName.VISIBILITY_MODIFIERS] shouldBe 0
            featureList should haveFeatureAt(FeatureName.VISIBILITY_MODIFIERS, listOf())

            featureMap[FeatureName.THROWS] shouldBe 0
            featureList should haveFeatureAt(FeatureName.THROWS, listOf())

            featureMap[FeatureName.STATIC_METHOD] shouldBe 0
            featureList should haveFeatureAt(FeatureName.STATIC_METHOD, listOf())
        }
    }
    "should count for loops in snippets" {
        Source.fromJavaSnippet(
            """
for (int i = 0; i < 10; i++) {
    System.out.println(i);
}
int[] arr = new int[10];
for (int num : arr) {
    num++;
}
"""
        ).features().check {
            featureMap[FeatureName.FOR_LOOPS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.FOR_LOOPS, listOf(1, 5))

            featureMap[FeatureName.ARRAYS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.ARRAYS, listOf(4))

            featureMap[FeatureName.NEW_KEYWORD] shouldBe 0
            featureList should haveFeatureAt(FeatureName.NEW_KEYWORD, listOf())

            featureMap[FeatureName.VARIABLE_ASSIGNMENTS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.VARIABLE_ASSIGNMENTS, listOf(1, 4))

            featureMap[FeatureName.VARIABLE_REASSIGNMENTS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.VARIABLE_REASSIGNMENTS, listOf(1, 6))

            featureMap[FeatureName.LOCAL_VARIABLE_DECLARATIONS] shouldBe 3
            featureList should haveFeatureAt(FeatureName.LOCAL_VARIABLE_DECLARATIONS, listOf(1, 4, 5))

            featureMap[FeatureName.ENHANCED_FOR] shouldBe 1
            featureList should haveFeatureAt(FeatureName.ENHANCED_FOR, listOf(5))
        }
    }
    "should count nested for loops in snippets" {
        Source.fromJavaSnippet(
            """
for (int i = 0; i < 10; i++) {
    for (int j = 0; j < 10; j++) {
        System.out.println(i + j);
    }
}
"""
        ).features().check {
            featureMap[FeatureName.FOR_LOOPS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.FOR_LOOPS, listOf(1, 2))

            featureMap[FeatureName.NESTED_FOR] shouldBe 1
            featureList should haveFeatureAt(FeatureName.NESTED_FOR, listOf(2))

            featureMap[FeatureName.NESTED_LOOP] shouldBe 1
            featureList should haveFeatureAt(FeatureName.NESTED_LOOP, listOf(2))
        }
    }
    "should count while loops in snippets" {
        Source.fromJavaSnippet(
            """
int i = 0;
while (i < 10) {
    while (j < 10) {
        j++;
    }
    i++;
}
"""
        ).features().check {
            featureMap[FeatureName.WHILE_LOOPS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.WHILE_LOOPS, listOf(2, 3))

            featureMap[FeatureName.NESTED_WHILE] shouldBe 1
            featureList should haveFeatureAt(FeatureName.NESTED_WHILE, listOf(3))

            featureMap[FeatureName.DO_WHILE_LOOPS] shouldBe 0
            featureList should haveFeatureAt(FeatureName.DO_WHILE_LOOPS, listOf())
        }
    }
    "should not count while loops under if" {
        Source.fromJavaSnippet(
            """
int i = 0;
if (i < 10) {
    while (j < 10) {
        j++;
    }
    i++;
}
"""
        ).features().check {
            featureMap[FeatureName.WHILE_LOOPS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.WHILE_LOOPS, listOf(3))

            featureMap[FeatureName.NESTED_WHILE] shouldBe 0
            featureList should haveFeatureAt(FeatureName.NESTED_WHILE, listOf())
        }
    }
    "should count do-while loops in snippets" {
        Source.fromJavaSnippet(
            """
int i = 0;
do {
    System.out.println(i);
    i++;
    
    int j = 0;
    do {
        j++;
    } while (j < 10);
} while (i < 10);
"""
        ).features().check {
            featureMap[FeatureName.DO_WHILE_LOOPS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.DO_WHILE_LOOPS, listOf(2, 7))

            featureMap[FeatureName.NESTED_DO_WHILE] shouldBe 1
            featureList should haveFeatureAt(FeatureName.NESTED_DO_WHILE, listOf(7))

            featureMap[FeatureName.WHILE_LOOPS] shouldBe 0
            featureList should haveFeatureAt(FeatureName.WHILE_LOOPS, listOf())
        }
    }
    "should count simple if-else statements in snippets" {
        Source.fromJavaSnippet(
            """
int i = 0;
if (i < 5) {
    i++;
} else {
    i--;
}
"""
        ).features().check {
            featureMap[FeatureName.IF_STATEMENTS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.IF_STATEMENTS, listOf(2))

            featureMap[FeatureName.ELSE_STATEMENTS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.ELSE_STATEMENTS, listOf(4))
        }
    }
    "should count a chain of if-else statements in snippets" {
        Source.fromJavaSnippet(
            """
int i = 0;
if (i < 5) {
    i++;
} else if (i < 10) {
    i--;
} else if (i < 15) {
    i++;
} else {
    i--;
}
"""
        ).features().check {
            featureMap[FeatureName.IF_STATEMENTS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.IF_STATEMENTS, listOf(2))

            featureMap[FeatureName.ELSE_STATEMENTS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.ELSE_STATEMENTS, listOf(8))

            featureMap[FeatureName.ELSE_IF] shouldBe 2
            featureList should haveFeatureAt(FeatureName.ELSE_IF, listOf(4, 6))
        }
    }
    "should count nested if statements in snippets" {
        Source.fromJavaSnippet(
            """
int i = 0;
if (i < 15) {
    if (i < 10) {
        i--;
        if (i < 5) {
            i++;
        }
    } else {
        if (i > 10) {
            i--;
        }
    }
}
"""
        ).features().check {
            featureMap[FeatureName.IF_STATEMENTS] shouldBe 4
            featureList should haveFeatureAt(FeatureName.IF_STATEMENTS, listOf(2, 3, 5, 9))

            featureMap[FeatureName.NESTED_IF] shouldBe 3
            featureList should haveFeatureAt(FeatureName.NESTED_IF, listOf(3, 5, 9))
        }
    }
    "should count conditional expressions and complex conditionals in snippets" {
        Source.fromJavaSnippet(
            """
int i = 0;
if (i < 5 || i > 15) {
    if (i < 0) {
        i--;
    }
} else if (!(i > 5 && i < 15)) {
    i++;
} else {
    i--;
}
"""
        ).features().check {
            featureMap[FeatureName.COMPARISON_OPERATORS] shouldBe 5
            featureList should haveFeatureAt(FeatureName.COMPARISON_OPERATORS, listOf(2, 2, 3, 6, 6))

            featureMap[FeatureName.LOGICAL_OPERATORS] shouldBe 3
            featureList should haveFeatureAt(FeatureName.LOGICAL_OPERATORS, listOf(2, 6, 6))
        }
    }
    "should count try blocks, switch statements, finally blocks, and assertions in snippets" {
        Source.fromJavaSnippet(
            """
int i = 0;
try {
    assert i > -1;
    switch(i) {
        case 0:
            System.out.println("zero");
            break;
        case 1:
            System.out.println("one");
            break;
        default:
            System.out.println("not zero or one");
    }
} catch (Exception e) {
    System.out.println("Oops");
} finally { }
"""
        ).features().check {
            featureMap[FeatureName.TRY_BLOCK] shouldBe 1
            featureList should haveFeatureAt(FeatureName.TRY_BLOCK, listOf(2))

            featureMap[FeatureName.ASSERT] shouldBe 1
            featureList should haveFeatureAt(FeatureName.ASSERT, listOf(3))

            featureMap[FeatureName.SWITCH] shouldBe 1
            featureList should haveFeatureAt(FeatureName.SWITCH, listOf(4))

            featureMap[FeatureName.FINALLY] shouldBe 1
            featureList should haveFeatureAt(FeatureName.FINALLY, listOf(16))
        }
    }
    "should count operators in snippets" {
        Source.fromJavaSnippet(
            """
int i = 0;
int j = 0;
if (i < 5) {
    i += 5;
    j = i - 1;
} else if (i < 10) {
    i++;
    j = j & i;
} else if (i < 15) {
    i--;
    j = j % i;
} else {
    i -= 5;
    j = i < j ? i : j;
}
j = j << 2;
"""
        ).features().check {
            featureMap[FeatureName.UNARY_OPERATORS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.UNARY_OPERATORS, listOf(7, 10))

            featureMap[FeatureName.ARITHMETIC_OPERATORS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.ARITHMETIC_OPERATORS, listOf(5, 11))

            featureMap[FeatureName.BITWISE_OPERATORS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.BITWISE_OPERATORS, listOf(8, 16))

            featureMap[FeatureName.ASSIGNMENT_OPERATORS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.ASSIGNMENT_OPERATORS, listOf(4, 13))

            featureMap[FeatureName.TERNARY_OPERATOR] shouldBe 1
            featureList should haveFeatureAt(FeatureName.TERNARY_OPERATOR, listOf(14))
        }
    }
    "should count the new keyword and array accesses in snippets" {
        Source.fromJavaSnippet(
            """
int[] arr = new int[3];
arr[0 + 0] = 5;
arr[1] = 10;
arr[2] = arr[0] + arr[1];
int[] nums = {1, 2, 4};
"""
        ).features().check {
            featureMap[FeatureName.ARRAYS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.ARRAYS, listOf(1, 5))

            featureMap[FeatureName.NEW_KEYWORD] shouldBe 0
            featureList should haveFeatureAt(FeatureName.NEW_KEYWORD, listOf())

            featureMap[FeatureName.ARRAY_ACCESS] shouldBe 5
            featureList should haveFeatureAt(FeatureName.ARRAY_ACCESS, listOf(2, 3, 4, 4, 4))

            featureMap[FeatureName.ARRAY_LITERAL] shouldBe 1
            featureList should haveFeatureAt(FeatureName.ARRAY_LITERAL, listOf(5))
        }
    }
    "should count strings, streams, and null in snippets" {
        Source.fromJavaSnippet(
            """
import java.util.stream.Stream;

String first = "Hello, world!";
String second = null;
Stream<String> stream;
"""
        ).features().check {
            featureMap[FeatureName.STRING] shouldBe 3
            featureList should haveFeatureAt(FeatureName.STRING, listOf(3, 4, 5))

            featureMap[FeatureName.NULL] shouldBe 1
            featureList should haveFeatureAt(FeatureName.NULL, listOf(4))

            featureMap[FeatureName.STREAM] shouldBe 1
            featureList should haveFeatureAt(FeatureName.STREAM, listOf(5))
        }.check("") {
            featureMap[FeatureName.IMPORT] shouldBe 1
            featureList should haveFeatureAt(FeatureName.IMPORT, listOf(1))
        }
    }
    "should count multidimensional arrays in snippets" {
        Source.fromJavaSnippet(
            """
int[][] array = new int[5][5];
char[][] array1 = new char[10][10];
"""
        ).features().check {
            featureMap[FeatureName.MULTIDIMENSIONAL_ARRAYS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.MULTIDIMENSIONAL_ARRAYS, listOf(1, 2))
        }
    }
    "should count use of type inference in snippets" {
        Source.fromJavaSnippet(
            """
var first = 0;
val second = "Hello, world!";
"""
        ).features().check {
            featureMap[FeatureName.TYPE_INFERENCE] shouldBe 2
            featureList should haveFeatureAt(FeatureName.TYPE_INFERENCE, listOf(1, 2))
        }
    }
    "should count methods and classes" {
        Source.fromJavaSnippet(
            """
int test() {
  return 0;
}
public class Test { }
System.out.println("Hello, world!");
"""
        ).features().check("") {
            featureMap[FeatureName.METHOD] shouldBe 1
            featureList should haveFeatureAt(FeatureName.METHOD, listOf(1))

            featureMap[FeatureName.RETURN] shouldBe 1
            featureList should haveFeatureAt(FeatureName.RETURN, listOf(2))

            featureMap[FeatureName.CLASS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.CLASS, listOf(4))
        }
    }
    "should count constructors, methods, getters, setters, visibility modifiers, and static methods in classes" {
        Source(
            mapOf(
                "Test.java" to """
public class Test {
    private int number;
    
    public Test(int setNumber) {
        number = setNumber;
    }
    
    void setNumber(int setNumber) {
        number = setNumber;
    }
    
    int getNumber() {
        return number;
    }
    
    static int add(int i, int j) {
        number = i + j;
        return number;
    }
    
    class InnerClass { }
}
""".trim()
            )
        ).features().check("", "Test.java") {
            featureMap[FeatureName.CLASS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.CLASS, listOf(1, 21))

            featureMap[FeatureName.VISIBILITY_MODIFIERS] shouldBe 3
            featureList should haveFeatureAt(FeatureName.VISIBILITY_MODIFIERS, listOf(1, 2, 4))
        }.check("Test", "Test.java") {
            featureMap[FeatureName.CONSTRUCTOR] shouldBe 1
            featureList should haveFeatureAt(FeatureName.CONSTRUCTOR, listOf(4))

            featureMap[FeatureName.METHOD] shouldBe 3
            featureList should haveFeatureAt(FeatureName.METHOD, listOf(8, 12, 16))

            featureMap[FeatureName.GETTER] shouldBe 1
            featureList should haveFeatureAt(FeatureName.GETTER, listOf(12))

            featureMap[FeatureName.SETTER] shouldBe 1
            featureList should haveFeatureAt(FeatureName.SETTER, listOf(8))

            featureMap[FeatureName.STATIC_METHOD] shouldBe 1
            featureList should haveFeatureAt(FeatureName.STATIC_METHOD, listOf(16))

            featureMap[FeatureName.VISIBILITY_MODIFIERS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.VISIBILITY_MODIFIERS, listOf(2, 4))

            featureMap[FeatureName.NESTED_CLASS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.NESTED_CLASS, listOf(21))
        }
    }
    "should count the extends keyword, the super constructor, and the 'this' keyword in classes" {
        Source.fromJavaSnippet(
            """
public class Person {
    private int age;
    public Person(int setAge) {
        this.age = setAge;
    }
}
public class Student extends Person {
    private String school;
    public Student(int setAge, String setSchool) {
        super(setAge);
        this.school = setSchool;
    }
}
"""
        ).features().check("Student") {
            featureMap[FeatureName.EXTENDS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.EXTENDS, listOf(7))

            featureMap[FeatureName.SUPER] shouldBe 1
            featureList should haveFeatureAt(FeatureName.SUPER, listOf(10))

            featureMap[FeatureName.THIS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.THIS, listOf(11))
        }
    }
    "should count instanceof and casting" {
        Source.fromJavaSnippet(
            """
double temperature = 72.5;
String name = "Geoff";
if (name instanceof String) {
    int rounded = (int) temperature;
    String test = (String) "test";
}
"""
        ).features().check {
            featureMap[FeatureName.INSTANCEOF] shouldBe 1
            featureList should haveFeatureAt(FeatureName.INSTANCEOF, listOf(3))

            featureMap[FeatureName.CASTING] shouldBe 1
            featureList should haveFeatureAt(FeatureName.CASTING, listOf(5))

            featureMap[FeatureName.PRIMITIVE_CASTING] shouldBe 1
            featureList should haveFeatureAt(FeatureName.PRIMITIVE_CASTING, listOf(4))
        }
    }
    "should count override annotation and import statements" {
        Source(
            mapOf(
                "Test.java" to """
import java.util.Random;

public class Test {
    private int number;
    
    public Test(int setNumber) {
        number = setNumber;
    }
    
    @Override
    String toString() {
        return "String";
    }
}
""".trim()
            )
        ).features().check("Test", "Test.java") {
            featureMap[FeatureName.OVERRIDE] shouldBe 1
            featureList should haveFeatureAt(FeatureName.OVERRIDE, listOf(10))
        }.check("", "Test.java") {
            featureMap[FeatureName.IMPORT] shouldBe 1
            featureList should haveFeatureAt(FeatureName.IMPORT, listOf(1))
        }
    }
    "should count reference equality" {
        Source.fromJavaSnippet(
            """
String first = "Hello";
String second = "World";
boolean third = first == second;
"""
        ).features().check {
            featureMap[FeatureName.REFERENCE_EQUALITY] shouldBe 1
            featureList should haveFeatureAt(FeatureName.REFERENCE_EQUALITY, listOf(3))
        }
    }
    "should count interfaces and classes that implement interfaces" {
        Source.fromJavaSnippet(
            """
public interface Test {
    int add(int x, int y);
    int subtract(int x, int y);
}
public class Calculator implements Test {
    int add(int x, int y) {
        return x + y;
    }
    
    int subtract(int x, int y) {
        return x - y;
    }
}
"""
        ).features().check("Test") {
            featureMap[FeatureName.INTERFACE] shouldBe 1
            featureList should haveFeatureAt(FeatureName.INTERFACE, listOf(1))

            featureMap[FeatureName.METHOD] shouldBe 2
            featureList should haveFeatureAt(FeatureName.METHOD, listOf(2, 3))
        }.check("Calculator") {
            featureMap[FeatureName.IMPLEMENTS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.IMPLEMENTS, listOf(5))
        }
    }
    "should count final and abstract methods" {
        Source.fromJavaSnippet(
            """
public abstract class Test {
    abstract int add(int x, int y);
    abstract int subtract(int x, int y);
}
public class Calculator implements Test {
    final int count;
    
    final int add(int x, int y) {
        return x + y;
    }
    
    int subtract(int x, int y) {
        return x - y;
    }
}
"""
        ).features().check("Test") {
            featureMap[FeatureName.ABSTRACT_METHOD] shouldBe 2
            featureList should haveFeatureAt(FeatureName.ABSTRACT_METHOD, listOf(2, 3))
        }.check("Calculator") {
            featureMap[FeatureName.FINAL_METHOD] shouldBe 1
            featureList should haveFeatureAt(FeatureName.FINAL_METHOD, listOf(8))
        }
    }
    "should count anonymous classes" {
        Source.fromJavaSnippet(
            """
public class Person {
  public String getType() {
    return "Person";
  }
}
Person student = new Person() {
  @Override
  public String getType() {
    return "Student";
  }
};
"""
        ).features().check {
            featureMap[FeatureName.ANONYMOUS_CLASSES] shouldBe 1
            featureList should haveFeatureAt(FeatureName.ANONYMOUS_CLASSES, listOf(6))
        }
    }
    "should count lambda expressions" {
        Source.fromJavaSnippet(
            """
interface Modify {
  int modify(int value);
}
Modify first = (value) -> value + 1;
Modify second = (value) -> value - 10;
"""
        ).features().check {
            featureMap[FeatureName.LAMBDA_EXPRESSIONS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.LAMBDA_EXPRESSIONS, listOf(4, 5))
        }
    }
    "should count throwing exceptions" {
        Source.fromJavaSnippet(
            """
void container(int setSize) throws IllegalArgumentException {
    if (setSize <= 0) {
      throw new IllegalArgumentException("Container size must be positive");
    }
    values = new int[setSize];
}
"""
        ).features().check("") {
            featureMap[FeatureName.THROW] shouldBe 1
            featureList should haveFeatureAt(FeatureName.THROW, listOf(3))

            featureMap[FeatureName.THROWS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.THROWS, listOf(1))
        }
    }
    "should count generic classes" {
        Source(
            mapOf(
                "Counter.java" to """
public class Counter<T> {
  private T value;
  private int count;
  public Counter(T setValue) {
    if (setValue == null) {
      throw new IllegalArgumentException();
    }
    value = setValue;
    count = 0;
  }
  public void add(T newValue) {
    if (value.equals(newValue)) {
      count++;
    }
  }
  public int getCount() {
    return count;
  }
}
""".trim()
            )
        ).features().check("Counter", "Counter.java") {
            featureMap[FeatureName.GENERIC_CLASS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.GENERIC_CLASS, listOf(1))
        }
    }
    "should count classes declared inside methods" {
        Source(
            mapOf(
                "Test.java" to """
public class Test {
    private int number;
    
    public Test(int setNumber) {
        number = setNumber;
    }
    
    void makeClass() {
        class Class { }
    }
}
""".trim()
            )
        ).features().check("", "Test.java") {
            featureMap[FeatureName.CLASS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.CLASS, listOf(1, 9))
        }
    }
    "should count final classes" {
        Source(
            mapOf(
                "Test.java" to """
public final class Test {
    private int number;
    
    public Test(int setNumber) {
        number = setNumber;
    }
    
    public final class First { }
    public abstract class AbstractFirst { }
    
    public void makeClass() {
        public final class Second { }
        public abstract class AbstractSecond { }
    }
}
""".trim()
            )
        ).features().check("", "Test.java") {
            featureMap[FeatureName.FINAL_CLASS] shouldBe 3
            featureList should haveFeatureAt(FeatureName.FINAL_CLASS, listOf(1, 8, 12))

            featureMap[FeatureName.ABSTRACT_CLASS] shouldBe 2
            featureList should haveFeatureAt(FeatureName.ABSTRACT_CLASS, listOf(9, 13))
        }
    }
    "should count interface methods" {
        Source(
            mapOf(
                "Test.java" to """
public interface Test {
    private final int add(int x, int y);
    private static int subtract(int x, int y);
}
                """.trim()
            )
        ).features().check("", "Test.java") {
            featureMap[FeatureName.INTERFACE] shouldBe 1
            featureList should haveFeatureAt(FeatureName.INTERFACE, listOf(1))

            featureMap[FeatureName.METHOD] shouldBe 2
            featureList should haveFeatureAt(FeatureName.METHOD, listOf(2, 3))

            featureMap[FeatureName.STATIC_METHOD] shouldBe 1
            featureList should haveFeatureAt(FeatureName.STATIC_METHOD, listOf(3))

            featureMap[FeatureName.FINAL_METHOD] shouldBe 1
            featureList should haveFeatureAt(FeatureName.FINAL_METHOD, listOf(2))

            featureMap[FeatureName.VISIBILITY_MODIFIERS] shouldBe 3
            featureList should haveFeatureAt(FeatureName.VISIBILITY_MODIFIERS, (1..3).toList())
        }
    }
    "should count enum classes" {
        Source(
            mapOf(
                "Test.java" to """
public enum Test {
    FIRST,
    SECOND,
    THIRD
}
                """.trim()
            )
        ).features().check("", "Test.java") {
            featureMap[FeatureName.ENUM] shouldBe 1
            featureList should haveFeatureAt(FeatureName.ENUM, listOf(1))
        }
    }
    "should correctly create a list of types and identifiers in snippets" {
        Source.fromJavaSnippet(
            """
int i = 0;
double j = 5.0;
boolean foo = true;
String string = "Hello, world!";

"""
        ).features().check {
            typeList shouldBe arrayListOf("int", "double", "boolean", "String")
            identifierList shouldBe arrayListOf("i", "j", "foo", "string")
        }
    }
    "should correctly create a list of import statements" {
        Source(
            mapOf(
                "Test.java" to """
import java.util.List;
import java.util.ArrayList;
                    
public class Test { }
                """.trim()
            )
        ).features().check("", "Test.java") {
            importList shouldBe arrayListOf("java.util.List", "java.util.ArrayList")
        }
    }
    "should correctly count Comparable" {
        Source(
            mapOf(
                "Test.java" to """
public class Test implements Comparable {
    public int compareTo(Test other) {
        return 0;
    }
}
                """.trim()
            )
        ).features().check("", "Test.java") {
            featureMap[FeatureName.COMPARABLE] shouldBe 1
            featureList should haveFeatureAt(FeatureName.COMPARABLE, listOf(1))
        }
    }
    "should correctly count break and continue in snippets" {
        Source.fromJavaSnippet(
            """
for (int i = 0; i < 10; i++) {
    if (i < 7) {
        continue;
    } else {
        break;
    }
}
"""
        ).features().check {
            featureMap[FeatureName.BREAK] shouldBe 1
            featureList should haveFeatureAt(FeatureName.BREAK, listOf(5))

            featureMap[FeatureName.CONTINUE] shouldBe 1
            featureList should haveFeatureAt(FeatureName.CONTINUE, listOf(3))
        }
    }
    "should correctly count modifiers on fields" {
        Source(
            mapOf(
                "Test.java" to """
public class Test {
    static int number = 0;
    final String string = "string";
}
                """.trim()
            )
        ).features().check("Test", "Test.java") {
            featureMap[FeatureName.STATIC_FIELD] shouldBe 1
            featureList should haveFeatureAt(FeatureName.STATIC_FIELD, listOf(2))

            featureMap[FeatureName.FINAL_FIELD] shouldBe 1
            featureList should haveFeatureAt(FeatureName.FINAL_FIELD, listOf(3))

            featureMap[FeatureName.CLASS_FIELD] shouldBe 2
            featureList should haveFeatureAt(FeatureName.CLASS_FIELD, listOf(2, 3))
        }
    }
    "should correctly count boxing classes and type parameters" {
        Source.fromJavaSnippet(
            """
import java.util.List;
import java.util.ArrayList;

Integer first = new Integer("1");
Boolean second = true;
List<String> list = new ArrayList<>();
"""
        ).features().check {
            featureMap[FeatureName.BOXING_CLASSES] shouldBe 2
            featureList should haveFeatureAt(FeatureName.BOXING_CLASSES, listOf(4, 5))

            featureMap[FeatureName.TYPE_PARAMETERS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.TYPE_PARAMETERS, listOf(6))
        }
    }
    "should correctly count print statements and dot notation" {
        Source.fromJavaSnippet(
            """
System.out.println("Hello, world!");
System.out.print("Hello, world!");
System.err.println("Hello, world!");
System.err.print("Hello, world!");
"""
        ).features().check {
            featureMap[FeatureName.PRINT_STATEMENTS] shouldBe 4
            featureList should haveFeatureAt(FeatureName.PRINT_STATEMENTS, (1..4).toList())

            featureMap[FeatureName.DOT_NOTATION] shouldBe 0
            featureList should haveFeatureAt(FeatureName.DOT_NOTATION, listOf())

            featureMap[FeatureName.DOTTED_METHOD_CALL] shouldBe 0
            featureList should haveFeatureAt(FeatureName.DOTTED_METHOD_CALL, listOf())

            featureMap[FeatureName.DOTTED_VARIABLE_ACCESS] shouldBe 0
            featureList should haveFeatureAt(FeatureName.DOTTED_VARIABLE_ACCESS, listOf())
        }
    }
    "should not choke on initializer blocks" {
        Source(
            mapOf(
                "Test.java" to """
public class Test {
    {
        System.out.println("Instance initializer");
    }
}
                """.trim()
            )
        ).features()
    }
    "should not choke on static initializer blocks" {
        Source(
            mapOf(
                "Test.java" to """
public class Test {
    static {
        System.out.println("Static initializer");
    }
}
                """.trim()
            )
        ).features()
    }
    "should not choke on pseudo-recursion" {
        Source(
            mapOf(
                "Catcher.java" to """
public class Catcher {
    public static int getValue(Faulter faulter) {
        while (true) {
            try {
                return faulter.getValue();
            } catch (Exception e) {
            }
        }
    }
}""".trim()
            )
        ).features()
    }
    "should not find static in snippets" {
        Source.fromJavaSnippet(
            "int i = 0;"
        ).features().check {
            featureMap[FeatureName.STATIC_METHOD] shouldBe 0
            featureList should haveFeatureAt(FeatureName.STATIC_METHOD, listOf())
        }
    }
    "should not count array.length as dotted variable access" {
        Source.fromJavaSnippet(
            """int[] array = new int[8];
              |int l = array.length;
            """.trimMargin()
        ).features().check {
            featureMap[FeatureName.DOTTED_VARIABLE_ACCESS] shouldBe 0
            featureList should haveFeatureAt(FeatureName.DOTTED_VARIABLE_ACCESS, listOf())

            featureMap[FeatureName.DOT_NOTATION] shouldBe 0
            featureList should haveFeatureAt(FeatureName.DOT_NOTATION, listOf())
        }
    }
    "should not count new with Strings and arrays" {
        Source.fromJavaSnippet(
            """String test = new String("test");
                |int[] test1 = new int[8];
                |int[] test2 = new int[] {1, 2, 4};
            """.trimMargin()
        ).features().check {
            featureMap[FeatureName.NEW_KEYWORD] shouldBe 0
            featureList should haveFeatureAt(FeatureName.NEW_KEYWORD, listOf())
        }
    }
    "should not count new with arrays" {
        Source.fromJavaSnippet(
            """int[] midThree(int[] values) {
                |  return new int[] {
                |    values[values.length / 2 - 1], values[values.length / 2], values[values.length / 2 + 1]
                |  };
                |}
            """.trimMargin()
        ).features().check {
            featureMap[FeatureName.NEW_KEYWORD] shouldBe 0
            featureList should haveFeatureAt(FeatureName.NEW_KEYWORD, listOf())
        }
    }
    "should not die on empty constructor" {
        Source(
            mapOf(
                "Simple.java" to """
public class Simple {
  private double val;

  public Simple() {
    val = 0;
  }

  public void setValue(double value) {
    val = value;
  }

  public double squared() {
    return val * val;
  }
}""".trim()
            )
        ).features()
    }
    "should identify and record dotted method calls and property access" {
        Source.fromJavaSnippet(
            """
int[] array = new int[] {1, 2, 4};
System.out.println(array.something);
array.sort();
int[] sorted = array.sorted();
            """.trimIndent()
        ).features().check {
            featureMap[FeatureName.DOTTED_METHOD_CALL] shouldBe 2
            featureList should haveFeatureAt(FeatureName.DOTTED_METHOD_CALL, listOf(3, 4))

            featureMap[FeatureName.DOTTED_VARIABLE_ACCESS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.DOTTED_VARIABLE_ACCESS, listOf(2))

            dottedMethodList shouldContainExactly setOf("sort", "sorted", "println")
        }
    }
    "should record dot in print" {
        Source.fromJavaSnippet(
            """
System.out.println(array.something);
            """.trimIndent()
        ).features().check {
            featureMap[FeatureName.DOTTED_METHOD_CALL] shouldBe 0
            featureList should haveFeatureAt(FeatureName.DOTTED_METHOD_CALL, listOf())

            featureMap[FeatureName.DOTTED_VARIABLE_ACCESS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.DOTTED_VARIABLE_ACCESS, listOf(1))
        }
    }
    "should not fail on repeated nested anonymous classes" {
        Source.fromJavaSnippet(
            """
public static IWhichHemisphere create(Position p) {
  double a = p.getLatitude();
  if (a == 0) {
    return new IWhichHemisphere() {
      public boolean isNorthern() {
        return false;
      }
      public boolean isSouthern() {
        return true;
      }
    };
  }
  if (a > 0) {
    return new IWhichHemisphere() {
      public boolean isNorthern() {
        return true;
      }
      public boolean isSouthern() {
        return false;
      }
    };
  } else {
    return new IWhichHemisphere() {
      public boolean isNorthern() {
        return false;
      }
      public boolean isSouthern() {
        return true;
      }
    };
  }
}""".trim()
        ).features().check("") {
            featureMap[FeatureName.ANONYMOUS_CLASSES] shouldBe 3
            featureList should haveFeatureAt(FeatureName.ANONYMOUS_CLASSES, listOf(4, 14, 23))
        }
    }
    "should allow top-level lambda methods" {
        Source.fromJavaSnippet(
            """
public interface Modify {
  int modify(int value);
}
public class Modifier {
  Modify modify = value -> value + 1;
}
""".trim()
        ).features().check("") {
            featureMap[FeatureName.LAMBDA_EXPRESSIONS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.LAMBDA_EXPRESSIONS, listOf(5))
        }
    }
    "should count calls to equals and reference equality" {
        Source.fromJavaSnippet(
            """
String t = "test";
System.out.println(t.equals("another"));
t == "another";
"string" == "another";
t == 1;
2 == 3;
""".trim()
        ).features().check("") {
            featureMap[FeatureName.EQUALITY] shouldBe 1
            featureList should haveFeatureAt(FeatureName.EQUALITY, listOf(2))

            featureMap[FeatureName.REFERENCE_EQUALITY] shouldBe 4
            featureList should haveFeatureAt(FeatureName.REFERENCE_EQUALITY, (3..6).toList())
        }
    }
})

fun haveFeatureAt(feature: FeatureName, lines: List<Int>) = object : Matcher<List<LocatedFeature>> {
    override fun test(value: List<LocatedFeature>): MatcherResult {
        val expectedLocations = lines.sorted()
        val actualLocations = value.filter { it.feature == feature }.map { it.location.line }.sorted()

        return MatcherResult(
            expectedLocations == actualLocations,
            { "Expected feature at $expectedLocations but found it at $actualLocations" },
            { "Expected feature at $expectedLocations but found it at $actualLocations" }
        )
    }
}
