package com.monomates.api.config;

import com.monomates.api.bin.AcceptedItemType;
import com.monomates.api.bin.AcceptedItemTypeRepository;
import com.monomates.api.bin.BinStatus;
import com.monomates.api.bin.Location;
import com.monomates.api.bin.LocationRepository;
import com.monomates.api.bin.RecyclingBin;
import com.monomates.api.bin.RecyclingBinRepository;
import com.monomates.api.common.model.BaseEntity;
import com.monomates.api.deposit.Deposit;
import com.monomates.api.deposit.DepositRepository;
import com.monomates.api.deposit.DepositSession;
import com.monomates.api.deposit.DepositSessionRepository;
import com.monomates.api.deposit.DepositStatus;
import com.monomates.api.device.Device;
import com.monomates.api.device.DeviceEvent;
import com.monomates.api.device.DeviceEventRepository;
import com.monomates.api.device.DeviceRepository;
import com.monomates.api.device.DeviceStatus;
import com.monomates.api.reward.TokenLedgerEntry;
import com.monomates.api.reward.TokenLedgerRepository;
import com.monomates.api.reward.TokenTransactionType;
import com.monomates.api.user.UserAccount;
import com.monomates.api.user.UserRepository;
import com.monomates.api.user.UserRole;
import com.monomates.api.user.UserStatus;
import com.monomates.api.voucher.Redemption;
import com.monomates.api.voucher.RedemptionRepository;
import com.monomates.api.voucher.Voucher;
import com.monomates.api.voucher.VoucherRepository;
import com.monomates.api.voucher.VoucherStatus;
import jakarta.persistence.EntityManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
public class DemoDataInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(
    DemoDataInitializer.class
  );
  private static final String DATASET_ROOT = "classpath:dataset/";
  private static final ZoneId HCMC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final String STARTING_BALANCE_DESCRIPTION =
    "Synthetic local dataset starting balance";

  private final UserRepository users;
  private final LocationRepository locations;
  private final AcceptedItemTypeRepository items;
  private final RecyclingBinRepository bins;
  private final DeviceRepository devices;
  private final VoucherRepository vouchers;
  private final DepositSessionRepository sessions;
  private final DeviceEventRepository events;
  private final DepositRepository deposits;
  private final RedemptionRepository redemptions;
  private final TokenLedgerRepository ledger;
  private final PasswordEncoder passwords;
  private final ResourceLoader resourceLoader;
  private final EntityManager entityManager;
  private final JdbcTemplate jdbc;

  @Value("${app.demo-data.enabled:true}")
  private boolean enabled;

  public DemoDataInitializer(
    UserRepository users,
    LocationRepository locations,
    AcceptedItemTypeRepository items,
    RecyclingBinRepository bins,
    DeviceRepository devices,
    VoucherRepository vouchers,
    DepositSessionRepository sessions,
    DeviceEventRepository events,
    DepositRepository deposits,
    RedemptionRepository redemptions,
    TokenLedgerRepository ledger,
    PasswordEncoder passwords,
    ResourceLoader resourceLoader,
    EntityManager entityManager,
    JdbcTemplate jdbc
  ) {
    this.users = users;
    this.locations = locations;
    this.items = items;
    this.bins = bins;
    this.devices = devices;
    this.vouchers = vouchers;
    this.sessions = sessions;
    this.events = events;
    this.deposits = deposits;
    this.redemptions = redemptions;
    this.ledger = ledger;
    this.passwords = passwords;
    this.resourceLoader = resourceLoader;
    this.entityManager = entityManager;
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!enabled) {
      log.info("Synthetic local dataset is disabled.");
      return;
    }

    Map<String, UserAccount> userMap = seedUsers();
    Map<String, AcceptedItemType> itemMap = seedAcceptedItems();
    Map<String, RecyclingBin> binMap = seedBins(itemMap);
    Map<String, Voucher> voucherMap = seedVouchers();

    seedStartingBalances(userMap);
    seedDepositHistory(userMap, binMap, itemMap);
    seedRedemptionHistory(userMap, voucherMap);

    log.info(
      "Synthetic local dataset ready: {} users, {} bins, {} deposits, {} vouchers, {} redemptions.",
      users.count(),
      bins.count(),
      deposits.count(),
      vouchers.count(),
      redemptions.count()
    );
    log.info(
      "Demo logins: user@monomates.local / User123!; admin@monomates.local / Admin123!; operator@monomates.local / Operator123!"
    );
  }

  private Map<String, UserAccount> seedUsers() {
    Map<String, UserAccount> result = new LinkedHashMap<>();
    for (DatasetRow row : readTsv("demo-users.tsv")) {
      String email = normalize(row.required("email"));
      UserAccount user = users
        .findByEmailIgnoreCase(email)
        .orElseGet(() ->
          users.save(
            new UserAccount(
              email,
              passwords.encode(row.required("password")),
              row.required("full_name"),
              row.enumValue("role", UserRole.class),
              row.enumValue("status", UserStatus.class)
            )
          )
        );
      user.setFullName(row.required("full_name"));
      result.put(email, user);
    }
    return result;
  }

  private Map<String, AcceptedItemType> seedAcceptedItems() {
    Map<String, AcceptedItemType> result = new LinkedHashMap<>();
    for (DatasetRow row : readTsv("demo-accepted-items.tsv")) {
      String code = row.required("code").toUpperCase(Locale.ROOT);
      AcceptedItemType item = items
        .findByCodeIgnoreCase(code)
        .orElseGet(() ->
          new AcceptedItemType(
            code,
            row.required("name"),
            row.optional("description"),
            row.intValue("base_tokens"),
            row.intValue("bonus_tokens"),
            row.booleanValue("active")
          )
        );
      item.update(
        row.required("name"),
        row.optional("description"),
        row.intValue("base_tokens"),
        row.intValue("bonus_tokens"),
        row.booleanValue("active")
      );
      result.put(code, items.save(item));
    }
    return result;
  }

  private Map<String, RecyclingBin> seedBins(
    Map<String, AcceptedItemType> itemMap
  ) {
    Map<String, RecyclingBin> result = new LinkedHashMap<>();
    Instant now = Instant.now();

    for (DatasetRow row : readTsv("demo-bins.tsv")) {
      String publicCode = row.required("public_code").toUpperCase(Locale.ROOT);
      RecyclingBin bin = bins
        .findByPublicCodeIgnoreCase(publicCode)
        .orElse(null);

      if (bin == null) {
        Location location = locations.save(
          new Location(
            row.required("location_name"),
            row.required("address"),
            row.decimalValue("latitude"),
            row.decimalValue("longitude")
          )
        );
        bin = new RecyclingBin(
          publicCode,
          row.required("name"),
          location,
          row.enumValue("status", BinStatus.class),
          row.intValue("capacity_percent")
        );
      } else {
        bin
          .getLocation()
          .update(
            row.required("location_name"),
            row.required("address"),
            row.decimalValue("latitude"),
            row.decimalValue("longitude")
          );
        locations.save(bin.getLocation());
        bin.update(
          row.required("name"),
          row.enumValue("status", BinStatus.class),
          row.intValue("capacity_percent")
        );
      }

      Set<AcceptedItemType> accepted = splitCodes(
        row.required("accepted_item_codes")
      )
        .stream()
        .map(code -> require(itemMap, code, "accepted item"))
        .collect(Collectors.toCollection(LinkedHashSet::new));
      bin.replaceAcceptedItems(accepted);
      RecyclingBin savedBin = bins.save(bin);

      DeviceStatus deviceStatus = row.enumValue(
        "device_status",
        DeviceStatus.class
      );
      Device device = devices
        .findByDeviceCodeIgnoreCase(row.required("device_code"))
        .orElseGet(() ->
          new Device(
            savedBin,
            row.required("device_code"),
            passwords.encode(row.required("device_secret")),
            deviceStatus,
            row.required("firmware_version")
          )
        );
      device.updateConfiguration(
        deviceStatus,
        row.required("firmware_version")
      );
      if (deviceStatus == DeviceStatus.ACTIVE) {
        device.heartbeat(row.required("firmware_version"), now.minusSeconds(60));
      }
      devices.save(device);
      result.put(publicCode, savedBin);
    }
    return result;
  }

  private Map<String, Voucher> seedVouchers() {
    Map<String, Voucher> result = new LinkedHashMap<>();
    Instant now = Instant.now();

    for (DatasetRow row : readTsv("demo-vouchers.tsv")) {
      String title = row.required("title");
      Instant validFrom = relativeInstant(
        now,
        row.intValue("valid_from_days_ago"),
        true
      );
      Instant validUntil = relativeInstant(
        now,
        row.intValue("valid_until_days_ahead"),
        false
      );
      Voucher voucher = vouchers
        .findByTitleIgnoreCase(title)
        .orElseGet(() ->
          vouchers.save(
            new Voucher(
              row.required("partner_name"),
              title,
              row.optional("description"),
              row.intValue("token_cost"),
              row.intValue("inventory"),
              validFrom,
              validUntil,
              row.enumValue("status", VoucherStatus.class),
              row.optional("image_url"),
              null,
              "Show the issued code at the partner counter.",
              title,
              1
            )
          )
        );
      result.put(
        row.required("seed_key").toUpperCase(Locale.ROOT),
        voucher
      );
    }
    return result;
  }

  private void seedStartingBalances(Map<String, UserAccount> userMap) {
    List<AuditUpdate> auditUpdates = new ArrayList<>();
    Instant openingTime = Instant.now().minusSeconds(60L * 60 * 24 * 60);

    for (DatasetRow row : readTsv("demo-users.tsv")) {
      int amount = row.intValue("starting_tokens");
      if (amount <= 0) {
        continue;
      }
      UserAccount user = require(
        userMap,
        normalize(row.required("email")),
        "user"
      );
      boolean exists = ledger.existsByUser_IdAndTransactionTypeAndDescription(
        user.getId(),
        TokenTransactionType.ADJUSTMENT,
        STARTING_BALANCE_DESCRIPTION
      );
      if (!exists) {
        TokenLedgerEntry entry = ledger.save(
          new TokenLedgerEntry(
            user,
            null,
            null,
            TokenTransactionType.ADJUSTMENT,
            amount,
            STARTING_BALANCE_DESCRIPTION
          )
        );
        auditUpdates.add(
          new AuditUpdate("token_ledger", entry, openingTime)
        );
      }
    }
    applyAuditUpdates(auditUpdates);
  }

  private void seedDepositHistory(
    Map<String, UserAccount> userMap,
    Map<String, RecyclingBin> binMap,
    Map<String, AcceptedItemType> itemMap
  ) {
    Map<UUID, Device> deviceByBin = devices
      .findAll()
      .stream()
      .collect(
        Collectors.toMap(
          d -> d.getBin().getId(),
          Function.identity(),
          (first, ignored) -> first,
          LinkedHashMap::new
        )
      );
    List<AuditUpdate> auditUpdates = new ArrayList<>();

    for (DatasetRow row : readTsv("demo-deposits.tsv")) {
      UserAccount user = require(
        userMap,
        normalize(row.required("user_email")),
        "user"
      );
      RecyclingBin bin = require(
        binMap,
        row.required("bin_code").toUpperCase(Locale.ROOT),
        "bin"
      );
      Device device = require(deviceByBin, bin.getId(), "device");
      String eventId = row.required("event_id");
      Instant eventTime = relativeLocalTime(
        row.intValue("days_ago"),
        row.required("local_time")
      );
      LocalDate sessionDate = eventTime.atZone(HCMC_ZONE).toLocalDate();

      if (
        events
          .findByDevice_IdAndExternalEventId(device.getId(), eventId)
          .isPresent() ||
        sessions.existsByUser_IdAndBin_IdAndSessionDate(
          user.getId(),
          bin.getId(),
          sessionDate
        )
      ) {
        continue;
      }

      DatasetOutcome outcome = row.enumValue(
        "outcome",
        DatasetOutcome.class
      );
      boolean valid = outcome != DatasetOutcome.REJECTED;
      boolean acceptedPet = outcome == DatasetOutcome.ACCEPTED_PET;
      BigDecimal weight = row.decimalValue("weight_grams");
      BigDecimal confidence = row.optionalDecimal(
        "classification_confidence"
      );
      AcceptedItemType item = valid
        ? require(itemMap, "CLEAR_PET_BOTTLE", "accepted item")
        : null;

      DepositSession session = new DepositSession(
        user,
        bin,
        eventTime,
        eventTime.plusSeconds(60),
        sessionDate
      );
      Instant decisionTime = eventTime.plusSeconds(20);
      if (valid) {
        session.complete(decisionTime);
      } else {
        session.reject(decisionTime);
      }
      session = sessions.save(session);

      DeviceEvent event = new DeviceEvent(
        device,
        eventId,
        eventTime.plusSeconds(15),
        decisionTime,
        valid,
        weight,
        valid ? "CLEAR_PET_BOTTLE" : null,
        confidence,
        "{\"source\":\"synthetic-local-dataset\",\"eventId\":\"" +
        eventId +
        "\"}"
      );
      if (valid) {
        event.markProcessed();
      } else {
        event.markRejected();
      }
      event = events.save(event);

      DepositStatus depositStatus = switch (outcome) {
        case ACCEPTED_PET -> DepositStatus.ACCEPTED;
        case VALID_UNCERTAIN -> DepositStatus.VALID_UNCLASSIFIED;
        case REJECTED -> DepositStatus.REJECTED;
      };
      Deposit deposit = deposits.save(
        new Deposit(
          session,
          event,
          item,
          depositStatus,
          valid,
          weight,
          confidence,
          valid
            ? null
            : "Synthetic rejected event: IR and weight validation failed.",
          decisionTime
        )
      );

      auditUpdates.add(new AuditUpdate("deposit_sessions", session, eventTime));
      auditUpdates.add(new AuditUpdate("device_events", event, decisionTime));
      auditUpdates.add(new AuditUpdate("deposits", deposit, decisionTime));

      int baseTokens = item == null ? 1 : item.getBaseTokens();
      int bonusTokens = item == null ? 0 : item.getBonusTokens();
      if (valid && baseTokens > 0) {
        TokenLedgerEntry base = ledger.save(
          new TokenLedgerEntry(
            user,
            deposit,
            null,
            TokenTransactionType.DEPOSIT_BASE,
            baseTokens,
            "Valid deposit at " + bin.getPublicCode()
          )
        );
        auditUpdates.add(
          new AuditUpdate("token_ledger", base, decisionTime)
        );
      }
      if (acceptedPet && bonusTokens > 0) {
        TokenLedgerEntry bonus = ledger.save(
          new TokenLedgerEntry(
            user,
            deposit,
            null,
            TokenTransactionType.PET_BONUS,
            bonusTokens,
            "Accepted clear PET bottle bonus"
          )
        );
        auditUpdates.add(
          new AuditUpdate("token_ledger", bonus, decisionTime.plusSeconds(1))
        );
      }
    }
    applyAuditUpdates(auditUpdates);
  }

  private void seedRedemptionHistory(
    Map<String, UserAccount> userMap,
    Map<String, Voucher> voucherMap
  ) {
    List<AuditUpdate> auditUpdates = new ArrayList<>();

    for (DatasetRow row : readTsv("demo-redemptions.tsv")) {
      String code = row.required("redemption_code");
      if (redemptions.findByRedemptionCode(code).isPresent()) {
        continue;
      }
      UserAccount user = require(
        userMap,
        normalize(row.required("user_email")),
        "user"
      );
      Voucher voucher = require(
        voucherMap,
        row.required("voucher_seed_key").toUpperCase(Locale.ROOT),
        "voucher"
      );
      Instant time = relativeLocalTime(
        row.intValue("days_ago"),
        row.required("local_time")
      );

      Redemption redemption = redemptions.save(
        new Redemption(user, voucher, voucher.getTokenCost(), code)
      );
      voucher.decrementInventory();
      vouchers.save(voucher);
      TokenLedgerEntry deduction = ledger.save(
        new TokenLedgerEntry(
          user,
          null,
          redemption,
          TokenTransactionType.REDEMPTION,
          -voucher.getTokenCost(),
          "Voucher redemption: " + voucher.getTitle()
        )
      );
      auditUpdates.add(new AuditUpdate("redemptions", redemption, time));
      auditUpdates.add(new AuditUpdate("token_ledger", deduction, time));
    }
    applyAuditUpdates(auditUpdates);
  }

  private List<DatasetRow> readTsv(String filename) {
    Resource resource = resourceLoader.getResource(DATASET_ROOT + filename);
    if (!resource.exists()) {
      throw new IllegalStateException(
        "Dataset resource does not exist: " + filename
      );
    }

    try (
      BufferedReader reader = new BufferedReader(
        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
      )
    ) {
      String headerLine = nextContentLine(reader);
      if (headerLine == null) {
        return List.of();
      }
      List<String> headers = Arrays.asList(headerLine.split("\\t", -1));
      List<DatasetRow> rows = new ArrayList<>();
      String line;
      int lineNumber = 1;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank() || line.stripLeading().startsWith("#")) {
          continue;
        }
        String[] values = line.split("\\t", -1);
        if (values.length != headers.size()) {
          throw new IllegalStateException(
            filename +
            ": line " +
            lineNumber +
            " has " +
            values.length +
            " columns but " +
            headers.size() +
            " were expected."
          );
        }
        Map<String, String> valueMap = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
          valueMap.put(headers.get(i).trim(), values[i].trim());
        }
        rows.add(new DatasetRow(filename, lineNumber, valueMap));
      }
      return rows;
    } catch (IOException exception) {
      throw new IllegalStateException(
        "Could not read dataset resource: " + filename,
        exception
      );
    }
  }

  private String nextContentLine(BufferedReader reader) throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      if (!line.isBlank() && !line.stripLeading().startsWith("#")) {
        return line;
      }
    }
    return null;
  }

  private void applyAuditUpdates(List<AuditUpdate> updates) {
    if (updates.isEmpty()) {
      return;
    }
    entityManager.flush();
    for (AuditUpdate update : updates) {
      updateAuditTimestamp(update.table(), update.entity().getId(), update.time());
    }
  }

  private void updateAuditTimestamp(String table, UUID id, Instant time) {
    String sql = switch (table) {
      case "deposit_sessions" ->
        "UPDATE deposit_sessions SET created_at=?, updated_at=? WHERE id=?";
      case "device_events" ->
        "UPDATE device_events SET created_at=?, updated_at=? WHERE id=?";
      case "deposits" ->
        "UPDATE deposits SET created_at=?, updated_at=? WHERE id=?";
      case "redemptions" ->
        "UPDATE redemptions SET created_at=?, updated_at=? WHERE id=?";
      case "token_ledger" ->
        "UPDATE token_ledger SET created_at=?, updated_at=? WHERE id=?";
      default -> throw new IllegalArgumentException(
        "Audit updates are not allowed for table " + table
      );
    };
    Timestamp timestamp = Timestamp.from(time);
    jdbc.update(sql, timestamp, timestamp, id);
  }

  private Instant relativeLocalTime(int daysAgo, String localTime) {
    LocalDate date = LocalDate.now(HCMC_ZONE).minusDays(daysAgo);
    return date
      .atTime(LocalTime.parse(localTime))
      .atZone(HCMC_ZONE)
      .toInstant();
  }

  private Instant relativeInstant(Instant now, int days, boolean ago) {
    long seconds = 60L * 60 * 24 * days;
    return ago ? now.minusSeconds(seconds) : now.plusSeconds(seconds);
  }

  private Set<String> splitCodes(String value) {
    return Arrays
      .stream(value.split("\\|"))
      .map(String::trim)
      .filter(code -> !code.isEmpty())
      .map(code -> code.toUpperCase(Locale.ROOT))
      .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private String normalize(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private <K, V> V require(Map<K, V> map, K key, String label) {
    V value = map.get(key);
    if (value == null) {
      throw new IllegalStateException(
        "Dataset references an unknown " + label + ": " + key
      );
    }
    return value;
  }

  private enum DatasetOutcome {
    ACCEPTED_PET,
    VALID_UNCERTAIN,
    REJECTED,
  }

  private record AuditUpdate(String table, BaseEntity entity, Instant time) {}

  private record DatasetRow(
    String filename,
    int lineNumber,
    Map<String, String> values
  ) {
    String required(String key) {
      String value = values.get(key);
      if (value == null || value.isBlank()) {
        throw new IllegalStateException(
          filename + ": line " + lineNumber + " requires column " + key
        );
      }
      return value;
    }

    String optional(String key) {
      String value = values.get(key);
      return value == null || value.isBlank() ? null : value;
    }

    int intValue(String key) {
      try {
        return Integer.parseInt(required(key));
      } catch (NumberFormatException exception) {
        throw invalid(key, "integer", exception);
      }
    }

    boolean booleanValue(String key) {
      String value = required(key).toLowerCase(Locale.ROOT);
      if ("true".equals(value)) {
        return true;
      }
      if ("false".equals(value)) {
        return false;
      }
      throw invalid(key, "boolean", null);
    }

    BigDecimal decimalValue(String key) {
      try {
        return new BigDecimal(required(key));
      } catch (NumberFormatException exception) {
        throw invalid(key, "decimal", exception);
      }
    }

    BigDecimal optionalDecimal(String key) {
      String value = optional(key);
      if (value == null) {
        return null;
      }
      try {
        return new BigDecimal(value);
      } catch (NumberFormatException exception) {
        throw invalid(key, "decimal", exception);
      }
    }

    <E extends Enum<E>> E enumValue(String key, Class<E> enumType) {
      try {
        return Enum.valueOf(
          enumType,
          required(key).toUpperCase(Locale.ROOT)
        );
      } catch (IllegalArgumentException exception) {
        throw invalid(key, enumType.getSimpleName(), exception);
      }
    }

    private IllegalStateException invalid(
      String key,
      String expected,
      Exception cause
    ) {
      return new IllegalStateException(
        filename +
        ": line " +
        lineNumber +
        " has an invalid " +
        expected +
        " value in column " +
        key,
        cause
      );
    }
  }
}
