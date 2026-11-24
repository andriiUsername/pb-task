package privatbank.task.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import privatbank.task.dto.response.WalletResponse;
import privatbank.task.service.WalletService;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/{walletId}")
    public WalletResponse getWallet(@PathVariable long walletId) {
        return walletService.getWallet(walletId);
    }
}
