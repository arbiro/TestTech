package com.dws.challenge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.TransferRequest;
import com.dws.challenge.exception.AccountNotFoundException;
import com.dws.challenge.exception.InsufficientFundsException;
import com.dws.challenge.repository.AccountsRepository;
import com.dws.challenge.service.AccountsService;
import com.dws.challenge.service.NotificationService;
import com.dws.challenge.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@ExtendWith(MockitoExtension.class)
public class TransferServiceTest {

    @InjectMocks
    private TransferService transferService;

    @Mock
    private AccountsService accountsService;

    @Mock
    private AccountsRepository accountsRepository;

    @Mock
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        lenient().when(accountsService.getAccountsRepository()).thenReturn(accountsRepository);
    }

    @Test
    void testTransferSuccess() throws InsufficientFundsException, AccountNotFoundException {
        Account accountFrom = new Account("1", BigDecimal.valueOf(1000.0));
        Account accountTo = new Account("2", BigDecimal.valueOf(500.0));
        TransferRequest request = new TransferRequest();
        request.setAccountFromId("1");
        request.setAccountToId("2");
        request.setAmount(new BigDecimal("200.0"));

        when(accountsRepository.getAccount("1")).thenReturn(accountFrom);
        when(accountsRepository.getAccount("2")).thenReturn(accountTo);

        transferService.transfer(request);

        assertEquals(BigDecimal.valueOf(800.0), accountFrom.getBalance());
        assertEquals(BigDecimal.valueOf(700.0), accountTo.getBalance());

        verify(notificationService).notifyAboutTransfer(accountFrom, "Transferred 200.0 to account 2");
        verify(notificationService).notifyAboutTransfer(accountTo, "Received 200.0 from account 1");
    }

    @Test
    void testTransferInsufficientFunds() {
        Account accountFrom = new Account("1", BigDecimal.valueOf(100.0));
        Account accountTo = new Account("2", BigDecimal.valueOf(500.0));
        TransferRequest request = new TransferRequest();
        request.setAccountFromId("1");
        request.setAccountToId("2");
        request.setAmount(new BigDecimal("200.0"));

        when(accountsRepository.getAccount("1")).thenReturn(accountFrom);
        when(accountsRepository.getAccount("2")).thenReturn(accountTo);

        InsufficientFundsException thrown = assertThrows(InsufficientFundsException.class, () -> {
            transferService.transfer(request);
        });

        assertEquals("Insufficient funds in account: 1", thrown.getMessage());
    }

    @Test
    void testTransferAccountNotFound() {
        TransferRequest request = new TransferRequest();
        request.setAccountFromId("1");
        request.setAccountToId("2");
        request.setAmount(new BigDecimal("200.0"));

        when(accountsRepository.getAccount("1")).thenReturn(null);

        AccountNotFoundException thrown = assertThrows(AccountNotFoundException.class, () -> {
            transferService.transfer(request);
        });

        assertEquals("Account not found: 1", thrown.getMessage());
    }

    @Test
    void testConcurrentTransfers() throws InterruptedException {
        Account accountFrom = new Account("1", BigDecimal.valueOf(1000.0));
        Account accountTo = new Account("2", BigDecimal.valueOf(500.0));

        when(accountsRepository.getAccount("1")).thenReturn(accountFrom);
        when(accountsRepository.getAccount("2")).thenReturn(accountTo);

        int numberOfThreads = 10;
        BigDecimal transferAmount = BigDecimal.valueOf(100.0);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    TransferRequest request = new TransferRequest();
                    request.setAccountFromId("1");
                    request.setAccountToId("2");
                    request.setAmount(transferAmount);
                    transferService.transfer(request);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertEquals(BigDecimal.valueOf(1000.0 - numberOfThreads * transferAmount.doubleValue()), accountFrom.getBalance());
        assertEquals(BigDecimal.valueOf(500.0 + numberOfThreads * transferAmount.doubleValue()), accountTo.getBalance());

        verify(notificationService, times(numberOfThreads)).notifyAboutTransfer(eq(accountFrom), anyString());
        verify(notificationService, times(numberOfThreads)).notifyAboutTransfer(eq(accountTo), anyString());
    }
}
