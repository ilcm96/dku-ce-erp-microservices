package erp.approvalrequest.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record ApprovalCreateRequest(
        @NotBlank String title, @NotBlank String content, @NotEmpty List<StepDto> steps) {

    public record StepDto(@NotNull Integer step, @NotNull Long approverId) {}
}
