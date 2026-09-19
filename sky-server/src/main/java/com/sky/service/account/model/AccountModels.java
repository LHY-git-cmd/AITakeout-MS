package com.sky.service.account.model;

/** 模拟账户领域服务的命令与结果模型。 */
public final class AccountModels {

    private AccountModels() {
    }

    public record GrantResult(long transferId, String transferNo, long accountId,
                              long amountCent, long balanceAfterCent, boolean replayed) {
    }

    public record AdjustmentResult(long transferId, String transferNo, long operatorId,
                                   long userId, long accountId, long deltaCent, String reason,
                                   long balanceBeforeCent, long balanceAfterCent, boolean replayed) {
    }

    public record TransferCommand(String businessKey, long sourceAccountId, long targetAccountId,
                                  long amountCent, String transferType, Long targetMaxAvailableCent) {
        public TransferCommand(String businessKey, long sourceAccountId, long targetAccountId,
                               long amountCent, String transferType) {
            this(businessKey, sourceAccountId, targetAccountId, amountCent, transferType, null);
        }
    }

    public record TransferResult(long transferId, String transferNo, String businessKey,
                                 long sourceAccountId, long targetAccountId, long amountCent,
                                 long sourceBalanceAfterCent, long targetBalanceAfterCent,
                                 boolean replayed) {
    }

    public record FreezeCommand(String businessKey, long accountId, long amountCent,
                                String transferType) {
    }

    public record FreezeResult(long transferId, String transferNo, long accountId,
                               long amountCent, long availableAfterCent, long frozenAfterCent,
                               boolean replayed) {
    }

    public record AccountView(long id, long ownerId, String accountNo, String accountType,
                              long availableCent, long frozenCent, int version) {
    }
}
