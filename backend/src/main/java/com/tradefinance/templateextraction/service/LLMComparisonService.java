package com.tradefinance.templateextraction.service;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.AzureKeyCredential;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradefinance.templateextraction.dto.FieldFinding;
import com.tradefinance.templateextraction.dto.StructuredAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LLMComparisonService {

    @Value("${azure.openai.api-key:}")
    private String apiKey;

    @Value("${azure.openai.endpoint:}")
    private String endpoint;

    @Value("${azure.openai.deployment-name:}")
    private String deploymentName;

    @Value("${azure.openai.enabled:false}")
    private boolean enabled;

    private OpenAIClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Initialize the Azure OpenAI client lazily when needed
     */
    private void initializeClient() {
        if (client == null && enabled && apiKey != null && !apiKey.isEmpty()) {
            try {
                client = new OpenAIClientBuilder()
                        .credential(new AzureKeyCredential(apiKey))
                        .endpoint(endpoint)
                        .buildClient();
                log.info("Azure OpenAI client initialized successfully");
            } catch (Exception e) {
                log.error("Failed to initialize Azure OpenAI client", e);
                enabled = false;
            }
        }
    }

    /**
     * Compare a transaction message to its matched template using LLM analysis
     *
     * @param messageContent The raw SWIFT message content
     * @param templateContent The template content
     * @param extractedFields The fields extracted from the message
     * @return A concise summary (3-5 bullet points) of key differences
     */
    public String compareTransactionToTemplate(String messageContent, String templateContent,
                                               Map<String, Object> extractedFields) {
        // Check if Azure OpenAI is configured
        if (!enabled || apiKey == null || apiKey.isEmpty() || endpoint == null || endpoint.isEmpty()) {
            log.debug("Azure OpenAI not configured, returning default message");
            return "LLM comparison not available - Azure OpenAI not configured";
        }

        try {
            // Initialize client if not already done
            initializeClient();

            if (client == null) {
                return "LLM comparison not available - Azure OpenAI client initialization failed";
            }

            // Build the prompt for comparison
            String prompt = buildComparisonPrompt(messageContent, templateContent, extractedFields);

            // Create chat completion request
            List<ChatRequestMessage> chatMessages = new ArrayList<>();
            chatMessages.add(new ChatRequestSystemMessage(
                "You are a trade finance expert specializing in SWIFT message analysis. Your role is to:\n" +
                "1. Compare actual transactions against expected templates\n" +
                "2. Explain differences in business terms (not just technical field comparisons)\n" +
                "3. Highlight what matters for compliance, risk, and operations\n" +
                "4. Provide clear recommendations for review\n\n" +
                "Your audience is trade finance professionals who need to understand:\n" +
                "- WHAT changed (in plain English)\n" +
                "- WHY it matters (business impact)\n" +
                "- WHAT action to take (if any)\n\n" +
                "Be concise but informative. Use bullet points with context, not just field names."
            ));
            chatMessages.add(new ChatRequestUserMessage(prompt));

            ChatCompletionsOptions options = new ChatCompletionsOptions(chatMessages)
                    .setMaxTokens(800)
                    .setTemperature(0.3);

            // Call Azure OpenAI
            ChatCompletions completions = client.getChatCompletions(deploymentName, options);

            // Extract the response
            if (completions.getChoices() != null && !completions.getChoices().isEmpty()) {
                String response = completions.getChoices().get(0).getMessage().getContent();
                log.debug("LLM comparison completed successfully");
                return response;
            } else {
                log.warn("No response from Azure OpenAI");
                return "LLM comparison not available - No response from Azure OpenAI";
            }

        } catch (Exception e) {
            log.error("Error during LLM comparison", e);
            return "LLM comparison failed: " + e.getMessage();
        }
    }

    /**
     * Build a comprehensive prompt for the LLM to compare message and template
     */
    private String buildComparisonPrompt(String messageContent, String templateContent,
                                        Map<String, Object> extractedFields) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Analyze this trade finance transaction against its expected template.\n\n");

        prompt.append("=== EXPECTED PATTERN (Template) ===\n");
        prompt.append(templateContent);
        prompt.append("\n\n");

        prompt.append("=== ACTUAL TRANSACTION ===\n");
        prompt.append(messageContent);
        prompt.append("\n\n");

        prompt.append("=== EXTRACTED DATA ===\n");
        for (Map.Entry<String, Object> entry : extractedFields.entrySet()) {
            prompt.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        prompt.append("\n");

        prompt.append("=== ANALYSIS REQUIRED ===\n");
        prompt.append("Provide a business-focused summary in 4-6 clear bullet points:\n\n");

        prompt.append("1. **Transaction Overview**: Brief summary of what this transaction is (e.g., 'Documentary Credit for $200K, partial shipments prohibited')\n\n");

        prompt.append("2. **Key Variations from Template**: Explain significant differences in business terms\n");
        prompt.append("   - For amounts/dates/quantities: mention the actual values and their business significance\n");
        prompt.append("   - For terms/conditions: explain what changed and why it matters (e.g., 'Shipment terms changed from PERMITTED to PROHIBITED - restricts shipping flexibility')\n");
        prompt.append("   - For document requirements: clarify implications for document handling\n\n");

        prompt.append("3. **Compliance & Risk Considerations**: Flag anything that needs attention\n");
        prompt.append("   - Unusual values or patterns\n");
        prompt.append("   - Deviations that may affect processing or compliance\n");
        prompt.append("   - Missing critical information\n\n");

        prompt.append("4. **Recommendation**: Clear action item\n");
        prompt.append("   - If acceptable: 'Appears routine, no action needed'\n");
        prompt.append("   - If review needed: 'Recommend review of [specific fields] due to [reason]'\n");
        prompt.append("   - If concerning: 'Flag for approval - [specific concern]'\n\n");

        prompt.append("Format each point with a bold label followed by concise explanation. Use plain language, not just field codes.\n");

        return prompt.toString();
    }

    /**
     * Check if LLM comparison is available
     */
    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Get structured field-level analysis with severity
     *
     * @param messageContent The raw SWIFT message content
     * @param templateContent The template content
     * @param extractedFields The fields extracted from the message
     * @return Structured analysis with field-level findings
     */
    public StructuredAnalysis getStructuredAnalysis(String messageContent, String templateContent,
                                                    Map<String, Object> extractedFields) {
        // Check if Azure OpenAI is configured
        if (!enabled || apiKey == null || apiKey.isEmpty() || endpoint == null || endpoint.isEmpty()) {
            log.debug("Azure OpenAI not configured, returning default analysis");
            return StructuredAnalysis.builder()
                    .transactionSummary("LLM analysis not available - Azure OpenAI not configured")
                    .fieldFindings(new ArrayList<>())
                    .overallRisk(StructuredAnalysis.RiskLevel.LOW)
                    .recommendation("Manual review required")
                    .build();
        }

        try {
            // Initialize client if not already done
            initializeClient();

            if (client == null) {
                return StructuredAnalysis.builder()
                        .transactionSummary("LLM analysis not available - client initialization failed")
                        .fieldFindings(new ArrayList<>())
                        .overallRisk(StructuredAnalysis.RiskLevel.LOW)
                        .build();
            }

            // Build the prompt for structured analysis
            String prompt = buildStructuredAnalysisPrompt(messageContent, templateContent, extractedFields);

            // Create chat completion request
            List<ChatRequestMessage> chatMessages = new ArrayList<>();
            chatMessages.add(new ChatRequestSystemMessage(
                "You are a trade finance expert analyzing SWIFT messages. " +
                "You MUST respond with ONLY valid JSON matching this exact structure:\n" +
                "{\n" +
                "  \"transactionSummary\": \"Brief summary\",\n" +
                "  \"fieldFindings\": [\n" +
                "    {\n" +
                "      \"fieldTag\": \"32B\",\n" +
                "      \"fieldName\": \"Currency & Amount\",\n" +
                "      \"severity\": \"INFO\",\n" +
                "      \"description\": \"Amount matches expected range\",\n" +
                "      \"actualValue\": \"USD 200,620.00\",\n" +
                "      \"expectedValue\": \"Variable amount\",\n" +
                "      \"businessImpact\": \"Transaction value within normal parameters\",\n" +
                "      \"recommendation\": \"No action needed\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"overallRisk\": \"LOW\",\n" +
                "  \"recommendation\": \"Approve\",\n" +
                "  \"notes\": \"Additional context\"\n" +
                "}\n\n" +
                "Severity levels: CRITICAL (immediate attention), WARNING (review needed), INFO (notable variation), ACCEPTABLE (normal).\n" +
                "Risk levels: LOW, MEDIUM, HIGH.\n" +
                "Respond with ONLY the JSON object, no markdown formatting, no explanatory text."
            ));
            chatMessages.add(new ChatRequestUserMessage(prompt));

            ChatCompletionsOptions options = new ChatCompletionsOptions(chatMessages)
                    .setMaxTokens(1500)
                    .setTemperature(0.2);

            // Call Azure OpenAI
            ChatCompletions completions = client.getChatCompletions(deploymentName, options);

            // Extract and parse the response
            if (completions.getChoices() != null && !completions.getChoices().isEmpty()) {
                String response = completions.getChoices().get(0).getMessage().getContent().trim();
                log.debug("LLM structured response: {}", response);

                // Parse JSON response
                return parseStructuredResponse(response);
            } else {
                log.warn("No response from Azure OpenAI");
                return StructuredAnalysis.builder()
                        .transactionSummary("No response from LLM")
                        .fieldFindings(new ArrayList<>())
                        .overallRisk(StructuredAnalysis.RiskLevel.MEDIUM)
                        .build();
            }

        } catch (Exception e) {
            log.error("Error during structured LLM analysis", e);
            return StructuredAnalysis.builder()
                    .transactionSummary("LLM analysis failed: " + e.getMessage())
                    .fieldFindings(new ArrayList<>())
                    .overallRisk(StructuredAnalysis.RiskLevel.MEDIUM)
                    .build();
        }
    }

    /**
     * Build prompt for structured analysis
     */
    private String buildStructuredAnalysisPrompt(String messageContent, String templateContent,
                                                Map<String, Object> extractedFields) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Analyze this SWIFT message against its template and provide structured field-level findings.\n\n");

        prompt.append("=== TEMPLATE ===\n");
        prompt.append(templateContent);
        prompt.append("\n\n");

        prompt.append("=== ACTUAL MESSAGE ===\n");
        prompt.append(messageContent);
        prompt.append("\n\n");

        prompt.append("=== EXTRACTED FIELDS ===\n");
        for (Map.Entry<String, Object> entry : extractedFields.entrySet()) {
            prompt.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        prompt.append("\n");

        prompt.append("Analyze each significant field variation. For each finding, determine:\n");
        prompt.append("- Field tag and business name\n");
        prompt.append("- Severity (CRITICAL for compliance issues, WARNING for review needed, INFO for expected variations, ACCEPTABLE for normal)\n");
        prompt.append("- Actual vs expected values\n");
        prompt.append("- Business impact\n");
        prompt.append("- Specific recommendation\n\n");

        prompt.append("Focus on material differences that affect risk, compliance, or operations.\n");
        prompt.append("Return ONLY the JSON object, no other text.");

        return prompt.toString();
    }

    /**
     * Parse LLM JSON response into StructuredAnalysis
     */
    private StructuredAnalysis parseStructuredResponse(String response) {
        try {
            // Clean response - remove markdown code blocks if present
            String cleanJson = response
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*$", "")
                    .trim();

            // Parse JSON
            Map<String, Object> jsonMap = objectMapper.readValue(cleanJson, new TypeReference<Map<String, Object>>() {});

            // Build StructuredAnalysis from JSON
            StructuredAnalysis.StructuredAnalysisBuilder builder = StructuredAnalysis.builder()
                    .transactionSummary((String) jsonMap.getOrDefault("transactionSummary", ""))
                    .recommendation((String) jsonMap.getOrDefault("recommendation", ""))
                    .notes((String) jsonMap.getOrDefault("notes", ""));

            // Parse overall risk
            String riskStr = (String) jsonMap.getOrDefault("overallRisk", "MEDIUM");
            try {
                builder.overallRisk(StructuredAnalysis.RiskLevel.valueOf(riskStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                builder.overallRisk(StructuredAnalysis.RiskLevel.MEDIUM);
            }

            // Parse field findings
            List<FieldFinding> findings = new ArrayList<>();
            List<Map<String, Object>> findingsJson = (List<Map<String, Object>>) jsonMap.get("fieldFindings");
            if (findingsJson != null) {
                for (Map<String, Object> findingMap : findingsJson) {
                    FieldFinding.FieldFindingBuilder findingBuilder = FieldFinding.builder()
                            .fieldTag((String) findingMap.getOrDefault("fieldTag", ""))
                            .fieldName((String) findingMap.getOrDefault("fieldName", ""))
                            .description((String) findingMap.getOrDefault("description", ""))
                            .actualValue((String) findingMap.getOrDefault("actualValue", ""))
                            .expectedValue((String) findingMap.getOrDefault("expectedValue", ""))
                            .businessImpact((String) findingMap.getOrDefault("businessImpact", ""))
                            .recommendation((String) findingMap.getOrDefault("recommendation", ""));

                    // Parse severity
                    String severityStr = (String) findingMap.getOrDefault("severity", "INFO");
                    try {
                        findingBuilder.severity(FieldFinding.Severity.valueOf(severityStr.toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        findingBuilder.severity(FieldFinding.Severity.INFO);
                    }

                    findings.add(findingBuilder.build());
                }
            }
            builder.fieldFindings(findings);

            return builder.build();

        } catch (Exception e) {
            log.error("Failed to parse structured LLM response", e);
            // Return a default structure with the raw response as a note
            return StructuredAnalysis.builder()
                    .transactionSummary("Failed to parse LLM response")
                    .fieldFindings(new ArrayList<>())
                    .overallRisk(StructuredAnalysis.RiskLevel.MEDIUM)
                    .notes("Raw response: " + response)
                    .build();
        }
    }
}
