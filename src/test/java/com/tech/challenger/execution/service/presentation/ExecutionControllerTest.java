package com.tech.challenger.execution.service.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tech.challenger.execution.service.application.ExecutionService;
import com.tech.challenger.execution.service.application.dto.DiagnosisRequest;
import com.tech.challenger.execution.service.application.dto.ExecutionResponse;
import com.tech.challenger.execution.service.application.dto.RepairRequest;
import com.tech.challenger.execution.service.domain.ExecutionStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ExecutionControllerTest {

    private ExecutionService service;
    private ExecutionController controller;
    private ExecutionResponse response;

    @BeforeEach
    void setUp() {
        service = mock(ExecutionService.class);
        controller = new ExecutionController(service);
        response = new ExecutionResponse(5L, ExecutionStatus.QUEUED_FOR_REPAIR,
                "Falha", true, false, null, null, null, null, null, List.of(), 1);
    }

    @Test
    void exposesQueueAndOrderQueries() {
        when(service.findQueue()).thenReturn(List.of(response));
        when(service.findByOrderId(5L)).thenReturn(response);

        assertThat(controller.queue().getBody()).containsExactly(response);
        assertThat(controller.find(5L).getBody()).isEqualTo(response);
        verify(service).findByOrderId(5L);
    }

    @Test
    void delegatesDiagnosisRepairAndCompletionActions() {
        when(service.startDiagnosis(5L)).thenReturn(response);
        when(service.recordDiagnosis(5L, "Falha")).thenReturn(response);
        when(service.startRepairs(5L)).thenReturn(response);
        when(service.recordRepair(5L, "Troca")).thenReturn(response);
        when(service.complete(5L)).thenReturn(response);

        assertThat(controller.startDiagnosis(5L).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.recordDiagnosis(5L, new DiagnosisRequest("Falha")).getBody()).isEqualTo(response);
        assertThat(controller.startRepairs(5L).getBody()).isEqualTo(response);
        assertThat(controller.recordRepair(5L, new RepairRequest("Troca")).getBody()).isEqualTo(response);
        assertThat(controller.complete(5L).getBody()).isEqualTo(response);
        verify(service).recordDiagnosis(5L, "Falha");
        verify(service).recordRepair(5L, "Troca");
    }
}
