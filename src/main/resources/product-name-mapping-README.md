# 产品名称映射配置说明

## 概述

本系统使用配置文件 `product-name-mapping.properties` 来管理产品名称的映射关系。通过配置文件，可以轻松地将从订单中提取的原始产品名称映射为标准的产品名称。

## 配置文件位置

- 文件路径: `src/main/resources/product-name-mapping.properties`
- 默认配置项: `application.properties` 中的 `app.mapping.product-name-file`

## 配置文件格式

配置文件采用标准的 Java Properties 格式：

```properties
# 注释行以 # 开头
原始名称=标准名称
```

### 示例

```properties
# 包装盒类型
BOX=包装盒
Box=包装盒
Oval Box=Oval Box-椭圆形开窗木盒
Square Box=Square Box-方形开窗木盒

# 产品类型（可根据需要扩展）
# Cufflink=袖扣
# Tie Clip=领带夹
# Pet Avatar=宠物头像
```

## 使用说明

### 1. 添加新的映射关系

在 `product-name-mapping.properties` 文件中添加一行：

```properties
YourOriginalName=你的标准名称
```

### 2. 修改现有映射关系

直接修改对应的配置行：

```properties
# 修改前
Oval Box=Oval Box-椭圆形木盒

# 修改后
Oval Box=Oval Box-椭圆形开窗木盒
```

### 3. 删除映射关系

在配置文件中删除对应行，或添加 `#` 注释掉。

### 4. 使配置生效

**重要**: 修改配置文件后需要重启应用程序才能生效。

如果需要动态加载配置，可以通过以下方式（需开发相应的接口）：
```java
@Resource
private ProductNameMapper productNameMapper;

// 重新加载配置
productNameMapper.reloadMapping();
```

## 系统工作流程

1. **PDF 解析**: 系统从 PDF 文件中提取订单信息
2. **原始识别**: 识别包装盒类型（如 "Box", "Oval Box", "Square Box"）
3. **名称映射**: 通过 `ProductNameMapper` 查找映射关系
   - 如果找到映射，使用标准名称
   - 如果未找到映射，使用原始名称
4. **数据写入**: 将标准化的产品名称写入 Excel 文件

## 示例场景

### 场景 1: Oval Box 映射

**原始数据**: 动态属性中包含 "Oval Box"

**配置文件**:
```properties
Oval Box=Oval Box-椭圆形开窗木盒
```

**结果**: Excel 中显示为 "Oval Box-椭圆形开窗木盒"

### 场景 2: 未配置的映射

**原始数据**: 动态属性中包含 "Gift Box"

**配置文件**: 未配置 "Gift Box" 映射

**结果**: Excel 中显示为 "Gift Box"（使用原始名称）

## 常见问题

### Q: 修改配置后为什么没有生效？
A: 需要重启应用程序才能加载新的配置。

### Q: 支持中文映射吗？
A: 支持，配置文件使用 UTF-8 编码，支持中文。

### Q: 如何查看当前所有的映射关系？
A: 可以直接打开 `product-name-mapping.properties` 文件查看，或通过日志查看加载情况。

### Q: 映射是否区分大小写？
A: 是的，映射区分大小写。但系统会进行不区分大小写的匹配作为后备方案。

## 注意事项

1. **编码格式**: 配置文件使用 UTF-8 编码，确保中文正确显示
2. **注释**: 使用 `#` 开头的行作为注释，不会被解析
3. **空格**: 等号前后允许有空格，但推荐格式为 `key=value`
4. **重复键**: 如果配置文件中有重复的键，后出现的会覆盖前面的

## 维护建议

1. 定期检查映射关系是否完整
2. 发现新的产品类型时，及时添加映射配置
3. 废弃的产品类型，可以考虑注释掉对应的映射
4. 保持配置文件的良好格式和注释，便于后续维护

## 技术实现

- **服务类**: `ProductNameMapper.java`
- **加载时机**: 应用启动时通过 `@PostConstruct` 注解自动加载
- **匹配策略**:
  1. 精确匹配（区分大小写）
  2. 不区分大小写匹配（后备方案）
  3. 未找到映射则返回原始名称
