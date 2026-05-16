package com.hsmart.backend.service;

import java.util.List;

public interface PolicySearchService {
    List<String> findRelevantPolicyChunks(String query);
}
