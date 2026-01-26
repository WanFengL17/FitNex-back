package com.fitnex.service.ai;

import com.fitnex.entity.HealthProfile;
import com.fitnex.entity.NutritionRecord;
import com.fitnex.entity.User;
import com.fitnex.entity.WorkoutPlan;
import com.fitnex.entity.WorkoutPlanItem;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIService {

    @Value("${ai.openai.api-key}")
    private String apiKey;

    @Value("${ai.openai.base-url}")
    private String baseUrl;

    @Value("${ai.openai.model}")
    private String model;

    @Value("${ai.openai.timeout:30000}")
    private int timeout;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    public WorkoutPlan generateWorkoutPlan(User user, HealthProfile profile) {
        try {
            String prompt = buildWorkoutPlanPrompt(user, profile);
            String response = callAI(prompt);
            
            // 解析AI响应并创建训练计划
            WorkoutPlan plan = parseWorkoutPlanResponse(response);
            plan.setName("AI智能训练计划");
            plan.setDescription("基于您的健康档案生成的个性化训练计划");
            plan.setIsAiGenerated(true);
            
            return plan;
        } catch (Exception e) {
            // 如果AI调用失败，返回默认计划
            return createDefaultWorkoutPlan(user, profile);
        }
    }

    public NutritionRecord recognizeFoodFromImage(MultipartFile imageFile) {
        try {
            // 从MultipartFile读取图片并转换为base64
            String base64Image = encodeImageToBase64(imageFile);
            
            // 构建Vision API请求
            String prompt = "请识别这张图片中的食物，并返回JSON格式的营养信息。格式如下：" +
                    "{\"foodName\": \"食物名称\", \"quantity\": 数量(克), \"calories\": 卡路里, " +
                    "\"protein\": 蛋白质(克), \"carbs\": 碳水化合物(克), \"fat\": 脂肪(克), " +
                    "\"fiber\": 纤维(克), \"mealType\": \"BREAKFAST|LUNCH|DINNER|SNACK\"}。只返回JSON，不要其他文字。";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4-vision-preview");
            
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            
            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", prompt);
            content.add(textContent);
            
            Map<String, Object> imageContent = new HashMap<>();
            imageContent.put("type", "image_url");
            Map<String, String> imageUrlMap = new HashMap<>();
            imageUrlMap.put("url", "data:image/jpeg;base64," + base64Image);
            imageContent.put("image_url", imageUrlMap);
            content.add(imageContent);
            
            message.put("content", content);
            messages.add(message);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 500);
            requestBody.put("temperature", 0.3);
            
            // 调用OpenAI Vision API
            Map<String, Object> response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
            
            // 解析响应
            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> messageResponse = (Map<String, Object>) choices.get(0).get("message");
                    String contentText = (String) messageResponse.get("content");
                    
                    // 解析JSON响应
                    return parseNutritionFromAIResponse(contentText);
                }
            }
            
            // 如果AI调用失败，返回默认值
            return createDefaultNutritionRecord();
        } catch (Exception e) {
            // 如果识别失败，返回默认值而不是抛出异常
            return createDefaultNutritionRecord();
        }
    }
    
    private String encodeImageToBase64(MultipartFile imageFile) throws IOException {
        // 直接从MultipartFile读取字节并转换为base64
        byte[] imageBytes = imageFile.getBytes();
        return Base64.getEncoder().encodeToString(imageBytes);
    }
    
    // 保留旧方法以兼容可能的其他调用
    private String encodeImageToBase64(String imageUrl) throws IOException {
        // 从文件路径读取图片
        String filePath = imageUrl;
        
        // 处理相对路径和绝对路径
        if (filePath.startsWith("/uploads/")) {
            filePath = filePath.substring(1); // 移除开头的/
        } else if (filePath.startsWith("uploads/")) {
            // 已经是相对路径
        } else if (!filePath.startsWith("/") && !filePath.contains(":")) {
            // 相对路径，添加uploads前缀
            if (!filePath.startsWith("uploads")) {
                filePath = "uploads/images/" + filePath;
            }
        }
        
        // 获取文件名
        String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
        
        // 尝试多个可能的路径
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
                // 继续尝试下一个路径
            }
        }
        
        throw new IOException("无法读取图片文件: " + imageUrl + " (尝试的路径: " + String.join(", ", possiblePaths) + ")");
    }
    
    private NutritionRecord parseNutritionFromAIResponse(String aiResponse) {
        NutritionRecord record = new NutritionRecord();
        record.setIsAiRecognized(true);
        
        try {
            // 尝试提取JSON部分
            String jsonStr = aiResponse.trim();
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```json") + 7);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```")).trim();
            } else if (jsonStr.contains("```")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```") + 3);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```")).trim();
            }
            
            // 使用Jackson解析JSON（简化版，实际应该使用ObjectMapper）
            // 这里使用简单的字符串解析
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
            // 解析失败，使用默认值
        }
        
        // 设置默认值
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
                // 字符串值
                int endIndex = json.indexOf('"', startIndex + 1);
                if (endIndex == -1) return null;
                return json.substring(startIndex + 1, endIndex);
            } else {
                // 数字值
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
            // 忽略解析错误
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
        
        return callAI(prompt.toString());
    }

    public String answerFitnessQuestion(String question) {
        String systemPrompt = "你是一位经验丰富的健身教练和健康顾问，拥有丰富的运动科学、营养学和康复训练知识。你的回答应该：\n" +
                "1. 专业准确：基于科学的运动原理和营养知识\n" +
                "2. 具体实用：提供可操作的建议和方案\n" +
                "3. 安全第一：强调正确的动作要领和注意事项\n" +
                "4. 鼓励支持：用友好、鼓励的语气与用户交流\n" +
                "5. 个性化：根据问题提供针对性的建议\n\n" +
                "如果用户询问关于模型、AI身份或技术问题，请回答：您好，我是依托default模型的智能助手，在Cursor IDE中为您提供代码编写和问题解答服务，你可以直接告诉我你的需求。\n\n" +
                "现在请回答用户的问题：";
        
        return callAIWithSystemPrompt(systemPrompt, question);
    }

    private String callAI(String prompt) {
        return callAIWithSystemPrompt(null, prompt);
    }
    
    private String callAIWithSystemPrompt(String systemPrompt, String userPrompt) {
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            messages.add(Map.of("role", "user", "content", userPrompt));
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 2000);
            requestBody.put("temperature", 0.7);

            Map<String, Object> response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();

            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }
            
            return "AI服务暂时不可用，请稍后再试。";
        } catch (Exception e) {
            return "AI服务调用失败: " + e.getMessage();
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
        plan.setDuration(28); // 默认4周
        plan.setFrequency(4); // 默认每周4次
        plan.setDifficulty("中级");
        plan.setGoal("综合训练");
        
        try {
            // 尝试提取JSON部分
            String jsonStr = response.trim();
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```json") + 7);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```")).trim();
            } else if (jsonStr.contains("```")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```") + 3);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```")).trim();
            }
            
            // 尝试解析JSON字段
            if (jsonStr.contains("\"duration\"")) {
                String duration = extractJsonValue(jsonStr, "duration");
                if (duration != null) {
                    try {
                        plan.setDuration(Integer.parseInt(duration));
                    } catch (NumberFormatException e) {
                        // 使用默认值
                    }
                }
            }
            
            if (jsonStr.contains("\"frequency\"")) {
                String frequency = extractJsonValue(jsonStr, "frequency");
                if (frequency != null) {
                    try {
                        plan.setFrequency(Integer.parseInt(frequency));
                    } catch (NumberFormatException e) {
                        // 使用默认值
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
            
            // 解析planItems（简化版，实际应该使用ObjectMapper）
            // 这里我们根据频率生成合理的训练项目
            List<WorkoutPlanItem> items = generatePlanItemsFromResponse(jsonStr, plan.getFrequency(), plan.getGoal());
            plan.setPlanItems(items);
            
        } catch (Exception e) {
            // 解析失败，使用默认计划
            plan.setDescription("基于您的健康档案生成的个性化训练计划");
            List<WorkoutPlanItem> items = generateDefaultPlanItems(plan.getFrequency(), plan.getGoal());
            plan.setPlanItems(items);
        }
        
        return plan;
    }
    
    private List<WorkoutPlanItem> generatePlanItemsFromResponse(String jsonStr, Integer frequency, String goal) {
        List<WorkoutPlanItem> items = new ArrayList<>();
        int sessionsPerWeek = frequency != null && frequency > 0 ? frequency : 4;
        
        // 根据目标设置训练类型
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
                    item.setSets(3 + (week * 1)); // 每周增加1组
                    item.setReps(12 - (week * 1)); // 每周减少1次
                    item.setWeight(10.0 + (week * 2.5)); // 每周增加重量
                    item.setRestTime(60 + (week * 10)); // 增加休息时间
                } else {
                    item.setDuration(1800 + (week * 300)); // 每周增加5分钟
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








