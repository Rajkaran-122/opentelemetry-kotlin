package io.opentelemetry.kotlin.tracing.sampling

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.attributes.CompatAttributesModel
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.error.FakeSdkErrorHandler
import io.opentelemetry.kotlin.factory.CompatContextFactory
import io.opentelemetry.kotlin.factory.hexToByteArray
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.sampling.SamplingResult.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalApi::class)
internal class CompatSamplerErrorHandlingTest {

    private val sdkErrorHandler = FakeSdkErrorHandler()
    private val contextFactory = CompatContextFactory()
    private val samplerDsl = CompatSamplerConfig(sdkErrorHandler)

    @Test
    fun `sampling predicate throws - returns false and reports error`() {
        val sampler = samplerDsl.composableRuleBased {
            rule(
                predicate = SamplingPredicate { _, _, _, _, _ -> throw RuntimeException("predicate error") },
                sampler = { composableAlwaysOn() },
            )
        }

        val adapter = samplerDsl.composite { sampler }
        val result = adapter.shouldSample(
            context = contextFactory.root(),
            traceIdBytes = ZERO_TRACE_ID,
            name = "span",
            spanKind = SpanKind.INTERNAL,
            attributes = CompatAttributesModel(),
            links = emptyList(),
        )

        // When predicate throws, rule doesn't match, so it falls through to default (never sample)
        assertEquals(Decision.DROP, result.decision)
        assertEquals(1, sdkErrorHandler.userCodeErrors.size)
        assertTrue(sdkErrorHandler.userCodeErrors.single().message.contains("SamplingPredicate.matches failed"))
        assertTrue(sdkErrorHandler.userCodeErrors.single().cause?.message == "predicate error")
    }

    @Test
    fun `sampling predicate succeeds - returns sampler decision`() {
        val sampler = samplerDsl.composableRuleBased {
            rule(
                predicate = SamplingPredicate { _, _, _, _, _ -> true },
                sampler = { composableAlwaysOn() },
            )
        }

        val adapter = samplerDsl.composite { sampler }
        val result = adapter.shouldSample(
            context = contextFactory.root(),
            traceIdBytes = ZERO_TRACE_ID,
            name = "span",
            spanKind = SpanKind.INTERNAL,
            attributes = CompatAttributesModel(),
            links = emptyList(),
        )

        assertEquals(Decision.RECORD_AND_SAMPLE, result.decision)
        assertEquals(0, sdkErrorHandler.userCodeErrors.size)
    }

    @Test
    fun `sampling predicate returns false - uses next rule or default`() {
        val sampler = samplerDsl.composableRuleBased {
            rule(
                predicate = SamplingPredicate { _, _, _, _, _ -> false },
                sampler = { composableAlwaysOn() },
            )
            rule(
                predicate = SamplingPredicate { _, _, _, _, _ -> true },
                sampler = { composableAlwaysOn() },
            )
        }

        val adapter = samplerDsl.composite { sampler }
        val result = adapter.shouldSample(
            context = contextFactory.root(),
            traceIdBytes = ZERO_TRACE_ID,
            name = "span",
            spanKind = SpanKind.INTERNAL,
            attributes = CompatAttributesModel(),
            links = emptyList(),
        )

        assertEquals(Decision.RECORD_AND_SAMPLE, result.decision)
        assertEquals(0, sdkErrorHandler.userCodeErrors.size)
    }

    @Test
    fun `composable sampler probability sampler works correctly`() {
        val sampler = samplerDsl.composableProbability(1.0)

        val adapter = samplerDsl.composite { sampler }
        val result = adapter.shouldSample(
            context = contextFactory.root(),
            traceIdBytes = ZERO_TRACE_ID,
            name = "span",
            spanKind = SpanKind.INTERNAL,
            attributes = CompatAttributesModel(),
            links = emptyList(),
        )

        assertEquals(Decision.RECORD_AND_SAMPLE, result.decision)
        assertEquals(0, sdkErrorHandler.userCodeErrors.size)
    }

    @Test
    fun `multiple failing callbacks - each reports error separately`() {
        val sampler = samplerDsl.composableRuleBased {
            rule(
                predicate = SamplingPredicate { _, _, _, _, _ -> throw RuntimeException("predicate 1") },
                sampler = { composableAlwaysOn() },
            )
            rule(
                predicate = SamplingPredicate { _, _, _, _, _ -> throw RuntimeException("predicate 2") },
                sampler = { composableAlwaysOn() },
            )
        }

        val adapter = samplerDsl.composite { sampler }
        adapter.shouldSample(
            context = contextFactory.root(),
            traceIdBytes = ZERO_TRACE_ID,
            name = "span",
            spanKind = SpanKind.INTERNAL,
            attributes = CompatAttributesModel(),
            links = emptyList(),
        )

        assertEquals(2, sdkErrorHandler.userCodeErrors.size)
        assertTrue(sdkErrorHandler.userCodeErrors[0].cause?.message == "predicate 1")
        assertTrue(sdkErrorHandler.userCodeErrors[1].cause?.message == "predicate 2")
    }

    private companion object {
        val ZERO_TRACE_ID = "00000000000000000000000000000000".hexToByteArray()
    }
}
