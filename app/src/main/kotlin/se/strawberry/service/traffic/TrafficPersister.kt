package se.strawberry.service.traffic

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import se.strawberry.repository.traffic.RecordedRequestRepository
import se.strawberry.repository.traffic.RecordedRequestRepository.RecordedRequest
import java.util.concurrent.atomic.AtomicBoolean

class TrafficPersister(
    private val repository: RecordedRequestRepository,
    private val broadcaster: TrafficBroadcastService? = null
) {
    private val log = LoggerFactory.getLogger(javaClass)
    // Channel to buffer requests
    val channel = Channel<RecordedRequest>(Channel.UNLIMITED)
    private val running = AtomicBoolean(false)

    fun start(scope: CoroutineScope) {
        if (running.getAndSet(true)) return

        scope.launch(Dispatchers.IO) {
            log.info("TrafficPersister started")
            for (request in channel) {
                // Launch independent jobs to avoid one blocking the other (e.g., DB retries vs WebSocket)
                launch {
                    try {
                        repository.save(request)
                    } catch (e: Exception) {
                        log.error("Failed to persist request ${request.id}", e)
                    }
                }
                
                launch {
                    try {
                        broadcaster?.broadcast(request)
                    } catch (e: Exception) {
                        log.error("Failed to broadcast request ${request.id}", e)
                    }
                }
            }
        }
    }
    
    fun capture(request: RecordedRequest) {
        val result = channel.trySend(request)
        if (result.isFailure) {
            log.warn("Traffic buffer full, dropping request ${request.id}")
        }
    }

    suspend fun submit(request: RecordedRequest) {
        channel.send(request)
    }

    fun close() {
        channel.close()
    }
}
