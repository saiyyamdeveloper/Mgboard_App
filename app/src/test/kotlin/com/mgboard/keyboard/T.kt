package com.mgboard.keyboard

/**
 * Minimal JVM test harness.
 *
 * Android ke `test` source-set mein rakha gaya hai taaki `./gradlew test` ise
 * chalaye, lekin ismein koi Android/JUnit dependency nahi — sirf `main()`.
 * Isse sandbox/CI mein bina Gradle ke bhi chalaya ja sakta hai:
 *
 *     ./scripts/run_jvm_tests.sh
 */
object T {
    private val results = ArrayList<Pair<Boolean, String>>()
    private var failures = ArrayList<String>()

    fun eq(name: String, actual: Any?, expected: Any?) {
        val ok = actual == expected
        results += ok to name
        if (!ok) failures += "  FAIL $name\n       got: ${show(actual)}\n       exp: ${show(expected)}"
    }

    fun ok(name: String, value: Boolean) = eq(name, value, true)

    private fun show(v: Any?): String = when (v) {
        null -> "null"
        is String -> "\"$v\"  (${com.mgboard.keyboard.CpText.codePointLabels(v)})"
        is List<*> -> v.joinToString(prefix = "[", postfix = "]") { show(it) }
        else -> v.toString()
    }

    fun section(name: String) { println("\n── $name") }

    fun report(): Int {
        val pass = results.count { it.first }
        failures.forEach { println(it) }
        println("\n" + "═".repeat(56))
        println((if (pass == results.size) "✅ ALL PASS  " else "❌ FAILURES  ") + "$pass/${results.size}")
        println("═".repeat(56))
        return if (pass == results.size) 0 else 1
    }

    fun reset() { results.clear(); failures = ArrayList() }
}
