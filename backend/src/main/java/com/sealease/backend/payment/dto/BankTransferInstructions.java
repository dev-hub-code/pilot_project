package com.sealease.backend.payment.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.util.List;

/**
 * How to pay a pending bank payment.
 *
 * @param reference quoted on the payment (transfer remarks, back of the cheque, deposit slip) so it
 *                  can be matched to the order
 * @param accounts  the company accounts the investor may pay into
 */
public record BankTransferInstructions(String reference, MoneyResponse amount, List<CompanyBankAccountResponse> accounts) {
}
