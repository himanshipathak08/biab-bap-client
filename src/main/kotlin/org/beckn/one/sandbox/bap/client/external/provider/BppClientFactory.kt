package org.beckn.one.sandbox.bap.client.external.provider

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.resilience4j.retrofit.CircuitBreakerCallAdapter
import io.github.resilience4j.retrofit.RetryCallAdapter
import io.github.resilience4j.retry.Retry
import okhttp3.OkHttpClient
import org.beckn.one.sandbox.bap.client.shared.security.SignRequestInterceptor
import org.beckn.one.sandbox.bap.factories.CircuitBreakerFactory
import org.beckn.one.sandbox.bap.factories.RetryFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.util.concurrent.TimeUnit

@Service
class BppClientFactory @Autowired constructor(
  val objectMapper: ObjectMapper,
  @Value("\${bpp_service.retry.max_attempts}")
  private val maxAttempts: Int,
  @Value("\${bpp_service.retry.initial_interval_in_millis}")
  private val initialIntervalInMillis: Long,
  @Value("\${bpp_service.retry.interval_multiplier}")
  private val intervalMultiplier: Double,
  @Value("\${beckn.security.enabled}") val enableSecurity: Boolean,
  private val interceptor: SignRequestInterceptor,
  @Value("\${bpp_service.timeouts.connection_in_seconds}") private val connectionTimeoutInSeconds: Long,
  @Value("\${bpp_service.timeouts.read_in_seconds}") private val readTimeoutInSeconds: Long,
  @Value("\${bpp_service.timeouts.write_in_seconds}") private val writeTimeoutInSeconds: Long,

  ) {
  @Cacheable("bppClients")
  fun getClient(bppUri: String): BppClient {
    val url = if (bppUri.endsWith("/")) bppUri else "$bppUri/"
    val retrofit = Retrofit.Builder()
      .baseUrl(url)
      .client(buildHttpClient())
      .addConverterFactory(JacksonConverterFactory.create(objectMapper))
      .addCallAdapterFactory(RetryCallAdapter.of(getRetryConfig(bppUri)))
      .addCallAdapterFactory(CircuitBreakerCallAdapter.of(CircuitBreakerFactory.create(bppUri)))
      .build()
    return retrofit.create(BppClient::class.java)
  }

  private fun buildHttpClient(): OkHttpClient {
    val httpClientBuilder = OkHttpClient.Builder()
      .connectTimeout(connectionTimeoutInSeconds, TimeUnit.SECONDS)
      .readTimeout(readTimeoutInSeconds, TimeUnit.SECONDS)
      .writeTimeout(writeTimeoutInSeconds, TimeUnit.SECONDS)
    if (enableSecurity) {
      httpClientBuilder.addInterceptor(interceptor)
    }
    return httpClientBuilder.build()
  }

  private fun getRetryConfig(bppUri: String): Retry {
    return RetryFactory.create(
      bppUri,
      maxAttempts,
      initialIntervalInMillis,
      intervalMultiplier
    )
  }
}