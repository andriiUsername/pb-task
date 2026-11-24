package privatbank.task.model;

import java.util.Set;

public enum FundingRequestStatus {
    APPROVED,
    SUSPENDED,
    PARTIALLY_PAID,
    FULLY_PAID;

    public static final Set<FundingRequestStatus> ACTIVE = Set.of(APPROVED, PARTIALLY_PAID);
}
