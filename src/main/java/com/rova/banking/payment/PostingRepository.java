package com.rova.banking.payment;

import java.util.*;

public interface PostingRepository {
  Optional<Posting> find(UUID id);

  Optional<Posting> findByKey(String key);

  Posting save(Posting posting);
}
