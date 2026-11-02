package com.rova.banking.domain;

public final class NotFound {
  private NotFound() {}

  public static BusinessException resource(String name) {
    return new BusinessException(BusinessException.Code.RESOURCE_NOT_FOUND, name + " not found");
  }
}
