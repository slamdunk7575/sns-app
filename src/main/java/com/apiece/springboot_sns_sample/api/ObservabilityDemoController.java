package com.apiece.springboot_sns_sample.api;

import com.apiece.springboot_sns_sample.api.demo.ErrorResponse;
import com.apiece.springboot_sns_sample.api.demo.TraceResponse;
import com.apiece.springboot_sns_sample.domain.recommend.RecommendService;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/demo")
public class ObservabilityDemoController {

    private final RecommendService recommendService;
    private final OpenTelemetry openTelemetry;

    @GetMapping("/trace")
    public ResponseEntity<TraceResponse> trace(
            @RequestParam(defaultValue = "hello") String message,
            @RequestParam(defaultValue = "1") Long userId,
            @RequestParam(defaultValue = "trace") String scenario
    ) {
        if (!"trace".equals(scenario) && !"incident".equals(scenario)) {
            return ResponseEntity.badRequest().build();
        }

        log.info("[STEP 1] 요청 수신 message={}, userID={}, scenario={}", message, userId, scenario);

        // recommend API 호출시 넘길 span 생성 (userId 값 포함)
        Span span = openTelemetry.getTracer("sns-app.demo")
                .spanBuilder("recommend-fetch")
                .setAttribute("user.id", userId)
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            try {
                List<Long> rankedPostIds = recommendService.recommend(userId);
                log.info("[STEP 3] 요청 처리 완료");
                return ResponseEntity.ok(new TraceResponse(message, rankedPostIds));

            } catch (RuntimeException e) {
                span.setStatus(StatusCode.ERROR, "recommend request failed");
                span.recordException(e);

                if ("incident".equals(scenario) || !(e instanceof RestClientException)) {
                    throw e;
                }

                log.error("장애 분석 요처 실패 userId={}", userId, e);
                return ResponseEntity
                        .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                        .body(new TraceResponse(message, null));
            }

        } finally {
            span.end();
        }
    }


    @GetMapping("/ok")
    public ResponseEntity<String> ok() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/slow")
    public ResponseEntity<String> slow() throws InterruptedException {
        Thread.sleep(2000);
        return ResponseEntity.ok("slow");
    }

    @GetMapping("/error")
    public ResponseEntity<ErrorResponse> error() {
        log.info("[STEP 1] 오류 재현 요청 수신");
        log.warn("[STEP 2] 처리중 이상 징후 발견");

        try {
            throw new RuntimeException("simulated error for observability demo");
        } catch (RuntimeException e) {
            log.error("[STEP 3] 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("error", e.getMessage()));
        }
    }
}
