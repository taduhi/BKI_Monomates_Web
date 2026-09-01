package com.monomates.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DemoDatasetResourceTest {

  @Test
  void depositDatasetHasUniqueEventsAndDailySessions() throws IOException {
    List<Map<String, String>> rows = read("dataset/demo-deposits.tsv");
    Set<String> eventIds = new HashSet<>();
    Set<String> dailySessions = new HashSet<>();

    for (Map<String, String> row : rows) {
      assertThat(eventIds.add(row.get("event_id")))
        .as("event IDs must be unique")
        .isTrue();
      String dailyKey = String.join(
        "|",
        row.get("user_email"),
        row.get("bin_code"),
        row.get("days_ago")
      );
      assertThat(dailySessions.add(dailyKey))
        .as("one synthetic session per user, bin, and local date")
        .isTrue();
      assertThat(row.get("outcome"))
        .isIn("ACCEPTED_PET", "VALID_UNCERTAIN", "REJECTED");
    }

    assertThat(rows).hasSize(26);
  }

  @Test
  void pilotPolicyContainsOnlyClearPetAsActiveItem() throws IOException {
    List<Map<String, String>> rows = read(
      "dataset/demo-accepted-items.tsv"
    );

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().get("code")).isEqualTo("CLEAR_PET_BOTTLE");
    assertThat(rows.getFirst().get("active")).isEqualTo("true");
  }

  private List<Map<String, String>> read(String resourcePath)
    throws IOException {
    InputStream stream = Thread.currentThread()
      .getContextClassLoader()
      .getResourceAsStream(resourcePath);
    assertThat(stream).as(resourcePath).isNotNull();

    try (
      BufferedReader reader = new BufferedReader(
        new InputStreamReader(stream, StandardCharsets.UTF_8)
      )
    ) {
      String[] headers = reader.readLine().split("\\t", -1);
      List<Map<String, String>> rows = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank() || line.startsWith("#")) {
          continue;
        }
        String[] values = line.split("\\t", -1);
        assertThat(values).hasSize(headers.length);
        Map<String, String> row = new LinkedHashMap<>();
        for (int index = 0; index < headers.length; index++) {
          row.put(headers[index], values[index]);
        }
        rows.add(row);
      }
      return rows;
    }
  }
}
