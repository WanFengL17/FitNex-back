package com.fitnex.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitnex.entity.HealthProfile;
import com.fitnex.entity.NutritionRecord;
import com.fitnex.entity.User;
import com.fitnex.entity.WorkoutPlan;
import com.fitnex.entity.WorkoutPlanItem;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AIService {

    // 讯飞星火配置
    @Value("${ai.spark.api-key:}")
    private String sparkApiKey;

    @Value("${ai.spark.api-secret:}")
    private String sparkApiSecret;

    @Value("${ai.spark.app-id:}")
    private String sparkAppId;

    @Value("${ai.spark.base-url:wss://spark-api.cn-huabei-1.xf-yun.com/v2.1/image}")
    private String sparkBaseUrl;

    @Value("${ai.spark.timeout:30000}")
    private int timeout;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build();

    @PostConstruct
    public void init() {
    }

    @PreDestroy
    public void cleanup() {
        try {
            httpClient.dispatcher().executorService().shutdown();
        } catch (Exception e) {
        }
    }

    public WorkoutPlan generateWorkoutPlan(User user, HealthProfile profile) {
        try {
            String prompt = buildWorkoutPlanPrompt(user, profile);
            String response = callSparkChatAPI(prompt, null);
            WorkoutPlan plan = parseWorkoutPlanResponse(response);
            plan.setName("AI智能训练计划");
            plan.setDescription("基于您的健康档案生成的个性化训练计划");
            plan.setIsAiGenerated(true);
            return plan;
        } catch (Exception e) {
            return createDefaultWorkoutPlan(user, profile);
        }
    }

    public NutritionRecord recognizeFoodFromImage(MultipartFile imageFile) {
        try {
            String base64Image = encodeImageToBase64(imageFile);
            if (sparkApiKey != null && !sparkApiKey.isEmpty() &&
                sparkApiSecret != null && !sparkApiSecret.isEmpty() &&
                sparkAppId != null && !sparkAppId.isEmpty()) {
                return recognizeFoodWithSpark(base64Image, imageFile.getContentType());
            }
            return createDefaultNutritionRecord();
        } catch (Exception e) {
            return createDefaultNutritionRecord();
        }
    }

    private NutritionRecord recognizeFoodWithSpark(String base64Image, String contentType) {
        String authUrl = buildSparkAuthUrl();
        String requestJson = buildSparkRequestJson(base64Image, contentType);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> responseRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();
        AtomicReference<WebSocket> webSocketRef = new AtomicReference<>();

        Request request = new Request.Builder()
                .url(authUrl)
                .get()
                .build();

        WebSocket webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                webSocketRef.set(ws);
                ws.send(requestJson);
            }

            @Override
            public void onMessage(WebSocket ws, String message) {
                try {
                    Map<String, Object> resp = objectMapper.readValue(message, Map.class);
                    if (resp.containsKey("payload")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = (Map<String, Object>) resp.get("payload");
                        if (payload != null && payload.containsKey("choices")) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> choices = (List<Map<String, Object>>) payload.get("choices");
                            if (!choices.isEmpty()) {
                                Map<String, Object> choice = choices.get(0);
                                if (choice.containsKey("message")) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> msgObj = (Map<String, Object>) choice.get("message");
                                    String content = (String) msgObj.get("content");
                                    responseRef.set(content);
                                }
                            }
                        }
                    }
                    if (resp.containsKey("header")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> header = (Map<String, Object>) resp.get("header");
                        if (header != null && "0".equals(header.get("code"))) {
                            ws.close(1000, "completed");
                        }
                    }
                } catch (Exception e) {
                    errorRef.set("解析响应失败: " + e.getMessage());
                }
                latch.countDown();
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                latch.countDown();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                errorRef.set("WebSocket错误: " + t.getMessage());
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(timeout, TimeUnit.MILLISECONDS);
            if (!completed) {
                errorRef.set("等待响应超时");
            }

            if (errorRef.get() != null) {
                System.err.println("讯飞API错误: " + errorRef.get());
                return createDefaultNutritionRecord();
            }

            String aiResponse = responseRef.get();
            if (aiResponse != null && !aiResponse.isEmpty()) {
                return parseNutritionFromAIResponse(aiResponse);
            }

            return createDefaultNutritionRecord();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("讯飞API调用被中断: " + e.getMessage());
            return createDefaultNutritionRecord();
        } finally {
            if (webSocketRef.get() != null) {
                try {
                    webSocketRef.get().close(1000, "finished");
                } catch (Exception e) {
                }
            }
        }
    }

    private String buildSparkAuthUrl() {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String signature = generateSparkSignature(timestamp);
            StringBuilder url = new StringBuilder(sparkBaseUrl);
            url.append("?authorization=").append(generateAuthorization(timestamp));
            url.append("&X-Appid=").append(sparkAppId);
            url.append("&X-CurTime=").append(timestamp);
            url.append("&X-Param=").append(generateParam());
            url.append("&X-CheckSum=").append(signature);
            return url.toString();
        } catch (Exception e) {
            System.err.println("构建鉴权URL失败: " + e.getMessage());
            return sparkBaseUrl;
        }
    }

    private String generateSparkSignature(String timestamp) {
        try {
            String input = sparkApiKey + timestamp + sparkApiSecret;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private String generateAuthorization(String timestamp) {
        try {
            String signature = generateSparkSignature(timestamp);
            String authInfo = "api_key=\"" + sparkApiKey + "\", timestamp=\"" + timestamp + "\", signature=\"" + signature + "\"";
            return Base64.getEncoder().encodeToString(authInfo.getBytes());
        } catch (Exception e) {
            return "";
        }
    }

    private String generateParam() {
        try {
            Map<String, Object> param = new HashMap<>();
            Map<String, Object> chat = new HashMap<>();
            chat.put("domain", "image");
            chat.put("temperature", 0.3);
            chat.put("max_tokens", 500);
            param.put("chat", chat);
            return Base64.getEncoder().encodeToString(objectMapper.writeValueAsString(param).getBytes());
        } catch (Exception e) {
            return "";
        }
    }

    private String buildSparkRequestJson(String base64Image, String contentType) {
        try {
            Map<String, Object> request = new HashMap<>();
            Map<String, Object> header = new HashMap<>();
            header.put("app_id", sparkAppId);
            header.put("uid", UUID.randomUUID().toString());
            request.put("header", header);

            Map<String, Object> parameter = new HashMap<>();
            Map<String, Object> chat = new HashMap<>();
            chat.put("temperature", 0.3);
            chat.put("max_tokens", 500);
            parameter.put("chat", chat);
            request.put("parameter", parameter);

            Map<String, Object> payload = new HashMap<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", Arrays.asList(
                    createTextContent("请识别这张图片中的食物，并返回JSON格式的营养信息。格式如下：{\"foodName\": \"食物名称\", \"quantity\": 数量(克), \"calories\": 卡路里, \"protein\": 蛋白质(克), \"carbs\": 碳水化合物(克), \"fat\": 脂肪(克), \"fiber\": 纤维(克), \"mealType\": \"BREAKFAST|LUNCH|DINNER|SNACK\"}。只返回JSON，不要其他文字。"),
                    createImageContent(base64Image, contentType)
            ));
            payload.put("message", message);
            request.put("payload", payload);

            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> createTextContent(String text) {
        Map<String, Object> content = new HashMap<>();
        content.put("type", "text");
        content.put("text", text);
        return content;
    }

    private Map<String, Object> createImageContent(String base64Image, String contentType) {
        Map<String, Object> content = new HashMap<>();
        content.put("type", "image");
        Map<String, Object> image = new HashMap<>();
        String format = "jpeg";
        if (contentType != null) {
            if (contentType.toLowerCase().contains("png")) {
                format = "png";
            } else if (contentType.toLowerCase().contains("gif")) {
                format = "gif";
            } else if (contentType.toLowerCase().contains("webp")) {
                format = "webp";
            }
        }
        image.put("format", format);
        image.put("image", base64Image);
        content.put("image", image);
        return content;
    }

    private String encodeImageToBase64(MultipartFile imageFile) throws IOException {
        byte[] imageBytes = imageFile.getBytes();
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    private String encodeImageToBase64(String imageUrl) throws IOException {
        String filePath = imageUrl;
        if (filePath.startsWith("/uploads/")) {
            filePath = filePath.substring(1);
        } else if (filePath.startsWith("uploads/")) {
        } else if (!filePath.startsWith("/") && !filePath.contains(":")) {
            if (!filePath.startsWith("uploads")) {
                filePath = "uploads/images/" + filePath;
            }
        }
        String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
        String[] possiblePaths = {
            filePath,
            System.getProperty("user.dir") + "/" + filePath,
            System.getProperty("user.dir") + "/FitNex-back/" + filePath,
            System.getProperty("user.dir") + "/uploads/images/" + fileName,
            "uploads/images/" + fileName,
            "/uploads/images/" + fileName
        };
        for (String path : possiblePaths) {
            try {
                java.nio.file.Path pathObj = Paths.get(path);
                if (Files.exists(pathObj) && Files.isRegularFile(pathObj)) {
                    byte[] imageBytes = Files.readAllBytes(pathObj);
                    return Base64.getEncoder().encodeToString(imageBytes);
                }
            } catch (Exception e) {
            }
        }
        throw new IOException("无法读取图片文件: " + imageUrl);
    }

    private NutritionRecord parseNutritionFromAIResponse(String aiResponse) {
        NutritionRecord record = new NutritionRecord();
        record.setIsAiRecognized(true);
        try {
            String jsonStr = aiResponse.trim();
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```json") + 7);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```")).trim();
            } else if (jsonStr.contains("```")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```") + 3);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```")).trim();
            }
            if (jsonStr.contains("\"foodName\"")) {
                record.setFoodName(extractJsonValue(jsonStr, "foodName"));
            }
            if (jsonStr.contains("\"quantity\"")) {
                String qty = extractJsonValue(jsonStr, "quantity");
                if (qty != null && !qty.isEmpty()) {
                    try {
                        record.setQuantity(Double.parseDouble(qty));
                        record.setUnit("克");
                    } catch (NumberFormatException e) {
                        record.setQuantity(100.0);
                        record.setUnit("克");
                    }
                }
            }
            if (jsonStr.contains("\"calories\"")) {
                String cal = extractJsonValue(jsonStr, "calories");
                if (cal != null && !cal.isEmpty()) {
                    try {
                        record.setCalories(Integer.parseInt(cal));
                    } catch (NumberFormatException e) {
                        record.setCalories(0);
                    }
                }
            }
            if (jsonStr.contains("\"protein\"")) {
                String prot = extractJsonValue(jsonStr, "protein");
                if (prot != null && !prot.isEmpty()) {
                    try {
                        record.setProtein(Double.parseDouble(prot));
                    } catch (NumberFormatException e) {
                        record.setProtein(0.0);
                    }
                }
            }
            if (jsonStr.contains("\"carbs\"")) {
                String carbs = extractJsonValue(jsonStr, "carbs");
                if (carbs != null && !carbs.isEmpty()) {
                    try {
                        record.setCarbs(Double.parseDouble(carbs));
                    } catch (NumberFormatException e) {
                        record.setCarbs(0.0);
                    }
                }
            }
            if (jsonStr.contains("\"fat\"")) {
                String fat = extractJsonValue(jsonStr, "fat");
                if (fat != null && !fat.isEmpty()) {
                    try {
                        record.setFat(Double.parseDouble(fat));
                    } catch (NumberFormatException e) {
                        record.setFat(0.0);
                    }
                }
            }
            if (jsonStr.contains("\"fiber\"")) {
                String fiber = extractJsonValue(jsonStr, "fiber");
                if (fiber != null && !fiber.isEmpty()) {
                    try {
                        record.setFiber(Double.parseDouble(fiber));
                    } catch (NumberFormatException e) {
                        record.setFiber(0.0);
                    }
                }
            }
            if (jsonStr.contains("\"mealType\"")) {
                record.setMealType(extractJsonValue(jsonStr, "mealType"));
            }
        } catch (Exception e) {
        }
        if (record.getFoodName() == null || record.getFoodName().isEmpty()) {
            record.setFoodName("未识别食物");
        }
        if (record.getCalories() == null) {
            record.setCalories(0);
        }
        if (record.getProtein() == null) {
            record.setProtein(0.0);
        }
        if (record.getCarbs() == null) {
            record.setCarbs(0.0);
        }
        if (record.getFat() == null) {
            record.setFat(0.0);
        }
        if (record.getMealType() == null) {
            record.setMealType("SNACK");
        }
        return record;
    }

    private String extractJsonValue(String json, String key) {
        try {
            String searchKey = "\"" + key + "\"";
            int keyIndex = json.indexOf(searchKey);
            if (keyIndex == -1) return null;
            int colonIndex = json.indexOf(":", keyIndex);
            if (colonIndex == -1) return null;
            int startIndex = colonIndex + 1;
            while (startIndex < json.length() && (json.charAt(startIndex) == ' ' || json.charAt(startIndex) == '\t')) {
                startIndex++;
            }
            if (startIndex >= json.length()) return null;
            char firstChar = json.charAt(startIndex);
            if (firstChar == '"') {
                int endIndex = json.indexOf('"', startIndex + 1);
                if (endIndex == -1) return null;
                return json.substring(startIndex + 1, endIndex);
            } else {
                int endIndex = startIndex;
                while (endIndex < json.length() &&
                       (Character.isDigit(json.charAt(endIndex)) ||
                        json.charAt(endIndex) == '.' ||
                        json.charAt(endIndex) == '-')) {
                    endIndex++;
                }
                if (endIndex > startIndex) {
                    return json.substring(startIndex, endIndex);
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    private NutritionRecord createDefaultNutritionRecord() {
        NutritionRecord record = new NutritionRecord();
        record.setFoodName("未识别食物");
        record.setCalories(0);
        record.setProtein(0.0);
        record.setCarbs(0.0);
        record.setFat(0.0);
        record.setFiber(0.0);
        record.setIsAiRecognized(true);
        record.setMealType("SNACK");
        return record;
    }

    public String getNutritionAdvice(Long userId, HealthProfile profile,
                                     Integer dailyCalories, Integer targetCalories,
                                     Double totalProtein, Double totalCarbs, Double totalFat) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一位专业的营养师和健康顾问。请根据以下用户信息提供个性化、详细且实用的饮食建议。\n\n");
        prompt.append("【用户健康档案】\n");
        if (profile != null) {
            if (profile.getFitnessGoal() != null) {
                prompt.append("- 健身目标：").append(profile.getFitnessGoal()).append("\n");
            }
            if (profile.getActivityLevel() != null) {
                prompt.append("- 活动水平：").append(profile.getActivityLevel()).append("\n");
            }
            if (profile.getHeight() != null && profile.getWeight() != null) {
                prompt.append("- 身高：").append(profile.getHeight()).append("cm，体重：").append(profile.getWeight()).append("kg\n");
                if (profile.getBmi() != null) {
                    prompt.append("- BMI：").append(String.format("%.1f", profile.getBmi())).append("\n");
                }
            }
            if (profile.getTargetWeight() != null) {
                prompt.append("- 目标体重：").append(profile.getTargetWeight()).append("kg\n");
            }
            if (profile.getDietaryRestrictions() != null && !profile.getDietaryRestrictions().isEmpty()) {
                prompt.append("- 饮食限制：").append(profile.getDietaryRestrictions()).append("\n");
            }
            if (profile.getAllergies() != null && !profile.getAllergies().isEmpty()) {
                prompt.append("- 过敏信息：").append(profile.getAllergies()).append("\n");
            }
        }
        prompt.append("\n【今日营养摄入情况】\n");
        prompt.append("- 已摄入卡路里：").append(dailyCalories != null ? dailyCalories : 0).append(" 卡\n");
        if (targetCalories != null) {
            prompt.append("- 目标卡路里：").append(targetCalories).append(" 卡\n");
            int remaining = targetCalories - (dailyCalories != null ? dailyCalories : 0);
            if (remaining > 0) {
                prompt.append("- 剩余卡路里：").append(remaining).append(" 卡\n");
            } else if (remaining < 0) {
                prompt.append("- 超出卡路里：").append(Math.abs(remaining)).append(" 卡（已超量）\n");
            }
        }
        if (totalProtein != null) {
            prompt.append("- 蛋白质：").append(String.format("%.1f", totalProtein)).append("g\n");
        }
        if (totalCarbs != null) {
            prompt.append("- 碳水化合物：").append(String.format("%.1f", totalCarbs)).append("g\n");
        }
        if (totalFat != null) {
            prompt.append("- 脂肪：").append(String.format("%.1f", totalFat)).append("g\n");
        }
        prompt.append("\n【请提供以下建议】\n");
        prompt.append("1. 针对用户健身目标的个性化饮食建议（具体到每餐搭配）\n");
        prompt.append("2. 今日营养摄入的详细评价（指出不足和优点）\n");
        if (targetCalories != null && dailyCalories != null && dailyCalories > targetCalories) {
            prompt.append("3. 卡路里超量的应对策略和调整建议\n");
        } else if (targetCalories != null && dailyCalories != null) {
            prompt.append("3. 剩余卡路里的合理分配建议\n");
        }
        prompt.append("4. 具体的食物推荐（列出3-5种适合的食物，考虑饮食限制和过敏信息）\n");
        prompt.append("5. 营养搭配建议（蛋白质、碳水、脂肪的合理比例）\n");
        prompt.append("6. 下一餐的建议（具体到食物种类和分量）\n");
        prompt.append("\n请用中文回答，内容要专业、具体、实用，语气要友好鼓励。");
        return callSparkChatAPI(prompt.toString(), null);
    }

    public String answerFitnessQuestion(String question) {
        String systemPrompt = "你是一位经验丰富的健身教练和健康顾问，拥有丰富的运动科学、营养学和康复训练知识。你的回答应该：\n" +
                "1. 专业准确：基于科学的运动原理和营养知识\n" +
                "2. 具体实用：提供可操作的建议和方案\n" +
                "3. 安全第一：强调正确的动作要领和注意事项\n" +
                "4. 鼓励支持：用友好、鼓励的语气与用户交流\n" +
                "5. 个性化：根据问题提供针对性的建议\n\n" +
                "如果用户询问关于模型、AI身份或技术问题，请回答：您好，我是依托讯飞星火大模型的智能助手，为您提供健身和营养方面的咨询服务，你可以直接告诉我你的需求。\n\n" +
                "现在请回答用户的问题：";
        return callSparkChatAPI(question, systemPrompt);
    }

    private String callSparkChatAPI(String userPrompt, String systemPrompt) {
        String authUrl = buildSparkChatAuthUrl();
        String requestJson = buildSparkChatRequestJson(systemPrompt, userPrompt);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> responseRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();
        AtomicReference<WebSocket> webSocketRef = new AtomicReference<>();

        Request request = new Request.Builder()
                .url(authUrl)
                .get()
                .build();

        WebSocket webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                webSocketRef.set(ws);
                ws.send(requestJson);
            }

            @Override
            public void onMessage(WebSocket ws, String message) {
                try {
                    Map<String, Object> resp = objectMapper.readValue(message, Map.class);
                    if (resp.containsKey("payload")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = (Map<String, Object>) resp.get("payload");
                        if (payload != null && payload.containsKey("choices")) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> choices = (List<Map<String, Object>>) payload.get("choices");
                            if (!choices.isEmpty()) {
                                Map<String, Object> choice = choices.get(0);
                                if (choice.containsKey("message")) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> msgObj = (Map<String, Object>) choice.get("message");
                                    String content = (String) msgObj.get("content");
                                    responseRef.set(content);
                                }
                            }
                        }
                    }
                    if (resp.containsKey("header")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> header = (Map<String, Object>) resp.get("header");
                        if (header != null && "0".equals(header.get("code"))) {
                            ws.close(1000, "completed");
                        }
                    }
                } catch (Exception e) {
                    errorRef.set("解析响应失败: " + e.getMessage());
                }
                latch.countDown();
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                latch.countDown();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                errorRef.set("WebSocket错误: " + t.getMessage());
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(timeout, TimeUnit.MILLISECONDS);
            if (!completed) {
                errorRef.set("等待响应超时");
            }
            if (errorRef.get() != null) {
                System.err.println("讯飞文本API错误: " + errorRef.get());
                return "AI服务暂时不可用，请稍后再试。";
            }
            return responseRef.get() != null ? responseRef.get() : "AI服务暂时不可用，请稍后再试。";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("讯飞文本API调用被中断: " + e.getMessage());
            return "AI服务调用被中断，请稍后再试。";
        } finally {
            if (webSocketRef.get() != null) {
                try {
                    webSocketRef.get().close(1000, "finished");
                } catch (Exception e) {
                }
            }
        }
    }

    private String buildSparkChatAuthUrl() {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String signature = generateSparkSignature(timestamp);
            String chatUrl = sparkBaseUrl.replace("/v2.1/image", "/v2.1/chat");
            StringBuilder url = new StringBuilder(chatUrl);
            url.append("?authorization=").append(generateAuthorization(timestamp));
            url.append("&X-Appid=").append(sparkAppId);
            url.append("&X-CurTime=").append(timestamp);
            url.append("&X-Param=").append(generateChatParam());
            url.append("&X-CheckSum=").append(signature);
            return url.toString();
        } catch (Exception e) {
            return sparkBaseUrl.replace("/v2.1/image", "/v2.1/chat");
        }
    }

    private String generateChatParam() {
        try {
            Map<String, Object> param = new HashMap<>();
            Map<String, Object> chat = new HashMap<>();
            chat.put("domain", "general");
            chat.put("temperature", 0.7);
            chat.put("max_tokens", 2000);
            param.put("chat", chat);
            return Base64.getEncoder().encodeToString(objectMapper.writeValueAsString(param).getBytes());
        } catch (Exception e) {
            return "";
        }
    }

    private String buildSparkChatRequestJson(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> request = new HashMap<>();
            Map<String, Object> header = new HashMap<>();
            header.put("app_id", sparkAppId);
            header.put("uid", UUID.randomUUID().toString());
            request.put("header", header);

            Map<String, Object> parameter = new HashMap<>();
            Map<String, Object> chat = new HashMap<>();
            chat.put("domain", "general");
            chat.put("temperature", 0.7);
            chat.put("max_tokens", 2000);
            parameter.put("chat", chat);
            request.put("parameter", parameter);

            Map<String, Object> payload = new HashMap<>();
            List<Map<String, Object>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                Map<String, Object> systemMsg = new HashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", systemPrompt);
                messages.add(systemMsg);
            }
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.add(userMsg);
            payload.put("message", messages);
            request.put("payload", payload);

            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildWorkoutPlanPrompt(User user, HealthProfile profile) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一位专业的健身教练和运动科学专家。请根据以下用户信息，生成一个详细、科学、可执行的个性化健身训练计划。\n\n");
        prompt.append("【用户基本信息】\n");
        prompt.append("- 用户名：").append(user.getUsername()).append("\n");
        if (profile != null) {
            prompt.append("\n【健康档案】\n");
            if (profile.getFitnessGoal() != null) {
                prompt.append("- 健身目标：").append(profile.getFitnessGoal()).append("\n");
            }
            if (profile.getActivityLevel() != null) {
                prompt.append("- 活动水平：").append(profile.getActivityLevel()).append("\n");
            }
            if (profile.getHeight() != null && profile.getWeight() != null) {
                prompt.append("- 身高：").append(profile.getHeight()).append("cm\n");
                prompt.append("- 体重：").append(profile.getWeight()).append("kg\n");
                if (profile.getBmi() != null) {
                    prompt.append("- BMI：").append(String.format("%.1f", profile.getBmi())).append("\n");
                }
            }
            if (profile.getTargetWeight() != null) {
                prompt.append("- 目标体重：").append(profile.getTargetWeight()).append("kg\n");
            }
            if (profile.getBodyFat() != null) {
                prompt.append("- 体脂率：").append(String.format("%.1f", profile.getBodyFat())).append("%\n");
            }
            if (profile.getMedicalHistory() != null && !profile.getMedicalHistory().isEmpty()) {
                prompt.append("- 病史：").append(profile.getMedicalHistory()).append("\n");
            }
        }
        prompt.append("\n【请生成训练计划要求】\n");
        prompt.append("1. 计划时长：4周（28天）\n");
        prompt.append("2. 训练频率：根据用户活动水平合理设置（每周3-5次）\n");
        prompt.append("3. 训练内容：\n");
        prompt.append("   - 针对用户健身目标设计（减脂/增肌/塑形/健康）\n");
        prompt.append("   - 包含有氧运动和力量训练的科学搭配\n");
        prompt.append("   - 每周的训练安排要循序渐进\n");
        prompt.append("   - 每个训练日包含具体的运动项目、组数、次数、重量、时长、休息时间\n");
        prompt.append("4. 难度设置：根据用户活动水平设置合适的难度（初级/中级/高级）\n");
        prompt.append("5. 安全考虑：考虑用户的健康状况，避免高风险动作\n");
        prompt.append("\n请以JSON格式返回训练计划，格式如下：\n");
        prompt.append("{\n");
        prompt.append("  \"duration\": 28,\n");
        prompt.append("  \"frequency\": 4,\n");
        prompt.append("  \"difficulty\": \"中级\",\n");
        prompt.append("  \"goal\": \"减脂\",\n");
        prompt.append("  \"description\": \"计划描述\",\n");
        prompt.append("  \"planItems\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"dayOfWeek\": 1,\n");
        prompt.append("      \"exerciseName\": \"运动名称\",\n");
        prompt.append("      \"exerciseType\": \"力量/有氧/混合\",\n");
        prompt.append("      \"sets\": 3,\n");
        prompt.append("      \"reps\": 12,\n");
        prompt.append("      \"weight\": 10.0,\n");
        prompt.append("      \"duration\": 1800,\n");
        prompt.append("      \"restTime\": 60,\n");
        prompt.append("      \"instructions\": \"动作说明\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        prompt.append("\n如果无法返回JSON，请提供详细的文字描述，我会据此生成计划。");
        return prompt.toString();
    }

    private WorkoutPlan parseWorkoutPlanResponse(String response) {
        WorkoutPlan plan = new WorkoutPlan();
        plan.setDuration(28);
        plan.setFrequency(4);
        plan.setDifficulty("中级");
        plan.setGoal("综合训练");
        try {
            String jsonStr = response.trim();
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```json") + 7);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```")).trim();
            } else if (jsonStr.contains("```")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```") + 3);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```")).trim();
            }
            if (jsonStr.contains("\"duration\"")) {
                String duration = extractJsonValue(jsonStr, "duration");
                if (duration != null) {
                    try {
                        plan.setDuration(Integer.parseInt(duration));
                    } catch (NumberFormatException e) {
                    }
                }
            }
            if (jsonStr.contains("\"frequency\"")) {
                String frequency = extractJsonValue(jsonStr, "frequency");
                if (frequency != null) {
                    try {
                        plan.setFrequency(Integer.parseInt(frequency));
                    } catch (NumberFormatException e) {
                    }
                }
            }
            if (jsonStr.contains("\"difficulty\"")) {
                String difficulty = extractJsonValue(jsonStr, "difficulty");
                if (difficulty != null) {
                    plan.setDifficulty(difficulty);
                }
            }
            if (jsonStr.contains("\"goal\"")) {
                String goal = extractJsonValue(jsonStr, "goal");
                if (goal != null) {
                    plan.setGoal(goal);
                }
            }
            if (jsonStr.contains("\"description\"")) {
                String description = extractJsonValue(jsonStr, "description");
                if (description != null) {
                    plan.setDescription(description);
                }
            }
            List<WorkoutPlanItem> items = generatePlanItemsFromResponse(jsonStr, plan.getFrequency(), plan.getGoal());
            plan.setPlanItems(items);
        } catch (Exception e) {
            plan.setDescription("基于您的健康档案生成的个性化训练计划");
            List<WorkoutPlanItem> items = generateDefaultPlanItems(plan.getFrequency(), plan.getGoal());
            plan.setPlanItems(items);
        }
        return plan;
    }

    private List<WorkoutPlanItem> generatePlanItemsFromResponse(String jsonStr, Integer frequency, String goal) {
        List<WorkoutPlanItem> items = new ArrayList<>();
        int sessionsPerWeek = frequency != null && frequency > 0 ? frequency : 4;
        String[] exerciseTypes = switch (goal != null ? goal : "") {
            case "减脂" -> new String[]{"有氧", "力量", "有氧", "力量", "有氧"};
            case "增肌" -> new String[]{"力量", "力量", "力量", "力量", "力量"};
            case "塑形" -> new String[]{"力量", "有氧", "力量", "有氧", "混合"};
            default -> new String[]{"混合", "力量", "有氧", "混合", "力量"};
        };
        String[] exerciseNames = switch (goal != null ? goal : "") {
            case "减脂" -> new String[]{"HIIT有氧训练", "全身力量训练", "跑步/快走", "核心力量训练", "有氧舞蹈"};
            case "增肌" -> new String[]{"胸背训练", "腿臀训练", "肩臂训练", "全身力量", "核心强化"};
            case "塑形" -> new String[]{"上半身塑形", "有氧燃脂", "下半身塑形", "全身拉伸", "综合训练"};
            default -> new String[]{"全身综合训练", "力量训练", "有氧训练", "柔韧性训练", "核心训练"};
        };
        int dayIndex = 0;
        for (int week = 0; week < 4; week++) {
            for (int session = 0; session < sessionsPerWeek && dayIndex < 28; session++) {
                int dayOfWeek = (dayIndex % 7) + 1;
                WorkoutPlanItem item = new WorkoutPlanItem();
                item.setDayOfWeek(dayOfWeek);
                item.setExerciseName(exerciseNames[session % exerciseNames.length]);
                item.setExerciseType(exerciseTypes[session % exerciseTypes.length]);
                item.setOrderIndex(dayIndex + 1);
                if ("力量".equals(item.getExerciseType()) || "混合".equals(item.getExerciseType())) {
                    item.setSets(3 + (week * 1));
                    item.setReps(12 - (week * 1));
                    item.setWeight(10.0 + (week * 2.5));
                    item.setRestTime(60 + (week * 10));
                } else {
                    item.setDuration(1800 + (week * 300));
                    item.setRestTime(30);
                }
                item.setInstructions("第" + (week + 1) + "周训练，注意动作规范，循序渐进");
                items.add(item);
                dayIndex++;
            }
        }
        return items;
    }

    private List<WorkoutPlanItem> generateDefaultPlanItems(Integer frequency, String goal) {
        return generatePlanItemsFromResponse("", frequency, goal);
    }

    private WorkoutPlan createDefaultWorkoutPlan(User user, HealthProfile profile) {
        WorkoutPlan plan = new WorkoutPlan();
        plan.setName("默认训练计划");
        plan.setDescription("基础训练计划");
        plan.setDuration(28);
        plan.setFrequency(3);
        plan.setDifficulty("初级");
        plan.setGoal(profile != null ? profile.getFitnessGoal() : "健康");
        plan.setIsAiGenerated(false);
        return plan;
    }
}
