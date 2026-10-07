package com.sealease.backend.withdrawal.dto;

import java.util.List;

public record BatchDetail(BatchResponse batch, List<WithdrawalResponse> items) {
}
