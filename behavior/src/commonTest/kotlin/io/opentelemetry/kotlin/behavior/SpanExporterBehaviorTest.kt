package io.opentelemetry.kotlin.behavior

import kotlin.test.Test
import kotlin.test.assertSame

internal class SpanExporterBehaviorTest {

    @Test
    fun consoleIsSingleton() {
        assertSame(SpanExporterBehavior.Console, SpanExporterBehavior.Console)
    }

    @Test
    fun consoleMergesToHigher() {
        assertSame(SpanExporterBehavior.Console, SpanExporterBehavior.Console.mergeWith(SpanExporterBehavior.Console))
    }
}
