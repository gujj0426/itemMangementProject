package com.pdfconverter.constant;

/**
 * 产品名称枚举
 * 定义所有支持的产品名称
 */
public enum ProductName {
    /**
     * 袖扣类
     */
    CUFFLINK("Cufflink", "袖扣"),

    /**
     * 领带夹类
     */
    TIE_CLIP_DUCK_BILL("Tie Clip Duck Bill", "鸭嘴领带夹"),
    TIE_CLIP_DUCK_BILL_THICK("Tie Clip Duck Bill 2", "鸭嘴领带夹(厚)"),
    TIE_CLIP_DOUBLE_SIDED_SLIDE_IN("Tie Clip Double Sided Slide In", "双面滑入式领带夹"),
    TIE_CLIP_ROUND_DUCK_BILL("Tie Clip Round Duck Bill", "圆片鸭嘴领带夹"),

    /**
     * 相盒吊坠类
     */
    FLOWER_HEART_BOX_PENDANT("Flower Heart Box Pendant", "花卉心形相盒吊坠"),
    PATTERN_HEART_BOX_PENDANT("Pattern Heart Box Pendant", "花纹心形相盒吊坠"),
    BIRTHSTONE_RAW("Birthstone Raw", "生日石原石"),
    CATS_EYE_STONE_PENDANT("Cats Eye Stone Pendant", "猫眼石吊坠"),
    ROUND_PENDANT("Round Pendant", "圆片吊坠"),
    TANGCA_PHOTO_BOX_PENDANT("Tangca Photo Box Pendant", "唐卡相合吊坠"),

    /**
     * 胸针类
     */
    BOWKNOT_BROOCH("Bowknot Brooch", "蝴蝶结胸针"),

    /**
     * 配件类
     */
    ADDON_BIRTHSTONE("Addon Birthstone", "生日石 add-on"),
    ADDON_WINGS("Addon Wings", "翅膀 add-on"),
    ADDON_STAR_ACCESSORY("Addon Star Accessory", "星星配件"),
    ADDON_SMALL_FISH_ACCESSORY("Addon Small Fish Accessory", "小鱼配件"),

    /**
     * 链饰类
     */
    BASE_CHAIN("Base Chain", "基础链"),
    DOOR_CLASP("Door Clasp", "门字扣"),
    KEY_RING("Key Ring", "钥匙环"),
    ROTATING_SCREW_WIRE_LOOP("Rotating Screw Wire Loop", "旋转螺丝钢丝环"),

    /**
     * 宠物玩具类
     */
    BIG_BROTHER_BIG_COMMA_CAT_WAND("Big Brother Big Comma Cat Wand", "大哥大逗猫棒"),
    COMMA_CAT_WAND_LETTER_ACCESSORY("Comma Cat Wand Letter Accessory", "逗猫棒字母配饰"),
    MICROPHONE_COMMA_CAT_WAND("Microphone Comma Cat Wand", "麦克风逗猫棒"),
    BIG_THUMB_BROTHER_LOSS_BALL_TOY("Big Thumb Brother Loss Ball Toy", "大拇哥丢球玩具"),
    PEAR_SHAPED_POOP_BAG("Pear Shaped Poop Bag", "梨形拾便袋"),
    PET_MUZZLE("Pet Muzzle", "宠物围嘴"),

    /**
     * 包装类
     */
    PACKAGING_BOX("Packaging Box", "包装盒"),

    /**
     * 狗牌类 - 哑光静音系列
     */
    DOG_TAG_NYLON_MATTE_SILENT("Dog Tag Nylon Matte Silent", "尼龙哑光静音狗牌"),
    DOG_TAG_SILICONE_MATTE_SILENT("Dog Tag Silicone Matte Silent", "硅胶哑光静音狗牌"),
    DOG_TAG_SILICONE_GLOSSY_SILENT("Dog Tag Silicone Glossy Silent", "硅胶亮光静音狗牌"),
    DOG_TAG_OPEN_RECTANGLE_SILENT("Dog Tag Open Rectangle Silent", "开口长方形静音狗牌"),
    DOG_TAG_OPEN_BONE_SILENT("Dog Tag Open Bone Silent", "开口骨头形静音狗牌"),
    DOG_TAG_HOLLOW_CLAW_BONE_SILENT("Dog Tag Hollow Claw Bone Silent", "镂空爪子骨头形狗牌"),

    /**
     * 狗牌类 - 其他系列
     */
    DOG_TAG_ROUND("Dog Tag Round", "圆形狗牌"),
    DOG_TAG_STAR("Dog Tag Star", "星星狗牌"),
    DOG_TAG_MOON("Dog Tag Moon", "月亮狗牌"),
    DOG_TAG_CAT_EAR("Dog Tag Cat Ear", "猫耳狗牌"),
    DOG_TAG_HEART("Dog Tag Heart", "心形狗牌"),

    /**
     * 宠物用品类
     */
    HAIR_COLLECTOR("Hair Collector", "毛发收集器"),

    /**
     * 硅胶绑带
     */
    SILICONE_BAND("Silicone Band", "硅胶绑带"),

    /**
     * 骨灰罐
     */
    ASH_CAN("Ash Can", "骨灰罐"),

    /**
     * 未知产品名称
     */
    UNKNOWN("", "未知产品");

    private final String nameCode;
    private final String displayName;

    ProductName(String nameCode, String displayName) {
        this.nameCode = nameCode;
        this.displayName = displayName;
    }

    public String getNameCode() {
        return nameCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 根据显示名称获取枚举值
     * @param nameCode 显示名称
     * @return 对应的枚举值，如果未找到返回UNKNOWN
     */
    public static ProductName fromNameCode(String nameCode) {
        if (nameCode == null || nameCode.trim().isEmpty()) {
            return UNKNOWN;
        }

        for (ProductName name : ProductName.values()) {
            if (name.nameCode.equals(nameCode) ||
                name.name().equalsIgnoreCase(nameCode) ||
                name.name().replace("_", " ").equalsIgnoreCase(nameCode)) {
                return name;
            }
        }

        // 尝试模糊匹配
        String lowerNameCode = nameCode.toLowerCase().replace(" ", "");
        for (ProductName name : ProductName.values()) {
            if (name.nameCode.toLowerCase().replace(" ", "").equals(lowerNameCode)) {
                return name;
            }
        }

        return UNKNOWN;
    }
    @Override
    public String toString() {
        return displayName;
    }
}
