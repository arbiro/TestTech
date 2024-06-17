package com.dws.challenge.service;

import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.TransferRequest;
import com.dws.challenge.exception.AccountNotFoundException;
import com.dws.challenge.exception.InsufficientFundsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class TransferService {

    private final AccountsService accountsService;
    private final NotificationService notificationService;
    private final ConcurrentHashMap<String, Lock> accountLocks = new ConcurrentHashMap<>();

    @Autowired
    public TransferService(AccountsService accountsService, NotificationService notificationService) {
        this.accountsService = accountsService;
        this.notificationService = notificationService;
    }

    public void transfer(TransferRequest transferRequest) throws InsufficientFundsException, AccountNotFoundException {
        validateTransferRequest(transferRequest);

        Account accountFrom = getAccountOrThrow(transferRequest.getAccountFromId());
        Account accountTo = getAccountOrThrow(transferRequest.getAccountToId());

        Lock lock1 = getLockForAccount(accountFrom.getAccountId());
        Lock lock2 = getLockForAccount(accountTo.getAccountId());

        try {
            if (!tryLockBoth(lock1, lock2)) {
                throw new IllegalStateException("Unable to acquire locks, operation timed out");
            }
            performTransfer(accountFrom, accountTo, transferRequest.getAmount());
        } finally {
            lock1.unlock();
            lock2.unlock();
        }
    }

    private void validateTransferRequest(TransferRequest transferRequest) {
        if (transferRequest.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
    }

    private Account getAccountOrThrow(String accountId) throws AccountNotFoundException {
        Account account = accountsService.getAccountsRepository().getAccount(accountId);
        if (account == null) {
            throw new AccountNotFoundException("Account not found: " + accountId);
        }
        return account;
    }

    private Lock getLockForAccount(String accountId) {
        return accountLocks.computeIfAbsent(accountId, k -> new ReentrantLock());
    }

    private boolean tryLockBoth(Lock lock1, Lock lock2) {
        boolean acquired = false;
        try {
            acquired = lock1.tryLock(10, TimeUnit.SECONDS);
            if (acquired) {
                acquired = lock2.tryLock(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return acquired;
    }

    private void performTransfer(Account accountFrom, Account accountTo, BigDecimal amount) throws InsufficientFundsException {
        if (accountFrom.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds in account: " + accountFrom.getAccountId());
        }

        accountFrom.debit(amount);
        accountTo.credit(amount);

        notificationService.notifyAboutTransfer(accountFrom, "Transferred " + amount + " to account " + accountTo.getAccountId());
        notificationService.notifyAboutTransfer(accountTo, "Received " + amount + " from account " + accountFrom.getAccountId());
    }
}
