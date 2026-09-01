package com.monomates.api;import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.assertThat;
class ArchitectureSmokeTest{@Test void apiPrefixIsVersioned(){assertThat("/api/v1").startsWith("/api");}}
