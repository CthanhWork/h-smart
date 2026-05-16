package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.PolicySearchResult;
import java.util.List;

public interface PolicySearchClient {
    List<PolicySearchResult> searchPolicies(String query);
}
