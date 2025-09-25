package com.example.fittrack.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Advice {
    private Long id;

    @NotBlank
    private String text;

    private Long userId;
}