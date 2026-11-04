package com.rova.banking.repository;

import com.rova.banking.model.Posting;
import java.util.*;

public interface PostingRepository {
  Optional<Posting> find(UUID id);

  Optional<Posting> findByKey(String key);

  Posting save(Posting posting);
}
