package com.monomates.api.testing;

import com.monomates.api.bin.RecyclingBinRepository;
import com.monomates.api.deposit.Deposit;
import com.monomates.api.deposit.DepositRepository;
import com.monomates.api.deposit.DepositStatus;
import com.monomates.api.user.UserRepository;
import com.monomates.api.voucher.RedemptionRepository;
import com.monomates.api.voucher.VoucherRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping("/api/v1/testing/dataset-summary")
public class DatasetSummaryController {

  private final UserRepository users;
  private final RecyclingBinRepository bins;
  private final DepositRepository deposits;
  private final VoucherRepository vouchers;
  private final RedemptionRepository redemptions;

  public DatasetSummaryController(
    UserRepository users,
    RecyclingBinRepository bins,
    DepositRepository deposits,
    VoucherRepository vouchers,
    RedemptionRepository redemptions
  ) {
    this.users = users;
    this.bins = bins;
    this.deposits = deposits;
    this.vouchers = vouchers;
    this.redemptions = redemptions;
  }

  @GetMapping
  public Map<String, Object> summary() {
    List<Deposit> depositList = deposits.findAll();
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("dataset", "synthetic-local");
    summary.put("realPilotData", false);
    summary.put("users", users.count());
    summary.put("bins", bins.count());
    summary.put("vouchers", vouchers.count());
    summary.put("deposits", depositList.size());
    summary.put(
      "acceptedPet",
      count(depositList, DepositStatus.ACCEPTED)
    );
    summary.put(
      "validUnclassified",
      count(depositList, DepositStatus.VALID_UNCLASSIFIED)
    );
    summary.put("rejected", count(depositList, DepositStatus.REJECTED));
    summary.put("redemptions", redemptions.count());
    return summary;
  }

  private long count(List<Deposit> deposits, DepositStatus status) {
    return deposits
      .stream()
      .filter(deposit -> deposit.getVerificationStatus() == status)
      .count();
  }
}
