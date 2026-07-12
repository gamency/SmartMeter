# 📱 智能电表抄表助手 - Android App

## 项目概述

**智能电表抄表助手**是一款专为自建房、出租屋电费管理设计的Android移动应用。通过连接后端电力统计系统，实现电费数据的可视化展示、智能抄表拍照、分户用电统计等功能。

## 🎯 核心功能

### 1. 数据看板 Dashboard
- **实时电费概览**：显示总用电量、总电费、盈亏状态
- **房东/租户分摊**：清晰展示双方承担的电费比例和变化趋势
- **分户用电排行**：按房间显示用电量排名，可视化柱状图展示
- **近7天趋势**：每日用电量变化趋势图

### 2. 智能抄表 Smart Meter Reading
- **拍照识别**：拍摄电表照片，自动识别读数
- **AI辅助识别**：集成OCR技术，提高识别准确率
- **批量处理**：支持多房间连续拍照抄表
- **人工复核**：提供识别结果复核界面

### 3. 数据管理 Data Management
- **日期筛选**：支持自定义时间段数据查询
- **下拉刷新**：实时同步最新数据
- **分户详情**：查看每个房间的详细用电记录
- **数据校验**：自动校验分户用电总和与总表数据

### 4. 用户体验 UX Features
- **现代化UI设计**：采用Material Design 3设计语言
- **马卡龙色系**：柔和的分户颜色标识
- **响应式布局**：适配不同屏幕尺寸
- **离线缓存**：网络不佳时显示最近数据

## 🏗️ 技术架构

### 前端技术栈
- **开发语言**：Java (Android原生开发)
- **最低SDK**：API 24 (Android 7.0)
- **目标SDK**：API 36 (Android 14)
- **UI框架**：AndroidX AppCompat, Material Design
- **网络请求**：OkHttp 4.12.0
- **数据解析**：JSONObject/JSONArray
- **数据库**：Room 2.6.1 (本地缓存)

### 项目结构
```
SmartMeterApp/
├── app/
│   ├── src/main/java/com/example/smartmeter/
│   │   ├── MainActivity.java          # 主界面，数据看板
│   │   ├── SmartPhotoActivity.java    # 智能拍照抄表
│   │   ├── SmartReviewActivity.java   # 识别结果复核
│   │   ├── DistributionActivity.java  # 分户用电详情
│   │   ├── RoomAdapter.java          # 房间列表适配器
│   │   ├── ReviewAdapter.java        # 复核列表适配器
│   │   ├── PendingAdapter.java       # 待处理列表适配器
│   │   ├── SmartReadingDao.java      # 智能抄表数据访问
│   │   └── RoomItem.java             # 房间数据模型
│   ├── src/main/res/
│   │   ├── layout/                   # 布局文件
│   │   ├── values/                   # 资源文件
│   │   └── drawable/                 # 图片资源
│   └── build.gradle.kts             # 构建配置
└── gradle/                          # Gradle配置
```

### 后端接口
应用连接的后端服务地址：`http://192.168.10.12:5000`
- **GET /api/dashboard** - 获取核心指标数据
- **GET /api/rooms_usage** - 获取房间用电数据
- **GET /api/daily_trend** - 获取趋势数据
- **POST /api/smart/upload** - 上传电表照片
- **GET /api/smart/pending** - 获取待复核记录

## 🚀 快速开始

### 环境要求
- Android Studio Giraffe (2022.3.1) 或更高版本
- Java 11 JDK
- Android SDK 24-36

