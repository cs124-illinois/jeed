fun foo() {
    out
    1
    @a abstract class foof {}
    abstract @a class foof {}

    // out val foo = 5 // Bogus modifier
    @a var foo = 4
    typealias f =  T.() -> Unit
}
