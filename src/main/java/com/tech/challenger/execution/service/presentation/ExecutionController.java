package com.tech.challenger.execution.service.presentation;

import com.tech.challenger.execution.service.application.ExecutionService;
import com.tech.challenger.execution.service.application.dto.DiagnosisRequest;
import com.tech.challenger.execution.service.application.dto.ExecutionResponse;
import com.tech.challenger.execution.service.application.dto.RepairRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/executions")
@Tag(name = "Execucao", description = "Fila, diagnostico e reparos das ordens de servico")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @GetMapping("/queue")
    @Operation(summary = "Consultar fila de reparos")
    public ResponseEntity<List<ExecutionResponse>> queue() {
        return ResponseEntity.ok(executionService.findQueue());
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Consultar execucao da OS")
    public ResponseEntity<ExecutionResponse> find(@PathVariable Long orderId) {
        return ResponseEntity.ok(executionService.findByOrderId(orderId));
    }

    @PostMapping("/{orderId}/diagnosis/start")
    @Operation(summary = "Iniciar diagnostico")
    public ResponseEntity<ExecutionResponse> startDiagnosis(@PathVariable Long orderId) {
        return ResponseEntity.ok(executionService.startDiagnosis(orderId));
    }

    @PostMapping("/{orderId}/diagnosis")
    @Operation(summary = "Registrar diagnostico")
    public ResponseEntity<ExecutionResponse> recordDiagnosis(
            @PathVariable Long orderId, @Valid @RequestBody DiagnosisRequest request) {
        return ResponseEntity.ok(executionService.recordDiagnosis(orderId, request.diagnosis()));
    }

    @PostMapping("/{orderId}/repairs/start")
    @Operation(summary = "Iniciar reparos aprovados")
    public ResponseEntity<ExecutionResponse> startRepairs(@PathVariable Long orderId) {
        return ResponseEntity.ok(executionService.startRepairs(orderId));
    }

    @PostMapping("/{orderId}/repairs")
    @Operation(summary = "Registrar reparo executado")
    public ResponseEntity<ExecutionResponse> recordRepair(
            @PathVariable Long orderId, @Valid @RequestBody RepairRequest request) {
        return ResponseEntity.ok(executionService.recordRepair(orderId, request.description()));
    }

    @PostMapping("/{orderId}/complete")
    @Operation(summary = "Concluir execucao")
    public ResponseEntity<ExecutionResponse> complete(@PathVariable Long orderId) {
        return ResponseEntity.ok(executionService.complete(orderId));
    }
}
