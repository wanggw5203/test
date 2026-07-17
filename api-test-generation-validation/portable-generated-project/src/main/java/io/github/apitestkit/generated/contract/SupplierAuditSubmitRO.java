package io.github.apitestkit.generated.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class SupplierAuditSubmitRO {
    @JsonProperty("employment_state")
    private String employmentState;

    @JsonProperty("position_id")
    private Long positionId;

    private List<String> ids;

    public String getEmploymentState() { return employmentState; }
    public void setEmploymentState(String employmentState) { this.employmentState = employmentState; }
    public Long getPositionId() { return positionId; }
    public void setPositionId(Long positionId) { this.positionId = positionId; }
    public List<String> getIds() { return ids; }
    public void setIds(List<String> ids) { this.ids = ids; }
}
