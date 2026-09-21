package io.opentelemetry.kotlin.behavior

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class SpanProcessorBehaviorTest {

    @Test
    fun simpleExporterStartsUnset() {
        assertNull(SpanProcessorBehavior.Simple().exporter)
    }

    @Test
    fun batchExporterStartsUnset() {
        assertNull(SpanProcessorBehavior.Batch().exporter)
    }

    @Test
    fun simpleStaysUnsetWhenNeitherLayerConfiguredExporter() {
        assertNull(
            SpanProcessorBehavior.Simple()
                .mergeWith(SpanProcessorBehavior.Simple()).exporter,
        )
    }

    @Test
    fun batchStaysUnsetWhenNeitherLayerConfiguredExporter() {
        assertNull(
            SpanProcessorBehavior.Batch()
                .mergeWith(SpanProcessorBehavior.Batch()).exporter,
        )
    }

    @Test
    fun simpleAdoptsExporterFromWhicheverLayerSuppliedIt() {
        val exporter = SpanExporterBehavior.Console

        assertEquals(
            exporter,
            SpanProcessorBehavior.Simple()
                .mergeWith(SpanProcessorBehavior.Simple(exporter = exporter)).exporter,
        )
        assertEquals(
            exporter,
            SpanProcessorBehavior.Simple(exporter = exporter)
                .mergeWith(SpanProcessorBehavior.Simple()).exporter,
        )
    }

    @Test
    fun batchAdoptsExporterFromWhicheverLayerSuppliedIt() {
        val exporter = SpanExporterBehavior.Console

        assertEquals(
            exporter,
            SpanProcessorBehavior.Batch()
                .mergeWith(SpanProcessorBehavior.Batch(exporter = exporter)).exporter,
        )
        assertEquals(
            exporter,
            SpanProcessorBehavior.Batch(exporter = exporter)
                .mergeWith(SpanProcessorBehavior.Batch()).exporter,
        )
    }

    @Test
    fun mergingDifferentProcessorTypesUsesHigher() {
        val lower = SpanProcessorBehavior.Simple(exporter = SpanExporterBehavior.Console)
        val higher = SpanProcessorBehavior.Batch(exporter = SpanExporterBehavior.Console)

        val result = lower.mergeWith(higher)
        assertTrue(result is SpanProcessorBehavior.Batch)
    }
}
