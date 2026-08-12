package com.shutdowntracker.api.approval;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRepository {

    /**
     * Marks every non-terminal approval for the source entity superseded.
     *
     * @return the number of records superseded
     */
    int supersedeActiveApprovals(UUID projectId, String sourceEntityType, UUID sourceEntityId);

    ApprovalRecord create(UUID projectId, UUID reviewedByUserId, ApprovalRecordCreateRequest request,
                          Map<String, Object> metadata);

    Optional<ApprovalRecord> findLatest(UUID projectId, String sourceEntityType, UUID sourceEntityId);

    List<ApprovalRecord> listBySourceEntity(UUID projectId, String sourceEntityType, UUID sourceEntityId);
}
