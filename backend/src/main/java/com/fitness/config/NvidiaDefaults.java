package com.fitness.config;

/**
 * Defaults for the NVIDIA NIM OpenAI-compatible API. Keep these aligned with
 * {@code application.yml} and the README.
 */
public final class NvidiaDefaults {

  public static final String BASE_URL = "https://integrate.api.nvidia.com/v1";

  /** Instruction model used for meal calorie and macro estimates. */
  public static final String MODEL = "meta/llama-3.3-70b-instruct";

  public static final int TIMEOUT_SECONDS = 25;

  private NvidiaDefaults() {}
}
