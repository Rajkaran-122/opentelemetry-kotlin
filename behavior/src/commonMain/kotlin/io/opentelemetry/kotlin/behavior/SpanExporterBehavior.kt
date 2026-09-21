package io.opentelemetry.kotlin.behavior

import io.opentelemetry.kotlin.ExperimentalApi

/**
 * Span exporter configuration.
 *
 * https://opentelemetry.io/docs/specs/otel/trace/sdk_exporters/
 */
@ExperimentalApi
sealed class SpanExporterBehavior : Behavior<SpanExporterBehavior> {

    /**
     * Console span exporter.
     *
     * https://opentelemetry.io/docs/specs/otel/trace/sdk_exporters/stdout/
     */
    data object Console : SpanExporterBehavior() {
        override fun mergeWith(higher: SpanExporterBehavior): SpanExporterBehavior = higher
    }
}
