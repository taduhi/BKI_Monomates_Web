package com.monomates.api.bin;

import com.monomates.api.common.model.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "locations")
public class Location extends BaseEntity {

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, length = 500)
  private String address;

  private BigDecimal latitude;
  private BigDecimal longitude;

  protected Location() {}

  public Location(String n, String a, BigDecimal lat, BigDecimal lon) {
    name = n;
    address = a;
    latitude = lat;
    longitude = lon;
  }

  public String getName() {
    return name;
  }

  public String getAddress() {
    return address;
  }

  public BigDecimal getLatitude() {
    return latitude;
  }

  public BigDecimal getLongitude() {
    return longitude;
  }

  public void update(String n, String a, BigDecimal lat, BigDecimal lon) {
    name = n;
    address = a;
    latitude = lat;
    longitude = lon;
  }
}
