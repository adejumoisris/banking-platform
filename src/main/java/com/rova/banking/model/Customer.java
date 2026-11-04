package com.rova.banking.model;

import com.rova.banking.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "customer")
public class Customer {
  @Id private UUID id;

  @Column(name = "full_name", length = 160, nullable = false)
  private String fullName;

  @Column(length = 254, nullable = false)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(name = "kyc_status", length = 20, nullable = false)
  private Status kycStatus;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Customer() {}

  public enum Status {
    PENDING,
    VERIFIED
  }

  public static Customer create(String fullName, String email, Instant now) {
    if (fullName == null
        || fullName.isBlank()
        || fullName.trim().length() > 160
        || email == null
        || email.length() > 254
        || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
      throw BusinessException.invalid("Valid customer name and email are required");
    }
    Customer customer = new Customer();
    customer.id = UUID.randomUUID();
    customer.fullName = fullName.trim();
    customer.email = email.trim().toLowerCase(Locale.ROOT);
    customer.kycStatus = Status.PENDING;
    customer.createdAt = Objects.requireNonNull(now);
    return customer;
  }

  public void verify() {
    kycStatus = Status.VERIFIED;
  }

  public void requireVerified() {
    if (kycStatus != Status.VERIFIED) throw BusinessException.rule("Customer KYC is not verified");
  }

  public UUID getId() {
    return id;
  }

  public String getFullName() {
    return fullName;
  }

  public String getEmail() {
    return email;
  }

  public Status getKycStatus() {
    return kycStatus;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
