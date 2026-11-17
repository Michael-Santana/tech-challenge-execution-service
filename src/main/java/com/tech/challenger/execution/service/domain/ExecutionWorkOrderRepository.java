package com.tech.challenger.execution.service.domain;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ExecutionWorkOrderRepository extends MongoRepository<ExecutionWorkOrder, String> {

    List<ExecutionWorkOrder> findByStatusOrderByQueuedAtAsc(ExecutionStatus status);

    @Query("{ 'pendingEvents.published': false }")
    List<ExecutionWorkOrder> findWithUnpublishedEvents();
}
