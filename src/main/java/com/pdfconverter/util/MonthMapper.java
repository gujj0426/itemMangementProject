package com.pdfconverter.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 月份映射工具类
 * 用于根据月份英文名称（如"12 Topaz Snow"）返回对应中文月份（如"十二月"）
 */
public class MonthMapper {

    /**
     * 月份映射表
     * Key: 完整月份描述 (如 "1 Garnet Glow")
     * Value: 中文月份 (如 "一月")
     */
    private static final Map<String, String> MONTH_MAP = new HashMap<>();

    /**
     * 月份编码到中文月份的映射（仅用数字作为key）
     * Key: 月份数字 (如 "12")
     * Value: 中文月份 (如 "十二月")
     */
    private static final Map<String, String> CODE_TO_MONTH_MAP = new HashMap<>();

    /**
     * 静态初始化块 - 初始化月份映射配置
     */
    static {
        initializeMonthMapping();
    }

    /**
     * 初始化月份映射配置
     * 对应关系：
     * 1 Garnet Glow    → 一月
     * 2 Amethyst Dream → 二月
     * 3 Aquamarine Sky → 三月
     * 4 Diamond Twilight → 四月
     * 5 Emerald Morning → 五月
     * 6 Pearl Blossom → 六月
     * 7 Ruby Rose → 七月
     * 8 Peridot Breeze → 八月
     * 9 Sapphire Night → 九月
     * 10 Pink Tourmaline → 十月
     * 11 Citrine Light → 十一月
     * 12 Topaz Snow → 十二月
     */
    private static void initializeMonthMapping() {
        addMonthMapping("1", "Garnet Glow", "一月");
        addMonthMapping("2", "Amethyst Dream", "二月");
        addMonthMapping("3", "Aquamarine Sky", "三月");
        addMonthMapping("4", "Diamond Twilight", "四月");
        addMonthMapping("5", "Emerald Morning", "五月");
        addMonthMapping("6", "Pearl Blossom", "六月");
        addMonthMapping("7", "Ruby Rose", "七月");
        addMonthMapping("8", "Peridot Breeze", "八月");
        addMonthMapping("9", "Sapphire Night", "九月");
        addMonthMapping("10", "Pink Tourmaline", "十月");
        addMonthMapping("11", "Citrine Light", "十一月");
        addMonthMapping("12", "Topaz Snow", "十二月");
    }

    /**
     * 添加月份映射
     *
     * @param code       月份数字编码（如 "1"）
     * @param value      完整月份描述（如 "Garnet Glow"）
     * @param chineseMonth 中文月份（如 "一月"）
     */
    private static void addMonthMapping(String code, String value, String chineseMonth) {
        String fullValue = code + " " + value;
        MONTH_MAP.put(fullValue, chineseMonth);
        CODE_TO_MONTH_MAP.put(code, chineseMonth);
    }

    /**
     * 根据传入的完整value返回对应的中文月份
     *
     * @param value 完整月份描述（如 "12 Topaz Snow"）
     * @return 对应的中文月份（如 "十二月"），如果未找到则返回null
     *
     * 示例：
     * <pre>
     * getMonthFromValue("12 Topaz Snow") → "十二月"
     * getMonthFromValue("1 Garnet Glow") → "一月"
     * getMonthFromValue("6 Pearl Blossom") → "六月"
     * </pre>
     */
    public static String getMonthFromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        // 去除首尾空格
        String trimmedValue = value.trim();

        // 精确匹配
        String result = MONTH_MAP.get(trimmedValue);
        if (result != null) {
            return result;
        }

        // 不区分大小写匹配
        for (Map.Entry<String, String> entry : MONTH_MAP.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(trimmedValue)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 根据月份数字编码返回对应的中文月份
     *
     * @param code 月份数字编码（如 "12"）
     * @return 对应的中文月份（如 "十二月"），如果未找到则返回null
     *
     * 示例：
     * <pre>
     * getMonthFromCode("12") → "十二月"
     * getMonthFromCode("1") → "一月"
     * getMonthFromCode("6") → "六月"
     * </pre>
     */
    public static String getMonthFromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }

        // 去除首尾空格
        String trimmedCode = code.trim();

        return CODE_TO_MONTH_MAP.get(trimmedCode);
    }

    /**
     * 根据月份数字编码返回完整的月份描述（含编码）
     *
     * @param code 月份数字编码（如 "12"）
     * @return 完整的月份描述（如 "12 Topaz Snow"），如果未找到则返回null
     *
     * 示例：
     * <pre>
     * getFullValueFromCode("12") → "12 Topaz Snow"
     * getFullValueFromCode("1") → "1 Garnet Glow"
     * getFullValueFromCode("6") → "6 Pearl Blossom"
     * </pre>
     */
    public static String getFullValueFromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }

        // 去除首尾空格
        String trimmedCode = code.trim();

        // 查找对应的完整value
        for (Map.Entry<String, String> entry : MONTH_MAP.entrySet()) {
            if (entry.getKey().startsWith(trimmedCode + " ")) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * 获取所有月份映射配置
     *
     * @return 月份映射Map的副本
     */
    public static Map<String, String> getAllMonthMappings() {
        return new HashMap<>(MONTH_MAP);
    }

    /**
     * 获取所有月份编码到中文月份的映射配置
     *
     * @return 月份编码映射Map的副本
     */
    public static Map<String, String> getAllCodeMappings() {
        return new HashMap<>(CODE_TO_MONTH_MAP);
    }

    /**
     * 检查是否存在指定的月份value
     *
     * @param value 完整月份描述
     * @return 如果存在返回true，否则返回false
     */
    public static boolean containsValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        String trimmedValue = value.trim();
        return MONTH_MAP.containsKey(trimmedValue) ||
               MONTH_MAP.entrySet().stream()
                       .anyMatch(entry -> entry.getKey().equalsIgnoreCase(trimmedValue));
    }

    /**
     * 检查是否存在指定的月份编码
     *
     * @param code 月份数字编码
     * @return 如果存在返回true，否则返回false
     */
    public static boolean containsCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }

        return CODE_TO_MONTH_MAP.containsKey(code.trim());
    }

    /**
     * 打印所有月份映射配置（用于调试）
     */
    public static void printAllMappings() {
        System.out.println("========== 月份映射配置 ==========");
        for (Map.Entry<String, String> entry : MONTH_MAP.entrySet()) {
            System.out.println(entry.getKey() + " → " + entry.getValue());
        }
        System.out.println("=================================");
    }
}
