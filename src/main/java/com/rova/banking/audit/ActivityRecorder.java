package com.rova.banking.audit;

import java.util.UUID;

/**
 * Records a committed business action, atomically with its state change. No customer PII in
 * payloads.
 */
public interface ActivityRecorder {
  void record(String action, UUID aggregateId);
}
