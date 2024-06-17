package com.dws.challenge.domain;

import lombok.Data;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Data
public class TransferRequest {

    @NotEmpty(message = "AccountFromId is mandatory")
    private String accountFromId;

    @NotEmpty(message = "AccountToId is mandatory")
    private String accountToId;

    @NotNull(message = "Amount is mandatory")
    @Min(value = 0, message = "Transfer amount must be positive")
    private BigDecimal amount;
}
