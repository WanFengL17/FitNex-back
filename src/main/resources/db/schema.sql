-- FitNex 数据库初始化脚本
-- 创建数据库
CREATE DATABASE IF NOT EXISTS fitnex_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE fitnex_db;

-- 注意：表结构将由JPA自动生成（hibernate.ddl-auto=update）
-- 以下是表结构说明，供参考

/*
用户表 (users)
- id: 主键
- username: 用户名（唯一）
- email: 邮箱（唯一）
- phone: 手机号（唯一）
- password: 密码（加密）
- nickname: 昵称
- avatar: 头像URL
- role: 角色（USER, ADMIN）
- memberLevel: 会员等级（BRONZE, SILVER, GOLD, PLATINUM, DIAMOND）
- loginType: 登录类型（EMAIL, PHONE, WECHAT, GOOGLE）
- enabled: 是否启用
- createdAt: 创建时间
- updatedAt: 更新时间

健康档案表 (health_profiles)
- id: 主键
- user_id: 用户ID（外键，一对一）
- birthDate: 出生日期
- gender: 性别
- height: 身高（厘米）
- weight: 体重（公斤）
- bmi: BMI值
- bodyFat: 体脂率
- muscleMass: 肌肉量
- bloodType: 血型
- medicalHistory: 病史
- fitnessGoal: 健身目标
- activityLevel: 活动水平
- targetWeight: 目标体重
- targetCalories: 目标卡路里
- allergies: 过敏信息
- dietaryRestrictions: 饮食限制
- createdAt: 创建时间
- updatedAt: 更新时间

训练计划表 (workout_plans)
- id: 主键
- user_id: 用户ID（外键）
- name: 计划名称
- description: 描述
- goal: 目标
- duration: 时长（天）
- frequency: 频率（每周次数）
- difficulty: 难度
- isActive: 是否活跃
- isAiGenerated: 是否AI生成
- createdAt: 创建时间
- updatedAt: 更新时间

训练计划项表 (workout_plan_items)
- id: 主键
- plan_id: 计划ID（外键）
- dayOfWeek: 星期几（1-7）
- exerciseName: 运动名称
- exerciseType: 运动类型
- sets: 组数
- reps: 次数
- weight: 重量
- duration: 时长（秒）
- restTime: 休息时间（秒）
- instructions: 说明
- videoUrl: 视频链接
- orderIndex: 顺序

训练记录表 (workout_records)
- id: 主键
- user_id: 用户ID（外键）
- plan_id: 计划ID（外键，可选）
- workoutName: 训练名称
- startTime: 开始时间
- endTime: 结束时间
- duration: 总时长（秒）
- caloriesBurned: 消耗卡路里
- averageHeartRate: 平均心率
- notes: 备注
- createdAt: 创建时间
- updatedAt: 更新时间

训练动作记录表 (workout_exercise_records)
- id: 主键
- workout_record_id: 训练记录ID（外键）
- exerciseName: 运动名称
- sets: 组数
- reps: 次数
- weight: 重量
- duration: 时长
- restTime: 休息时间
- notes: 备注
- orderIndex: 顺序

营养记录表 (nutrition_records)
- id: 主键
- user_id: 用户ID（外键）
- recordDate: 记录日期
- mealType: 餐次（BREAKFAST, LUNCH, DINNER, SNACK）
- foodName: 食物名称
- quantity: 数量
- unit: 单位
- calories: 卡路里
- protein: 蛋白质（克）
- carbs: 碳水化合物（克）
- fat: 脂肪（克）
- fiber: 纤维（克）
- imageUrl: 图片URL
- isAiRecognized: 是否AI识别
- notes: 备注
- createdAt: 创建时间
- updatedAt: 更新时间

身体测量表 (body_measurements)
- id: 主键
- user_id: 用户ID（外键）
- measureDate: 测量日期
- weight: 体重（公斤）
- bodyFat: 体脂率（%）
- muscleMass: 肌肉量（公斤）
- chest: 胸围（厘米）
- waist: 腰围（厘米）
- hip: 臀围（厘米）
- arm: 臂围（厘米）
- thigh: 大腿围（厘米）
- notes: 备注
- createdAt: 创建时间
- updatedAt: 更新时间

健康分析表 (health_analyses)
- id: 主键
- user_id: 用户ID（外键）
- analysisDate: 分析日期
- progressScore: 进度评分（0-100）
- progressLevel: 进度等级
- totalWorkouts: 总训练次数
- totalCaloriesBurned: 总消耗卡路里
- weightChange: 体重变化
- bodyFatChange: 体脂率变化
- riskWarnings: 风险预警（JSON）
- recommendations: 建议（JSON）
- analysisData: 分析数据（JSON）
- createdAt: 创建时间
- updatedAt: 更新时间
*/