### 构建步骤
1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd SmartMeterApp
   ```

2. **修改服务器地址**
   在 `MainActivity.java` 第35行修改 `BASE_URL` 为你的后端服务器地址：
   ```java
   public static final String BASE_URL = "http://你的服务器IP:5000";
   ```

3. **构建运行**
   - 使用Android Studio打开项目
   - 连接Android设备或启动模拟器
   - 点击运行按钮 ▶️

### 权限配置
应用需要以下权限：
- **网络权限**：访问后端API
- **相机权限**：拍摄电表照片
- **存储权限**：保存照片文件

## 📱 界面说明

### 1. 主界面 (MainActivity)
- 顶部卡片：总用电量、总电费、盈亏状态
- 中间部分：房东/租户分摊详情
- 下半部：分户用电排行、近7天趋势
- 右下角：智能抄表入口按钮

### 2. 智能抄表界面 (SmartPhotoActivity)
- 相机预览区域
- 拍照按钮
- 相册选择
- 识别结果显示

### 3. 复核界面 (SmartReviewActivity)
- 待复核记录列表
- 原始照片预览
- 识别结果编辑
- 确认/驳回操作

### 4. 分户详情界面 (DistributionActivity)
- 所有房间用电详情
- 按楼层分组
- 用电量排序
- 导出功能

## 🔧 配置说明

### 颜色主题
应用使用马卡龙色系标识不同房间：
```xml
<color name="room_1">#FFB6C1</color> <!-- 浅粉色 -->
<color name="room_2">#87CEEB</color> <!-- 天蓝色 -->
<color name="room_3">#98FB98</color> <!-- 浅绿色 -->
<!-- ... 共10种颜色 -->
```

### 网络配置
- 连接超时：10秒
- 读取超时：10秒
- 自动重试：否
- 缓存策略：无（实时数据）

## 🧪 测试

### 单元测试
```bash
./gradlew test
```

### 仪器测试
```bash
./gradlew connectedAndroidTest
```

### 测试覆盖
- 网络请求测试
- UI交互测试
- 数据解析测试
- 权限处理测试

## 📊 数据流

```
用户操作 → 界面更新 → 网络请求 → 后端API → 数据解析 → 界面渲染
    ↑           ↓
本地缓存 ←─── 数据存储
```

### 关键数据模型
```java
class RoomItem {
    int id;           // 房间ID
    String name;      // 房间名称
    int floor;        // 楼层
    String roomType;  // 房间类型
    double totalKwh;  // 用电量
    double price;     // 单价
}
```

## 🔄 更新日志

### v1.0.0 (当前版本)
- ✅ 基础数据看板功能
- ✅ 智能拍照抄表
- ✅ 分户用电统计
- ✅ 趋势图表展示
- ✅ 日期筛选功能
- ✅ 下拉刷新同步

### 计划功能
- [ ] 离线模式支持
- [ ] 数据导出PDF
- [ ] 电费预测功能
- [ ] 多语言支持
- [ ] 深色模式

## 🛠️ 故障排除

### 常见问题

1. **网络连接失败**
   - 检查 `BASE_URL` 配置
   - 确认设备网络连接
   - 检查后端服务状态

2. **相机无法启动**
   - 检查相机权限
   - 确认设备有相机硬件
   - 尝试使用相册功能

3. **数据不更新**
   - 下拉刷新强制更新
   - 检查网络连接
   - 查看后端API响应

4. **应用崩溃**
   - 检查日志输出
   - 确认Android版本兼容性
   - 清理应用缓存

### 调试模式
启用调试日志：
```java
// 在代码中添加日志
Log.d("SmartMeter", "数据加载完成: " + data);
```

## 📈 性能优化

### 已实施优化
- ✅ 图片懒加载
- ✅ 列表项回收
- ✅ 网络请求合并
- ✅ 内存泄漏检测

### 待优化项
- [ ] 图片压缩处理
- [ ] 数据预加载
- [ ] 缓存策略优化
- [ ] 启动速度优化

## 🤝 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 📞 联系支持

- **问题反馈**：GitHub Issues
- **功能建议**：GitHub Discussions
- **紧急支持**：<support@example.com>

---

**开发团队**：智能电表开发组  
**最后更新**：2026年7月  
**版本**：v1.0.0