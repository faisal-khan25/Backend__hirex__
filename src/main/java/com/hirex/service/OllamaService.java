package com.hirex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OllamaService — Production-Ready Ollama Integration
 *
 * Replaces OpenAIService entirely. All AI capabilities are now served
 * by a locally-running Ollama instance via its REST API.
 *
 * Key features:
 * 1. Communicates with Ollama /api/generate (non-streaming)
 * 2. Model is configurable via application properties (default: llama3)
 * 3. Circuit breaker pattern to fail fast on repeated Ollama outages
 * 4. Every public method has a safe fallback — interview flow never blocks
 * 5. Drop-in replacement for OpenAIService (same method signatures)
 */
@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);

    // ── Typed Exception ────────────────────────────────────────────────────

    public static class AIServiceException extends RuntimeException {
        private final int statusCode;
        private final String errorCategory;
        private final boolean shouldRetry;

        public AIServiceException(String message, int statusCode, String errorCategory, boolean shouldRetry) {
            super(message);
            this.statusCode    = statusCode;
            this.errorCategory = errorCategory;
            this.shouldRetry   = shouldRetry;
        }

        public int getStatusCode()       { return statusCode; }
        public String getErrorCategory() { return errorCategory; }
        public boolean shouldRetry()     { return shouldRetry; }

        public boolean isNetworkError()  { return "NETWORK".equals(errorCategory); }
        public boolean isServerError()   { return "SERVER".equals(errorCategory); }
        public boolean isTimeoutError()  { return "TIMEOUT".equals(errorCategory); }
    }

    // ── Circuit Breaker ────────────────────────────────────────────────────

    private static class CircuitBreaker {
        private final int failureThreshold;
        private final long resetAfterMs;
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private volatile long lastFailureTime = 0;

        CircuitBreaker(int failureThreshold, long resetAfterMs) {
            this.failureThreshold = failureThreshold;
            this.resetAfterMs     = resetAfterMs;
        }

        synchronized void recordFailure() {
            consecutiveFailures.incrementAndGet();
            lastFailureTime = System.currentTimeMillis();
        }

        synchronized void recordSuccess() {
            consecutiveFailures.set(0);
        }

        synchronized boolean isOpen() {
            if (consecutiveFailures.get() >= failureThreshold) {
                long elapsed = System.currentTimeMillis() - lastFailureTime;
                if (elapsed < resetAfterMs) return true;
                consecutiveFailures.set(0); // auto-reset after cooldown
            }
            return false;
        }

        int getFailureCount() { return consecutiveFailures.get(); }
    }

    private final CircuitBreaker circuitBreaker = new CircuitBreaker(5, 30_000);

    // ── Configuration ──────────────────────────────────────────────────────

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:llama3}")
    private String model;

    @Value("${ollama.temperature:0.7}")
    private double temperature;

    @Value("${ollama.num-predict:2000}")
    private int numPredict;

    @Value("${ollama.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${ollama.retry.base-delay-ms:1000}")
    private long retryBaseDelayMs;

    public OllamaService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // ── Public API (same signatures as the old OpenAIService) ─────────────

    /**
     * Generate a single text response from Ollama.
     * Throws AIServiceException on failure — never returns null.
     */
    public String generateText(String prompt) {
        String response = callOllamaWithRetry(prompt, "generateText");
        String text = extractTextFromResponse(response);
        if (text == null || text.isBlank()) {
            throw new AIServiceException(
                    "Ollama returned an empty response. Raw: " + response,
                    0, "EMPTY_RESPONSE", false);
        }
        return text;
    }

    /**
     * Generate multiple interview questions.
     * Falls back to static questions if Ollama is unavailable.
     * ALWAYS succeeds.
     */
    public List<String> generateQuestions(String prompt, int count) {
        try {
            String response = callOllamaWithRetry(prompt, "generateQuestions");
            String text = extractTextFromResponse(response);

            if (text == null || text.isBlank()) {
                log.warn("Ollama returned empty text for question generation; using fallback.");
                return generateFallbackQuestions(count);
            }

            List<String> questions = new ArrayList<>();
            for (String line : text.split("\n")) {
                String trimmed = line.trim()
                        .replaceAll("^\\d+\\.\\s*", "")
                        .replaceAll("^[-*]\\s*", "")
                        .trim();
                if (!trimmed.isEmpty() && trimmed.length() > 10) {
                    questions.add(trimmed);
                }
                if (questions.size() >= count) break;
            }

            if (questions.isEmpty()) {
                log.warn("Ollama question parsing yielded 0 questions; using fallback.");
                return generateFallbackQuestions(count);
            }
            return questions;

        } catch (AIServiceException e) {
            log.warn("Ollama error [{}] generating questions: {}. Using fallback.", e.getErrorCategory(), e.getMessage());
            return generateFallbackQuestions(count);
        } catch (Exception e) {
            log.error("Unexpected error generating questions", e);
            return generateFallbackQuestions(count);
        }
    }

    /**
     * Evaluate a single answer. Returns default neutral scores if Ollama is unavailable.
     * NEVER throws.
     */
    public Map<String, Double> evaluateAnswerQuality(String question, String answer) {
        String prompt = String.format(
                "Question: %s\n\nAnswer: %s\n\n"
                + "Evaluate this interview answer and provide scores (0.0 to 1.0) for:\n"
                + "- relevance: How well does the answer address the question?\n"
                + "- completeness: Did the candidate cover key points?\n"
                + "- clarity: How clearly was the answer expressed?\n"
                + "- confidence: Did the candidate speak with confidence?\n"
                + "Return ONLY a CSV in this exact format (no labels, no extra text):\n"
                + "relevance,completeness,clarity,confidence\n"
                + "Example: 0.8,0.7,0.9,0.8",
                question, answer
        );
        try {
            String response = generateText(prompt);
            // Strip any extra lines — take only the first CSV line
            String csvLine = response.lines()
                    .filter(l -> l.matches("[\\d.,\\s]+"))
                    .findFirst()
                    .orElse(response.trim());
            String[] scores = csvLine.split(",");
            if (scores.length < 4) return getDefaultScores();

            Map<String, Double> eval = new HashMap<>();
            eval.put("relevance",    Double.parseDouble(scores[0].trim()));
            eval.put("completeness", Double.parseDouble(scores[1].trim()));
            eval.put("clarity",      Double.parseDouble(scores[2].trim()));
            eval.put("confidence",   Double.parseDouble(scores[3].trim()));
            return eval;

        } catch (Exception e) {
            log.warn("Ollama answer evaluation failed ({}). Using default scores.", e.getMessage());
            return getDefaultScores();
        }
    }

    /**
     * Summarise interview Q&A. Returns placeholder if Ollama is unavailable.
     */
    public String summarizeInterview(List<Map<String, String>> qAndA) {
        StringBuilder prompt = new StringBuilder("Summarize this interview professionally:\n\n");
        for (Map<String, String> qa : qAndA) {
            prompt.append("Q: ").append(qa.get("question")).append("\n")
                  .append("A: ").append(qa.get("answer")).append("\n\n");
        }
        prompt.append("Provide a 2-3 sentence summary of the candidate's overall performance.");
        try {
            return generateText(prompt.toString());
        } catch (Exception e) {
            log.warn("Interview summary failed: {}", e.getMessage());
            return "Interview summary is temporarily unavailable. Your interview has been recorded.";
        }
    }

    /**
     * Generate feedback for a single answer.
     */
    public String generateFeedback(String question, String answer, String expectedPoints) {
        String prompt = String.format(
                "Question: %s\n\nExpected answer points: %s\n\nCandidate's answer: %s\n\n"
                + "Provide constructive feedback (2-3 sentences) on what went well and what could be improved.",
                question, expectedPoints, answer
        );
        try {
            return generateText(prompt);
        } catch (Exception e) {
            log.warn("Feedback generation failed: {}", e.getMessage());
            return "Feedback is temporarily unavailable. Your answer has been recorded.";
        }
    }

    /** Score relevance (0–1). Returns 0.5 if Ollama is unavailable. */
    public Double scoreRelevance(String question, String answer) {
        String prompt = String.format(
                "Question: %s\n\nAnswer: %s\n\n"
                + "On a scale of 0 to 1, how relevant is this answer to the question? "
                + "Return ONLY a decimal number, nothing else.",
                question, answer
        );
        try {
            return Double.parseDouble(generateText(prompt).trim());
        } catch (Exception e) {
            log.warn("Relevance scoring failed: {}", e.getMessage());
            return 0.5;
        }
    }

    /** Score completeness (0–1). Returns 0.5 if Ollama is unavailable. */
    public Double scoreCompleteness(String question, String answer, String expectedCoverage) {
        String prompt = String.format(
                "Expected coverage: %s\n\nCandidate's answer: %s\n\n"
                + "Rate how complete the answer is (0 to 1). Return ONLY a decimal number.",
                expectedCoverage, answer
        );
        try {
            return Double.parseDouble(generateText(prompt).trim());
        } catch (Exception e) {
            log.warn("Completeness scoring failed: {}", e.getMessage());
            return 0.5;
        }
    }

    /** Generate role-specific insights. Returns placeholder on failure. */
    public String generateRoleSpecificInsights(String jobRole, String candidateAnswers) {
        String prompt = String.format(
                "Job Role: %s\n\nCandidate Answers:\n%s\n\n"
                + "Generate 3-4 key insights about whether this candidate is suitable for the role.",
                jobRole, candidateAnswers
        );
        try {
            return generateText(prompt);
        } catch (Exception e) {
            log.warn("Role-specific insights failed: {}", e.getMessage());
            return "Role insights are temporarily unavailable. Your interview has been recorded.";
        }
    }

    /** Detect red flags in an answer. Returns empty list on failure. */
    public List<String> detectRedFlags(String question, String answer) {
        String prompt = String.format(
                "Question: %s\n\nAnswer: %s\n\n"
                + "Identify potential red flags in this answer. "
                + "Return a comma-separated list, or the single word NONE if there are none.",
                question, answer
        );
        try {
            String response = generateText(prompt);
            if ("NONE".equalsIgnoreCase(response.trim())) return new ArrayList<>();
            return Arrays.asList(response.split(","));
        } catch (Exception e) {
            log.warn("Red-flag detection failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Health check — returns false when Ollama is unreachable.
     * Does NOT throw.
     */
    public boolean isHealthy() {
        try {
            String response = generateText("Reply with exactly: OK");
            return response != null && !response.isBlank();
        } catch (Exception e) {
            log.warn("Ollama health check failed: {}", e.getMessage());
            return false;
        }
    }

    /** Usage stats for the /config endpoint. */
    public Map<String, Object> getUsageStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("provider",    "Ollama");
        stats.put("model",       model);
        stats.put("baseUrl",     ollamaBaseUrl);
        stats.put("temperature", temperature);
        stats.put("numPredict",  numPredict);
        stats.put("healthy",     isHealthy());
        stats.put("circuitBreaker", Map.of(
                "isOpen",              circuitBreaker.isOpen(),
                "consecutiveFailures", circuitBreaker.getFailureCount()
        ));
        return stats;
    }

    // ── Private: HTTP layer ────────────────────────────────────────────────

    /**
     * Call Ollama with exponential-backoff retries on retryable errors.
     */
    private String callOllamaWithRetry(String prompt, String operationName) {
        if (circuitBreaker.isOpen()) {
            log.error("Circuit breaker OPEN — Ollama has failed too many times recently.");
            throw new AIServiceException(
                    "Ollama service is temporarily unavailable. Please try again later.",
                    503, "CIRCUIT_BREAKER", false);
        }

        int    attempt = 0;
        long   delay   = retryBaseDelayMs;

        while (true) {
            attempt++;
            try {
                String result = callOllama(prompt, operationName);
                circuitBreaker.recordSuccess();
                return result;

            } catch (AIServiceException e) {
                circuitBreaker.recordFailure();
                log.warn("[{}/{}] Ollama call failed [{}]: {}", attempt, maxRetryAttempts,
                        e.getErrorCategory(), e.getMessage());

                if (!e.shouldRetry() || attempt >= maxRetryAttempts) {
                    log.error("Ollama call giving up after {} attempt(s). Category: {}",
                            attempt, e.getErrorCategory());
                    throw e;
                }

                log.info("Retrying Ollama call in {}ms (attempt {}/{}).", delay, attempt, maxRetryAttempts);
                try { Thread.sleep(delay); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                delay = Math.min(delay * 2, 15_000);
            }
        }
    }

    /**
     * Single HTTP call to Ollama /api/generate (non-streaming).
     */
    private String callOllama(String prompt, String operationName) {
        String url = ollamaBaseUrl.replaceAll("/+$", "") + "/api/generate";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model",  model);
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false); // get the full response in one shot
        requestBody.put("options", Map.of(
                "temperature", temperature,
                "num_predict", numPredict
        ));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        log.debug("[{}] POST {} model={}", operationName, url, model);
        long t0 = System.currentTimeMillis();

        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            long elapsed = System.currentTimeMillis() - t0;

            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.error("[{}] Ollama HTTP {} (elapsed {}ms)", operationName,
                        resp.getStatusCode().value(), elapsed);
                throw new AIServiceException(
                        "Ollama returned HTTP " + resp.getStatusCode().value(),
                        resp.getStatusCode().value(), "SERVER", true);
            }

            log.debug("[{}] Ollama responded in {}ms", operationName, elapsed);
            return resp.getBody();

        } catch (AIServiceException e) {
            throw e;
        } catch (ResourceAccessException e) {
            long elapsed = System.currentTimeMillis() - t0;
            log.error("[{}] Cannot reach Ollama at {} (elapsed {}ms): {}",
                    operationName, url, elapsed, e.getMessage());
            throw new AIServiceException(
                    "Cannot reach Ollama at " + url + ". Is Ollama running? Error: " + e.getMessage(),
                    503, "NETWORK", true);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - t0;
            log.error("[{}] Unexpected error calling Ollama (elapsed {}ms): {}",
                    operationName, elapsed, e.getMessage(), e);
            throw new AIServiceException(
                    "Unexpected error communicating with Ollama: " + e.getMessage(),
                    500, "UNKNOWN", false);
        }
    }

    /**
     * Parse the Ollama /api/generate response.
     * Expected format: { "response": "...", "done": true, ... }
     */
    private String extractTextFromResponse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(raw);

            // /api/generate (non-streaming) → "response" field
            JsonNode responseNode = root.get("response");
            if (responseNode != null && !responseNode.isNull()) {
                return responseNode.asText().trim();
            }

            // /api/chat → "message.content"
            JsonNode message = root.path("message");
            if (!message.isMissingNode()) {
                JsonNode content = message.get("content");
                if (content != null) return content.asText().trim();
            }

            log.warn("Could not extract text from Ollama response. Keys: {}", root.fieldNames());
            return null;
        } catch (Exception e) {
            log.error("Failed to parse Ollama JSON response: {}", e.getMessage());
            return null;
        }
    }

    // ── Fallback responses ─────────────────────────────────────────────────

    private List<String> generateFallbackQuestions(int count) {
        List<String> pool = Arrays.asList(
                "Tell me about your most significant professional accomplishment.",
                "Describe a challenging situation you faced and how you resolved it.",
                "What are your key strengths and how do you apply them at work?",
                "How do you stay current with industry trends and best practices?",
                "What motivates you to excel in your career?",
                "Describe your experience working in a team environment.",
                "How do you approach learning a new skill or technology?",
                "Where do you see your career in the next three to five years?",
                "How do you handle constructive feedback and criticism?",
                "Describe a time when you had to adapt quickly to a significant change."
        );
        return pool.subList(0, Math.min(count, pool.size()));
    }

    private Map<String, Double> getDefaultScores() {
        Map<String, Double> d = new HashMap<>();
        d.put("relevance",    0.5);
        d.put("completeness", 0.5);
        d.put("clarity",      0.5);
        d.put("confidence",   0.5);
        return d;
    }
}
