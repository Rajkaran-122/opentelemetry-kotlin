package io.opentelemetry.kotlin.tracing.sampling

import io.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.opentelemetry.kotlin.aliases.OtelJavaComposableSampler
import io.opentelemetry.kotlin.aliases.OtelJavaContext
import io.opentelemetry.kotlin.aliases.OtelJavaLinkData
import io.opentelemetry.kotlin.aliases.OtelJavaSamplingIntent
import io.opentelemetry.kotlin.aliases.OtelJavaSpanKind
import io.opentelemetry.kotlin.aliases.OtelJavaTraceState
import io.opentelemetry.kotlin.attributes.CompatAttributesModel
import io.opentelemetry.kotlin.attributes.toOtelJavaAttributes
import io.opentelemetry.kotlin.context.toOtelKotlinContext
import io.opentelemetry.kotlin.error.SdkErrorHandler
import io.opentelemetry.kotlin.error.guardOrDefault
import io.opentelemetry.kotlin.error.userCode
import io.opentelemetry.kotlin.tracing.TraceState
import io.opentelemetry.kotlin.tracing.ext.toOtelJavaTraceState
import io.opentelemetry.kotlin.tracing.ext.toOtelKotlinSpanKind
import io.opentelemetry.kotlin.tracing.ext.toOtelKotlinSpanLink
import io.opentelemetry.kotlin.tracing.ext.toOtelKotlinTraceState
import java.util.function.Function

/** Sentinel used when the delegate's intent is `null` (never sample); `R` is always < 2^56. */
private const val NEVER_SAMPLE_THRESHOLD: Long = 1L shl 56

/** Fallback SamplingIntent used when user code throws. */
private class FallbackSamplingIntent : SamplingIntent {
    override val threshold: Long? = NEVER_SAMPLE_THRESHOLD
    override val adjustedCountReliable: Boolean = false
    override val attributesProvider: (() -> io.opentelemetry.kotlin.attributes.AttributeContainer)? = null
    override val traceStateProvider: ((TraceState, SamplingResult.Decision) -> TraceState)? = null
}

internal class KotlinComposableSamplerAdapter(
    private val delegate: ComposableSampler,
    private val sdkErrorHandler: SdkErrorHandler,
) : OtelJavaComposableSampler {

    override fun getSamplingIntent(
        context: OtelJavaContext,
        traceId: String,
        name: String,
        spanKind: OtelJavaSpanKind,
        attributes: OtelJavaAttributes,
        links: List<OtelJavaLinkData>,
    ): OtelJavaSamplingIntent {
        val intent = sdkErrorHandler.guardOrDefault(
            FallbackSamplingIntent(),
            "ComposableSampler.getSamplingIntent failed",
        ) {
            userCode {
                delegate.getSamplingIntent(
                    context.toOtelKotlinContext(),
                    name,
                    spanKind.toOtelKotlinSpanKind(),
                    CompatAttributesModel(attributes.toBuilder()),
                    links.map { it.toOtelKotlinSpanLink() },
                )
            }
        }

        val threshold = intent.threshold ?: NEVER_SAMPLE_THRESHOLD
        val javaAttributes = sdkErrorHandler.guardOrDefault(
            OtelJavaAttributes.empty(),
            "SamplingIntent.attributesProvider failed",
        ) {
            userCode { intent.attributesProvider?.invoke()?.toOtelJavaAttributes() }
                ?: OtelJavaAttributes.empty()
        }
        val traceStateProvider = intent.traceStateProvider
        val traceStateUpdater: Function<OtelJavaTraceState, OtelJavaTraceState> = if (traceStateProvider == null) {
            Function.identity()
        } else {
            Function { javaTraceState ->
                sdkErrorHandler.guardOrDefault(
                    javaTraceState,
                    "SamplingIntent.traceStateProvider failed",
                ) {
                    userCode {
                        traceStateProvider(javaTraceState.toOtelKotlinTraceState(), SamplingResult.Decision.RECORD_AND_SAMPLE)
                            .toOtelJavaTraceState()
                    }
                }
            }
        }

        return OtelJavaSamplingIntent.create(threshold, intent.adjustedCountReliable, javaAttributes, traceStateUpdater)
    }

    override fun getDescription(): String = delegate.description
}
