package com.agentframework.service;

import com.agentframework.data.entity.*;
import com.agentframework.data.repository.*;
import com.agentframework.dto.*;
import com.agentframework.registry.MCPTool;
import com.agentframework.registry.MCPToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for predicting MCP tools based on user queries.
 * 
 * Uses a Bootstrap Training Strategy:
 * 
 * Phase 1 (BOOTSTRAP): Use LLM for predictions, store results as training data
 * Phase 2 (TRAINING): Model is being trained from collected data
 * Phase 3 (HYBRID): ML + LLM fallback (accuracy 80-90%)
 * Phase 4 (ML_PRIMARY): ML primary, LLM only for edge cases (accuracy > 90%)
 * 
 * This approach uses LLM as a "teacher" to train a local DistilBERT classifier,
 * progressively reducing LLM token usage as the local model improves.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolPredictionService {

    private final MCPToolRegistry toolRegistry;
    private final LLMService llmService;

    // Repositories (optional - may be null if data module not configured)
    private final Optional<ToolPredictionRepository> predictionRepository;
    private final Optional<PredictionFeedbackRepository> feedbackRepository;
    private final Optional<ToolLearningStatRepository> learningStatRepository;
    private final Optional<KeywordToolWeightRepository> keywordWeightRepository;
    private final Optional<TrainingSampleRepository> trainingSampleRepository;
    private final Optional<ModelTrainingStatusRepository> trainingStatusRepository;

    private final WebClient.Builder webClientBuilder;

    @Value("${ml.service.url:http://localhost:8001}")
    private String mlServiceUrl;

    @Value("${ml.service.enabled:false}")
    private boolean mlServiceEnabled;

    @Value("${prediction.default-method:hybrid}")
    private String defaultPredictionMethod;

    @Value("${prediction.min-samples-for-training:100}")
    private int minSamplesForTraining;

    // Estimated tokens per LLM call (for savings calculation)
    private static final int ESTIMATED_TOKENS_PER_LLM_CALL = 500;

    /**
     * Predict tools for a query asynchronously.
     */
    @Async("toolPredictionExecutor")
    public CompletableFuture<ToolPredictionResponse> predictToolsAsync(ToolPredictionRequest request) {
        return CompletableFuture.completedFuture(predictToolsInternal(request));
    }

    /**
     * Predict tools for a query synchronously (with transaction support).
     */
    @Transactional
    public ToolPredictionResponse predictTools(ToolPredictionRequest request) {
        return predictToolsInternal(request);
    }

    /**
     * Internal prediction logic using Bootstrap Training Strategy.
     * 
     * Phases:
     * - BOOTSTRAP: Use keyword + LLM, store LLM predictions as training data
     * - HYBRID: Try ML first, fallback to LLM if low confidence
     * - ML_PRIMARY: Use ML, LLM only for edge cases
     */
    private ToolPredictionResponse predictToolsInternal(ToolPredictionRequest request) {
        long startTime = System.currentTimeMillis();
        String queryText = request.getQuery();

        log.info("Predicting tools for query: '{}'", queryText);

        try {
            // Get current training status
            ModelTrainingStatus status = getOrCreateTrainingStatus();
            String phase = status.getCurrentPhase();
            log.info("Current training phase: {}, accuracy: {}", phase, status.getCurrentAccuracy());

            // If force method is specified, use it
            if (request.getForceMethod() != null) {
                return predictWithForcedMethod(request, startTime);
            }

            // Route based on training phase
            return switch (phase) {
                case ModelTrainingStatus.PHASE_BOOTSTRAP, ModelTrainingStatus.PHASE_TRAINING -> 
                    predictInBootstrapPhase(request, status, startTime);
                case ModelTrainingStatus.PHASE_HYBRID -> 
                    predictInHybridPhase(request, status, startTime);
                case ModelTrainingStatus.PHASE_ML_PRIMARY -> 
                    predictInMlPrimaryPhase(request, status, startTime);
                default -> 
                    predictInBootstrapPhase(request, status, startTime);
            };
        } catch (Exception e) {
            log.error("Error predicting tools for query: {}", queryText, e);
            return ToolPredictionResponse.error(queryText, e.getMessage());
        }
    }

    /**
     * Predict using forced method (ignores training phase).
     */
    private ToolPredictionResponse predictWithForcedMethod(ToolPredictionRequest request, long startTime) {
        String method = request.getForceMethod().toLowerCase();
        log.info("Using forced method: {}", method);

        return switch (method) {
            case "ml_classifier" -> {
                if (mlServiceEnabled) {
                    yield predictWithMLService(request, startTime);
                }
                log.warn("ML service not enabled, falling back to hybrid");
                yield predictWithHybridStrategy(request, startTime);
            }
            case "hybrid" -> predictWithHybridStrategy(request, startTime);
            case "keyword" -> predictWithKeywordStrategy(request, startTime);
            case "llm" -> predictWithLLMStrategy(request, startTime);
            case "cosine" -> predictWithCosineStrategy(request, startTime);
            default -> predictWithHybridStrategy(request, startTime);
        };
    }

    /**
     * PHASE: BOOTSTRAP
     * Use keyword matching first, then LLM for better accuracy.
     * Store LLM predictions as training data for future model training.
     */
    private ToolPredictionResponse predictInBootstrapPhase(ToolPredictionRequest request, 
                                                           ModelTrainingStatus status, 
                                                           long startTime) {
        log.info("Bootstrap phase: Using keyword + LLM, collecting training data");

        List<MCPTool> availableTools = toolRegistry.getAllTools();
        
        // Step 1: Try keyword-based prediction first (fast, no cost)
        KeywordPredictionResult keywordResult = predictWithLearnedKeywords(request.getQuery(), availableTools);

        // Step 2: If keyword confidence is high, use it but still call LLM for training data
        // Step 3: Always call LLM in bootstrap phase to collect training data
        if (llmService.isEnabled()) {
            try {
                LLMService.LLMAnalysisResult llmResult = llmService.analyzeAgentDescription(
                        "Tool Selection", request.getQuery(), availableTools);

                // Record LLM call
                recordLlmCall();

                if (llmResult != null && llmResult.getSelectedTools() != null && !llmResult.getSelectedTools().isEmpty()) {
                    List<ToolPredictionResponse.PredictedTool> llmTools = llmResult.getSelectedTools().stream()
                            .map(toolName -> buildPredictedTool(toolName, 0.90, "Selected by LLM (training data)"))
                            .filter(Objects::nonNull)
                            .toList();

                    if (!llmTools.isEmpty()) {
                        // Store LLM prediction as training sample
                        storeAsTrainingSample(request.getQuery(), llmResult.getSelectedTools(), "llm_teacher");

                        ToolPredictionResponse response = buildPredictionResponse(request, llmTools, 0.90,
                                "bootstrap (llm)", llmResult.getReasoning(), startTime);

                        // Check if we have enough samples to trigger training
                        checkAndTriggerTraining(status);

                        return response;
                    }
                }
            } catch (Exception e) {
                log.warn("LLM prediction failed in bootstrap phase: {}", e.getMessage());
            }
        }

        // Fallback to keyword result
        return buildPredictionResponse(request, keywordResult.tools(), keywordResult.confidence(),
                "bootstrap (keyword-fallback)", keywordResult.reasoning(), startTime);
    }

    /**
     * PHASE: HYBRID
     * ML model has 80-90% accuracy. Use ML first, fallback to LLM if confidence is low.
     */
    private ToolPredictionResponse predictInHybridPhase(ToolPredictionRequest request, 
                                                         ModelTrainingStatus status, 
                                                         long startTime) {
        log.info("Hybrid phase: ML primary with LLM fallback");

        // Step 1: Try ML classifier first
        if (mlServiceEnabled) {
            try {
                ToolPredictionResponse mlResult = callMLService(request);
                
                if (mlResult != null && mlResult.getConfidence() >= status.getMinConfidenceForMl().doubleValue()) {
                    // ML confidence is high enough, use it and save LLM tokens
                    recordSavedLlmCall();
                    mlResult.setPredictionMethod("hybrid (ml)");
                    mlResult.setPredictionTimeMs((int)(System.currentTimeMillis() - startTime));
                    log.info("ML prediction confidence {} >= threshold {}, using ML result",
                            mlResult.getConfidence(), status.getMinConfidenceForMl());
                    return mlResult;
                }
                
                log.info("ML confidence {} < threshold {}, falling back to LLM",
                        mlResult != null ? mlResult.getConfidence() : 0, status.getMinConfidenceForMl());
            } catch (Exception e) {
                log.warn("ML service failed in hybrid phase: {}", e.getMessage());
            }
        }

        // Step 2: ML confidence was low or failed, use LLM
        return predictWithLLMAndStore(request, startTime, "hybrid (llm-fallback)");
    }

    /**
     * PHASE: ML_PRIMARY
     * ML model has >90% accuracy. Use ML for everything, LLM only for edge cases.
     */
    private ToolPredictionResponse predictInMlPrimaryPhase(ToolPredictionRequest request, 
                                                            ModelTrainingStatus status, 
                                                            long startTime) {
        log.info("ML Primary phase: Using ML classifier");

        if (mlServiceEnabled) {
            try {
                ToolPredictionResponse mlResult = callMLService(request);
                
                if (mlResult != null && mlResult.getConfidence() >= 0.5) {
                    // Use ML result
                    recordSavedLlmCall();
                    mlResult.setPredictionMethod("ml_primary");
                    mlResult.setPredictionTimeMs((int)(System.currentTimeMillis() - startTime));
                    return mlResult;
                }
                
                // Very low confidence - edge case, use LLM
                log.info("ML confidence very low ({}), using LLM for edge case", 
                        mlResult != null ? mlResult.getConfidence() : 0);
            } catch (Exception e) {
                log.warn("ML service failed in ML primary phase: {}", e.getMessage());
            }
        }

        // Fallback to LLM for edge cases
        return predictWithLLMAndStore(request, startTime, "ml_primary (llm-edge-case)");
    }

    /**
     * Use LLM for prediction and store result as training sample.
     */
    private ToolPredictionResponse predictWithLLMAndStore(ToolPredictionRequest request, 
                                                           long startTime, 
                                                           String methodLabel) {
        List<MCPTool> availableTools = toolRegistry.getAllTools();

        if (!llmService.isEnabled()) {
            // Fall back to keyword if LLM not available
            KeywordPredictionResult keywordResult = predictWithLearnedKeywords(request.getQuery(), availableTools);
            return buildPredictionResponse(request, keywordResult.tools(), keywordResult.confidence(),
                    methodLabel + " (keyword)", keywordResult.reasoning(), startTime);
        }

        try {
            LLMService.LLMAnalysisResult llmResult = llmService.analyzeAgentDescription(
                    "Tool Selection", request.getQuery(), availableTools);

            recordLlmCall();

            if (llmResult != null && llmResult.getSelectedTools() != null && !llmResult.getSelectedTools().isEmpty()) {
                List<ToolPredictionResponse.PredictedTool> llmTools = llmResult.getSelectedTools().stream()
                        .map(toolName -> buildPredictedTool(toolName, 0.90, "Selected by LLM"))
                        .filter(Objects::nonNull)
                        .toList();

                // Store as training sample for continuous improvement
                storeAsTrainingSample(request.getQuery(), llmResult.getSelectedTools(), "llm_fallback");

                return buildPredictionResponse(request, llmTools, 0.90,
                        methodLabel, llmResult.getReasoning(), startTime);
            }
        } catch (Exception e) {
            log.error("LLM prediction failed: {}", e.getMessage());
        }

        // Ultimate fallback to keyword
        KeywordPredictionResult keywordResult = predictWithLearnedKeywords(request.getQuery(), availableTools);
        return buildPredictionResponse(request, keywordResult.tools(), keywordResult.confidence(),
                methodLabel + " (keyword-fallback)", keywordResult.reasoning(), startTime);
    }

    /**
     * Store a query-tools pair as a training sample for the ML model.
     */
    private void storeAsTrainingSample(String query, List<String> tools, String source) {
        trainingSampleRepository.ifPresent(repo -> {
            try {
                // Check if this query already exists to avoid duplicates
                if (repo.existsByQuery(query)) {
                    log.debug("Training sample already exists for query: {}", query);
                    return;
                }

                TrainingSample sample = TrainingSample.builder()
                        .query(query)
                        .source(source)
                        .confidenceLevel(new BigDecimal("0.90")) // LLM predictions have high confidence
                        .verified(false)
                        .usedInTraining(false)
                        .build();
                sample.setToolsFromList(tools);

                repo.save(sample);
                log.info("Stored training sample from {}: query='{}', tools={}", source, query, tools);

                // Update training status counts
                trainingStatusRepository.ifPresent(statusRepo -> {
                    statusRepo.incrementLlmSamples(ModelTrainingStatus.DEFAULT_MODEL_NAME);
                });

            } catch (Exception e) {
                log.warn("Failed to store training sample: {}", e.getMessage());
            }
        });
    }

    /**
     * Record an LLM call for statistics.
     */
    private void recordLlmCall() {
        trainingStatusRepository.ifPresent(repo -> {
            repo.incrementLlmCalls(ModelTrainingStatus.DEFAULT_MODEL_NAME);
        });
    }

    /**
     * Record a saved LLM call (when ML was used instead).
     */
    private void recordSavedLlmCall() {
        trainingStatusRepository.ifPresent(repo -> {
            repo.incrementSavedLlmCalls(ModelTrainingStatus.DEFAULT_MODEL_NAME, ESTIMATED_TOKENS_PER_LLM_CALL);
        });
    }

    /**
     * Get or create the training status.
     */
    private ModelTrainingStatus getOrCreateTrainingStatus() {
        return trainingStatusRepository
                .map(ModelTrainingStatusRepository::getOrCreateDefault)
                .orElseGet(() -> ModelTrainingStatus.builder()
                        .currentPhase(ModelTrainingStatus.PHASE_BOOTSTRAP)
                        .build());
    }

    /**
     * Check if we have enough samples and trigger training if needed.
     */
    private void checkAndTriggerTraining(ModelTrainingStatus status) {
        if (status.isBootstrapPhase() && status.hasEnoughSamplesForTraining(minSamplesForTraining)) {
            log.info("Reached {} training samples, training can be triggered", status.getTotalTrainingSamples());
            // Note: Actual training is triggered via the /train endpoint in the ML service
            // This just logs that we're ready for training
        }
    }

    /**
     * Hybrid strategy: Combines multiple methods with confidence-based selection.
     */
    private ToolPredictionResponse predictWithHybridStrategy(ToolPredictionRequest request, long startTime) {
        String query = request.getQuery();
        List<MCPTool> availableTools = toolRegistry.getAllTools();

        // Step 1: Try keyword-based prediction with learned weights
        KeywordPredictionResult keywordResult = predictWithLearnedKeywords(query, availableTools);

        // Step 2: If keyword confidence is high enough, use it
        if (keywordResult.confidence >= 0.8) {
            return buildPredictionResponse(request, keywordResult.tools, keywordResult.confidence,
                    "hybrid (keyword)", keywordResult.reasoning, startTime);
        }

        // Step 3: Try LLM if enabled and keyword confidence is low
        if (llmService.isEnabled() && keywordResult.confidence < 0.6) {
            try {
                LLMService.LLMAnalysisResult llmResult = llmService.analyzeAgentDescription(
                        "Tool Selection", query, availableTools);

                if (llmResult != null && llmResult.getSelectedTools() != null && !llmResult.getSelectedTools().isEmpty()) {
                    List<ToolPredictionResponse.PredictedTool> llmTools = llmResult.getSelectedTools().stream()
                            .map(toolName -> buildPredictedTool(toolName, 0.85, "Selected by LLM analysis"))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    if (!llmTools.isEmpty()) {
                        return buildPredictionResponse(request, llmTools, 0.85,
                                "hybrid (llm)", llmResult.getReasoning(), startTime);
                    }
                }
            } catch (Exception e) {
                log.warn("LLM prediction failed, using keyword result: {}", e.getMessage());
            }
        }

        // Step 4: If ML service is enabled, try it as tiebreaker
        if (mlServiceEnabled && keywordResult.confidence < 0.7) {
            try {
                ToolPredictionResponse mlResult = callMLService(request);
                if (mlResult != null && mlResult.getConfidence() > keywordResult.confidence) {
                    mlResult.setPredictionMethod("hybrid (ml)");
                    mlResult.setPredictionTimeMs((int)(System.currentTimeMillis() - startTime));
                    return mlResult;
                }
            } catch (Exception e) {
                log.warn("ML service prediction failed: {}", e.getMessage());
            }
        }

        // Step 5: Return keyword result as fallback
        return buildPredictionResponse(request, keywordResult.tools, keywordResult.confidence,
                "hybrid (keyword-fallback)", keywordResult.reasoning, startTime);
    }

    /**
     * Keyword-based prediction with learned weights.
     */
    private ToolPredictionResponse predictWithKeywordStrategy(ToolPredictionRequest request, long startTime) {
        String query = request.getQuery();
        List<MCPTool> availableTools = toolRegistry.getAllTools();

        KeywordPredictionResult result = predictWithLearnedKeywords(query, availableTools);

        return buildPredictionResponse(request, result.tools, result.confidence,
                "keyword", result.reasoning, startTime);
    }

    /**
     * LLM-based prediction.
     */
    private ToolPredictionResponse predictWithLLMStrategy(ToolPredictionRequest request, long startTime) {
        String query = request.getQuery();
        List<MCPTool> availableTools = toolRegistry.getAllTools();

        if (!llmService.isEnabled()) {
            return ToolPredictionResponse.error(query, "LLM service is not enabled");
        }

        try {
            LLMService.LLMAnalysisResult llmResult = llmService.analyzeAgentDescription(
                    "Tool Selection", query, availableTools);

            if (llmResult == null || llmResult.getSelectedTools() == null || llmResult.getSelectedTools().isEmpty()) {
                return ToolPredictionResponse.error(query, "LLM returned no tools");
            }

            List<ToolPredictionResponse.PredictedTool> tools = llmResult.getSelectedTools().stream()
                    .map(toolName -> buildPredictedTool(toolName, 0.85, "Selected by LLM"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            return buildPredictionResponse(request, tools, 0.85, "llm", llmResult.getReasoning(), startTime);
        } catch (Exception e) {
            log.error("LLM prediction failed: {}", e.getMessage());
            return ToolPredictionResponse.error(query, "LLM prediction failed: " + e.getMessage());
        }
    }

    /**
     * Cosine similarity-based prediction using stored embeddings.
     */
    private ToolPredictionResponse predictWithCosineStrategy(ToolPredictionRequest request, long startTime) {
        // For now, fall back to hybrid if no embeddings are available
        // This will be fully implemented when Python ML service provides embeddings
        log.info("Cosine strategy falling back to hybrid (embeddings not yet implemented in Java)");
        return predictWithHybridStrategy(request, startTime);
    }

    /**
     * ML service-based prediction.
     */
    private ToolPredictionResponse predictWithMLService(ToolPredictionRequest request, long startTime) {
        try {
            ToolPredictionResponse mlResult = callMLService(request);
            if (mlResult != null) {
                mlResult.setPredictionTimeMs((int)(System.currentTimeMillis() - startTime));
                return mlResult;
            }
        } catch (Exception e) {
            log.error("ML service call failed: {}", e.getMessage());
        }

        // Fall back to hybrid
        log.warn("ML service failed, falling back to hybrid strategy");
        return predictWithHybridStrategy(request, startTime);
    }

    /**
     * Call the Python ML service for prediction.
     */
    private ToolPredictionResponse callMLService(ToolPredictionRequest request) {
        if (!mlServiceEnabled) {
            return null;
        }

        try {
            WebClient client = webClientBuilder.baseUrl(mlServiceUrl).build();

            return client.post()
                    .uri("/predict")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ToolPredictionResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to call ML service: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Predict using keywords with learned weights.
     */
    private KeywordPredictionResult predictWithLearnedKeywords(String query, List<MCPTool> availableTools) {
        String normalizedQuery = query.toLowerCase();
        List<String> extractedKeywords = extractKeywords(normalizedQuery);

        Map<String, Double> toolScores = new HashMap<>();
        StringBuilder reasoningBuilder = new StringBuilder();

        // Get learned weights if available
        Map<String, Map<String, BigDecimal>> learnedWeights = new HashMap<>();
        keywordWeightRepository.ifPresent(repo -> {
            List<KeywordToolWeight> weights = repo.findByKeywordIn(extractedKeywords);
            for (KeywordToolWeight weight : weights) {
                learnedWeights
                        .computeIfAbsent(weight.getKeyword(), k -> new HashMap<>())
                        .put(weight.getToolName(), weight.getWeight());
            }
        });

        // Score tools based on keywords and learned weights
        for (MCPTool tool : availableTools) {
            double score = calculateToolScore(tool, extractedKeywords, learnedWeights);
            if (score > 0) {
                toolScores.put(tool.getName(), score);
            }
        }

        // Get learning stats for tools
        Map<String, Double> successRates = new HashMap<>();
        learningStatRepository.ifPresent(repo -> {
            List<String> toolNames = new ArrayList<>(toolScores.keySet());
            List<ToolLearningStat> stats = repo.findByToolNameIn(toolNames);
            for (ToolLearningStat stat : stats) {
                successRates.put(stat.getToolName(), stat.getSuccessRate());
            }
        });

        // Sort and select top tools
        List<Map.Entry<String, Double>> sortedTools = toolScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());

        if (sortedTools.isEmpty()) {
            return new KeywordPredictionResult(List.of(), 0.0, "No matching tools found for keywords: " + extractedKeywords);
        }

        // Build predicted tools list
        double maxScore = sortedTools.get(0).getValue();
        List<ToolPredictionResponse.PredictedTool> predictedTools = sortedTools.stream()
                .map(entry -> {
                    String toolName = entry.getKey();
                    double normalizedScore = entry.getValue() / maxScore;
                    double successRate = successRates.getOrDefault(toolName, 0.5);

                    // Combine score with historical success rate
                    double combinedConfidence = (normalizedScore * 0.7) + (successRate * 0.3);

                    return buildPredictedTool(toolName, combinedConfidence,
                            String.format("Keyword match (score: %.2f, success rate: %.0f%%)",
                                    entry.getValue(), successRate * 100));
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Calculate overall confidence
        double avgConfidence = predictedTools.stream()
                .mapToDouble(ToolPredictionResponse.PredictedTool::getConfidence)
                .average()
                .orElse(0.5);

        reasoningBuilder.append("Keywords detected: ").append(extractedKeywords)
                .append(". Top tools based on keyword matching and historical performance.");

        return new KeywordPredictionResult(predictedTools, avgConfidence, reasoningBuilder.toString());
    }

    /**
     * Extract keywords from query.
     */
    private List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();

        // Domain keywords
        Map<String, List<String>> domainKeywords = Map.of(
                "jira", List.of("jira", "ticket", "issue", "bug", "task", "sprint", "backlog", "story", "epic"),
                "confluence", List.of("confluence", "wiki", "doc", "documentation", "page", "knowledge", "article"),
                "github", List.of("github", "code", "repository", "repo", "commit", "pull request", "pr", "branch", "merge"),
                "webex", List.of("webex", "chat", "room", "message", "meeting", "space", "team")
        );

        for (Map.Entry<String, List<String>> entry : domainKeywords.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    keywords.add(entry.getKey());
                    break;
                }
            }
        }

        // Action keywords
        Map<String, List<String>> actionKeywords = Map.of(
                "post", List.of("post", "send", "write message"),
                "list", List.of("list", "show", "get all", "fetch all"),
                "get", List.of("fetch", "retrieve", "read", "get"),
                "search", List.of("search", "query", "find", "look for"),
                "create", List.of("create", "new", "add"),
                "update", List.of("update", "modify", "change", "edit")
        );

        for (Map.Entry<String, List<String>> entry : actionKeywords.entrySet()) {
            for (String phrase : entry.getValue()) {
                if (text.contains(phrase)) {
                    keywords.add(entry.getKey());
                    break;
                }
            }
        }

        return keywords;
    }

    /**
     * Calculate tool score based on keywords and learned weights.
     */
    private double calculateToolScore(MCPTool tool, List<String> keywords,
                                      Map<String, Map<String, BigDecimal>> learnedWeights) {
        double score = 0;
        String toolName = tool.getName().toLowerCase();
        String toolCategory = tool.getCategory().toLowerCase();

        for (String keyword : keywords) {
            // Check if keyword matches tool category
            if (toolCategory.contains(keyword)) {
                score += 5;
            }

            // Check if keyword matches tool name
            if (toolName.contains(keyword)) {
                score += 10;
            }

            // Apply learned weight if available
            Map<String, BigDecimal> weightsByTool = learnedWeights.get(keyword);
            if (weightsByTool != null) {
                BigDecimal weight = weightsByTool.get(tool.getName());
                if (weight != null) {
                    score *= weight.doubleValue();
                }
            }
        }

        // Check tool capabilities
        for (String capability : tool.getCapabilities()) {
            for (String keyword : keywords) {
                if (capability.toLowerCase().contains(keyword)) {
                    score += 3;
                }
            }
        }

        return score;
    }

    /**
     * Build a PredictedTool from tool name.
     */
    private ToolPredictionResponse.PredictedTool buildPredictedTool(String toolName, double confidence, String reason) {
        Optional<MCPTool> toolOpt = toolRegistry.getTool(toolName);
        if (toolOpt.isEmpty()) {
            return null;
        }

        MCPTool tool = toolOpt.get();

        // Get historical success rate if available
        Double successRate = learningStatRepository
                .flatMap(repo -> repo.findByToolName(toolName))
                .map(ToolLearningStat::getSuccessRate)
                .orElse(null);

        return ToolPredictionResponse.PredictedTool.builder()
                .name(tool.getName())
                .category(tool.getCategory())
                .description(tool.getDescription())
                .confidence(confidence)
                .reason(reason)
                .successRate(successRate)
                .capabilities(tool.getCapabilities())
                .requiredInputs(tool.getRequiredInputs())
                .build();
    }

    /**
     * Build the full prediction response.
     */
    private ToolPredictionResponse buildPredictionResponse(ToolPredictionRequest request,
                                                           List<ToolPredictionResponse.PredictedTool> tools,
                                                           double confidence,
                                                           String method,
                                                           String reasoning,
                                                           long startTime) {
        int predictionTimeMs = (int)(System.currentTimeMillis() - startTime);

        // Filter by min confidence and limit
        List<ToolPredictionResponse.PredictedTool> filteredTools = tools.stream()
                .filter(t -> t.getConfidence() >= request.getMinConfidence())
                .limit(request.getMaxTools())
                .collect(Collectors.toList());

        // Create and save prediction record
        String predictionId = UUID.randomUUID().toString();

        predictionRepository.ifPresent(repo -> {
            ToolPrediction prediction = ToolPrediction.builder()
                    .query(request.getQuery())
                    .predictionMethod(method)
                    .reasoning(reasoning)
                    .userId(request.getUserId())
                    .tenantId(request.getTenantId())
                    .sessionId(request.getSessionId())
                    .status("completed")
                    .predictionTimeMs(predictionTimeMs)
                    .build();

            prediction.setPredictedToolsFromList(
                    filteredTools.stream().map(ToolPredictionResponse.PredictedTool::getName).collect(Collectors.toList())
            );

            Map<String, Double> confidenceScores = filteredTools.stream()
                    .collect(Collectors.toMap(
                            ToolPredictionResponse.PredictedTool::getName,
                            ToolPredictionResponse.PredictedTool::getConfidence
                    ));
            prediction.setConfidenceScores(confidenceScores);

            try {
                repo.save(prediction);
            } catch (Exception e) {
                log.warn("Failed to save prediction record: {}", e.getMessage());
            }
        });

        return ToolPredictionResponse.builder()
                .predictionId(predictionId)
                .query(request.getQuery())
                .predictedTools(filteredTools)
                .confidence(confidence)
                .predictionMethod(method)
                .reasoning(request.getIncludeReasoning() ? reasoning : null)
                .predictionTimeMs(predictionTimeMs)
                .createdAt(Instant.now())
                .feedbackUrl("/api/tools/feedback")
                .build();
    }

    /**
     * Process feedback on a prediction.
     */
    @Transactional
    public PredictionFeedbackResponse processFeedback(PredictionFeedbackRequest request) {
        log.info("Processing feedback for prediction: {}", request.getPredictionId());

        try {
            UUID predictionId = UUID.fromString(request.getPredictionId());

            // Check if prediction exists
            if (predictionRepository.isEmpty()) {
                return PredictionFeedbackResponse.error(request.getPredictionId(),
                        "Database not configured");
            }

            Optional<ToolPrediction> predictionOpt = predictionRepository.get().findById(predictionId);
            if (predictionOpt.isEmpty()) {
                return PredictionFeedbackResponse.error(request.getPredictionId(),
                        "Prediction not found");
            }

            ToolPrediction prediction = predictionOpt.get();

            // Create feedback record
            PredictionFeedback feedback = PredictionFeedback.builder()
                    .prediction(prediction)
                    .feedbackType(request.getFeedbackType())
                    .accuracyRating(request.getAccuracyRating())
                    .userId(request.getUserId())
                    .userComment(request.getUserComment())
                    .build();

            feedback.setCorrectToolsFromList(request.getCorrectTools());
            feedback.setMissingToolsFromList(request.getMissingTools());
            feedback.setIncorrectToolsFromList(request.getIncorrectTools());

            PredictionFeedback savedFeedback = feedbackRepository.get().save(feedback);

            // Update learning stats and weights
            boolean learningUpdated = updateLearningFromFeedback(prediction, request);

            // Create training sample if feedback is useful
            boolean trainingSampleCreated = createTrainingSampleFromFeedback(prediction, request);

            // Update prediction status
            prediction.setStatus("used");
            predictionRepository.get().save(prediction);

            return PredictionFeedbackResponse.success(
                    savedFeedback.getId().toString(),
                    request.getPredictionId(),
                    learningUpdated,
                    trainingSampleCreated
            );

        } catch (Exception e) {
            log.error("Error processing feedback: {}", e.getMessage(), e);
            return PredictionFeedbackResponse.error(request.getPredictionId(), e.getMessage());
        }
    }

    /**
     * Update learning stats and keyword weights based on feedback.
     */
    private boolean updateLearningFromFeedback(ToolPrediction prediction, PredictionFeedbackRequest feedback) {
        try {
            // Update tool learning stats
            if (learningStatRepository.isPresent()) {
                ToolLearningStatRepository repo = learningStatRepository.get();

                // Update stats for correct tools
                if (feedback.getCorrectTools() != null) {
                    for (String toolName : feedback.getCorrectTools()) {
                        ToolLearningStat stat = repo.findByToolName(toolName)
                                .orElseGet(() -> ToolLearningStat.builder().toolName(toolName).build());
                        stat.recordCorrectPrediction();
                        repo.save(stat);
                    }
                }

                // Update stats for incorrect tools
                if (feedback.getIncorrectTools() != null) {
                    for (String toolName : feedback.getIncorrectTools()) {
                        ToolLearningStat stat = repo.findByToolName(toolName)
                                .orElseGet(() -> ToolLearningStat.builder().toolName(toolName).build());
                        stat.recordIncorrectPrediction();
                        repo.save(stat);
                    }
                }

                // Update stats for missing tools
                if (feedback.getMissingTools() != null) {
                    for (String toolName : feedback.getMissingTools()) {
                        ToolLearningStat stat = repo.findByToolName(toolName)
                                .orElseGet(() -> ToolLearningStat.builder().toolName(toolName).build());
                        stat.recordMissedPrediction();
                        repo.save(stat);
                    }
                }
            }

            // Update keyword-tool weights
            if (keywordWeightRepository.isPresent()) {
                KeywordToolWeightRepository repo = keywordWeightRepository.get();
                List<String> queryKeywords = extractKeywords(prediction.getQuery().toLowerCase());

                // Increase weights for correct keyword-tool associations
                if (feedback.getCorrectTools() != null) {
                    for (String keyword : queryKeywords) {
                        for (String toolName : feedback.getCorrectTools()) {
                            KeywordToolWeight weight = repo.findByKeywordAndToolName(keyword, toolName)
                                    .orElseGet(() -> KeywordToolWeight.builder()
                                            .keyword(keyword)
                                            .toolName(toolName)
                                            .build());
                            weight.recordSuccess();
                            repo.save(weight);
                        }
                    }
                }

                // Decrease weights for incorrect keyword-tool associations
                if (feedback.getIncorrectTools() != null) {
                    for (String keyword : queryKeywords) {
                        for (String toolName : feedback.getIncorrectTools()) {
                            KeywordToolWeight weight = repo.findByKeywordAndToolName(keyword, toolName)
                                    .orElseGet(() -> KeywordToolWeight.builder()
                                            .keyword(keyword)
                                            .toolName(toolName)
                                            .build());
                            weight.recordFailure();
                            repo.save(weight);
                        }
                    }
                }
            }

            return true;
        } catch (Exception e) {
            log.error("Failed to update learning from feedback: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Create a training sample from feedback for ML model training.
     */
    private boolean createTrainingSampleFromFeedback(ToolPrediction prediction, PredictionFeedbackRequest feedback) {
        try {
            if (trainingSampleRepository.isEmpty()) {
                return false;
            }

            // Only create samples from useful feedback
            if (!"correct".equals(feedback.getFeedbackType()) &&
                    !"partial".equals(feedback.getFeedbackType())) {
                return false;
            }

            // Determine correct tools for this query
            List<String> correctTools = new ArrayList<>();
            if (feedback.getCorrectTools() != null) {
                correctTools.addAll(feedback.getCorrectTools());
            }
            if (feedback.getMissingTools() != null) {
                correctTools.addAll(feedback.getMissingTools());
            }

            if (correctTools.isEmpty()) {
                return false;
            }

            // Determine confidence based on feedback rating
            BigDecimal confidence = BigDecimal.valueOf(0.7);
            if (feedback.getAccuracyRating() != null) {
                confidence = BigDecimal.valueOf(feedback.getAccuracyRating() / 5.0);
            }

            TrainingSample sample = TrainingSample.fromFeedback(prediction, correctTools, confidence);
            trainingSampleRepository.get().save(sample);

            log.info("Created training sample from feedback for query: {}", prediction.getQuery());
            return true;

        } catch (Exception e) {
            log.error("Failed to create training sample: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get prediction statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // Training status (bootstrap strategy)
        trainingStatusRepository.ifPresent(repo -> {
            ModelTrainingStatus status = repo.getOrCreateDefault();
            Map<String, Object> trainingInfo = new LinkedHashMap<>();
            trainingInfo.put("currentPhase", status.getCurrentPhase());
            trainingInfo.put("currentAccuracy", status.getCurrentAccuracy());
            trainingInfo.put("currentPrecision", status.getCurrentPrecision());
            trainingInfo.put("currentRecall", status.getCurrentRecall());
            trainingInfo.put("currentF1", status.getCurrentF1());
            trainingInfo.put("totalTrainingSamples", status.getTotalTrainingSamples());
            trainingInfo.put("samplesFromLlm", status.getSamplesFromLlm());
            trainingInfo.put("samplesFromFeedback", status.getSamplesFromFeedback());
            trainingInfo.put("llmCallsTotal", status.getLlmCallsTotal());
            trainingInfo.put("llmCallsSaved", status.getLlmCallsSaved());
            trainingInfo.put("estimatedTokensSaved", status.getEstimatedTokensSaved());
            trainingInfo.put("llmSavingsPercentage", String.format("%.1f%%", status.getLlmSavingsPercentage()));
            trainingInfo.put("activeModelVersion", status.getActiveModelVersion());
            trainingInfo.put("lastTrainingDate", status.getLastTrainingDate());
            trainingInfo.put("trainingRunsCount", status.getTrainingRunsCount());
            trainingInfo.put("minSamplesForTraining", minSamplesForTraining);
            trainingInfo.put("readyForTraining", status.hasEnoughSamplesForTraining(minSamplesForTraining));
            stats.put("trainingStatus", trainingInfo);
        });

        predictionRepository.ifPresent(repo -> {
            stats.put("totalPredictions", repo.count());
            stats.put("byMethod", repo.countByPredictionMethod());
        });

        feedbackRepository.ifPresent(repo -> {
            stats.put("totalFeedback", repo.count());
            stats.put("averageRating", repo.getAverageAccuracyRating());
            stats.put("byType", repo.countByFeedbackType());
        });

        learningStatRepository.ifPresent(repo -> {
            stats.put("toolStats", repo.getOverallStatistics());
            stats.put("topTools", repo.findTop10ByOrderByF1ScoreDesc());
        });

        trainingSampleRepository.ifPresent(repo -> {
            stats.put("totalTrainingSamples", repo.count());
            stats.put("unusedSamples", repo.countUnusedSamples());
            stats.put("bySource", repo.countBySource());
        });

        return stats;
    }

    /**
     * Result holder for keyword prediction.
     */
    private record KeywordPredictionResult(
            List<ToolPredictionResponse.PredictedTool> tools,
            double confidence,
            String reasoning
    ) {}
}
