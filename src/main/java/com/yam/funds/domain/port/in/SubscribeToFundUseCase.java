package com.yam.funds.domain.port.in;

import com.yam.funds.domain.exception.AlreadySubscribedException;
import com.yam.funds.domain.exception.ClientNotFoundException;
import com.yam.funds.domain.exception.FundNotFoundException;
import com.yam.funds.domain.exception.IdempotencyConflictException;
import com.yam.funds.domain.exception.InsufficientBalanceException;
import com.yam.funds.domain.exception.MinimumAmountNotMetException;
import com.yam.funds.domain.exception.OperationInProgressException;
import com.yam.funds.domain.port.in.command.SubscribeToFundCommand;
import com.yam.funds.domain.port.in.result.TransactionReceipt;
import reactor.core.publisher.Mono;

/** Links a client to a fund, debiting the linked amount from their balance. */
public interface SubscribeToFundUseCase {

    /**
     * Subscribes the client to the fund.
     *
     * <p>The balance debit, the subscription, the ledger entry and the notification
     * event are committed as a single unit: either all of them land or none does.
     * Running twice with the same idempotency key returns the first result rather than
     * moving money again.
     *
     * @param command the subscription request, carrying its idempotency key
     * @return the resulting ledger entry, flagged as replayed when the key had already
     *         been used with an identical request
     * @throws ClientNotFoundException      if the client does not exist
     * @throws FundNotFoundException        if the fund does not exist
     * @throws AlreadySubscribedException   if the client already holds an active
     *                                      subscription to the fund
     * @throws MinimumAmountNotMetException if the amount is below the fund's minimum
     * @throws InsufficientBalanceException if the balance does not cover the amount
     * @throws IdempotencyConflictException if the key was reused with a different request
     * @throws OperationInProgressException if a request with the same key is still running
     */
    Mono<TransactionReceipt> subscribeToFund(SubscribeToFundCommand command);
}
