package com.invest.debate.infrastructure.web

import com.invest.debate.domain.model.DebateId
import com.invest.debate.domain.port.*
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/debate")
class DebateController(
    private val startDebateUseCase: StartDebateUseCase,
    private val getDebateReportUseCase: GetDebateReportUseCase
) {

    // POST /debate/start → 202 Accepted + debateId
    @PostMapping("/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun start(@RequestBody req: StartDebateRequest): Mono<StartDebateResponse> {
        return startDebateUseCase.startDebate(
            StartDebateCommand(
                userId = req.userId,
                ticker = req.ticker,
                thesis = req.thesis,
                currentPrice = req.price
            )
        ).map { debateId -> StartDebateResponse(debateId.toString()) }
    }

    // GET /debate/{id}/report → 레포트 조회
    @GetMapping("/{id}/report")
    fun getReport(@PathVariable id: String): Mono<DebateReportResponse> {
        val debateId: DebateId = UUID.fromString(id)
        return getDebateReportUseCase.getReport(debateId)
            .map { report ->
                DebateReportResponse(
                    consensus = report.consensus,
                    successProbability = report.successProbability,
                    disputes = report.disputes,
                    actions = report.actions
                )
            }
    }

    // GET /debate/{id}/status → 진행 상태 간단 조회 (React Native polling용)
    @GetMapping("/{id}/status")
    fun getStatus(@PathVariable id: String): Mono<StatusResponse> {
        val debateId: DebateId = UUID.fromString(id)
        return getDebateReportUseCase.getReport(debateId)
            .map { StatusResponse(debateId = id, status = "COMPLETED") }
            .onErrorReturn(StatusResponse(debateId = id, status = "IN_PROGRESS"))
    }
}

// ─── Request / Response DTO ────────────────────────────

data class StartDebateRequest(
    val userId: String,
    val ticker: String,
    val thesis: String,
    val price: Double? = null
)

data class StartDebateResponse(val debateId: String)

data class DebateReportResponse(
    val consensus: String,
    val successProbability: Int,
    val disputes: List<String>,
    val actions: List<String>
)

data class StatusResponse(
    val debateId: String,
    val status: String
)
