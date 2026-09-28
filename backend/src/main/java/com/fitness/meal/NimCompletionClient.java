package com.fitness.meal;

public interface NimCompletionClient {

  boolean isConfigured();

  String complete(String systemPrompt, String userPrompt);
}
