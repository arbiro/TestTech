package com.dws.challenge;

import com.dws.challenge.domain.TransferRequest;
import com.dws.challenge.exception.AccountNotFoundException;
import com.dws.challenge.exception.InsufficientFundsException;
import com.dws.challenge.service.TransferService;
import com.dws.challenge.web.TransferController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(TransferController.class)
public class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransferService transferService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void transferSuccess() throws Exception {
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setAccountFromId("1");
        transferRequest.setAccountToId("2");
        transferRequest.setAmount(BigDecimal.valueOf(200.0));

        Mockito.doNothing().when(transferService).transfer(Mockito.any(TransferRequest.class));

        this.mockMvc.perform(post("/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"1\", \"accountToId\":\"2\", \"amount\":200.0}"))
                .andExpect(status().isOk());
    }

    @Test
    void transferInsufficientFunds() throws Exception {
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setAccountFromId("1");
        transferRequest.setAccountToId("2");
        transferRequest.setAmount(BigDecimal.valueOf(200.0));

        Mockito.doThrow(new InsufficientFundsException("Insufficient funds")).when(transferService).transfer(Mockito.any(TransferRequest.class));

        this.mockMvc.perform(post("/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"1\", \"accountToId\":\"2\", \"amount\":200.0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferAccountNotFound() throws Exception {
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setAccountFromId("1");
        transferRequest.setAccountToId("2");
        transferRequest.setAmount(BigDecimal.valueOf(200.0));

        Mockito.doThrow(new AccountNotFoundException("Account not found")).when(transferService).transfer(Mockito.any(TransferRequest.class));

        this.mockMvc.perform(post("/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"1\", \"accountToId\":\"2\", \"amount\":200.0}"))
                .andExpect(status().isBadRequest());
    }
}
