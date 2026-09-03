package com.borrowbox.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssetCreateRequest(
        @NotBlank String title,
        String description,
        Long categoryId,
        @NotNull @Min(1) Integer quantity
) {
}
