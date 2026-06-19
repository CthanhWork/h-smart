package com.hsmart.backend.service;

import java.util.List;

public interface EmbeddingClient {
    List<Double> embedText(String text);
}
