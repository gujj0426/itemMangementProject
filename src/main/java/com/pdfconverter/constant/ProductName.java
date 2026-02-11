package com.pdfconverter.constant;

/**
 * 产品名称枚举
 * 定义所有支持的产品名称
 */
public enum ProductName {
    /**
     * 袖扣类
     */
    CUFFLINK_OLD("旧款袖扣"),
    CUFFLINK_SINGLE_POLISHED_TRANSITION("单抛袖扣-过渡"),
    CUFFLINK_DOUBLE_POLISHED("双抛袖扣"),

    /**
     * 领带夹类
     */
    TIE_CLIP_DUCK_BILL_THIN("鸭嘴领带夹（薄）"),
    TIE_CLIP_DUCK_BILL_THICK("鸭嘴领带夹（厚）"),
    TIE_CLIP_DOUBLE_SIDED_SLIDE_IN("双面滑入式领带夹"),
    TIE_CLIP_ROUND_DUCK_BILL("圆片鸭嘴领带夹"),

    /**
     * 相盒吊坠类
     */
    FLOWER_HEART_BOX_PENDANT("花卉心形相盒吊坠"),
    PATTERN_HEART_BOX_PENDANT("花纹心形相盒吊坠"),
    BIRTHSTONE_RAW("生日石原石"),
    CATS_EYE_STONE_PENDANT("猫眼石吊坠"),
    ROUND_PENDANT("圆片吊坠"),
    TANGCA_PHOTO_BOX_PENDANT("唐卡相合吊坠"),

    /**
     * 胸针类
     */
    BOWKNOT_BROOCH("蝴蝶结胸针"),

    /**
     * 配件类
     */
    BIRTHSTONE_ADDON("生日石 add-on"),
    WINGS_ADDON("翅膀 add-on"),
    STAR_ACCESSORY("星星配件"),
    SMALL_FISH_ACCESSORY("小鱼配件"),

    /**
     * 链饰类
     */
    BASE_CHAIN("基础链"),
    DOOR_CLASP("门字扣"),
    KEY_RING("钥匙环"),
    ROTATING_SCREW_WIRE_LOOP("旋转螺丝钢丝环"),

    /**
     * 宠物玩具类
     */
    BIG_BROTHER_BIG_COMMA_CAT_WAND("大哥大逗猫棒"),
    COMMA_CAT_WAND_LETTER_ACCESSORY("逗猫棒字母配饰"),
    MICROPHONE_COMMA_CAT_WAND("麦克风逗猫棒"),
    BIG_THUMB_BROTHER_LOSS_BALL_TOY("大拇哥丢球玩具"),
    PEAR_SHAPED_POOP_BAG("梨形拾便袋"),
    PET_MUZZLE("宠物围嘴"),

    /**
     * 包装类
     */
    PACKAGING_BOX("包装盒"),

    /**
     * 狗牌类 - 哑光静音系列
     */
    DOG_TAG_NYLON_MATTE_SILENT("尼龙哑光静音狗牌"),
    DOG_TAG_SILICONE_MATTE_SILENT("硅胶哑光静音狗牌"),
    DOG_TAG_SILICONE_GLOSSY_SILENT("硅胶亮光静音狗牌"),
    DOG_TAG_OPEN_RECTANGLE_SILENT("开口长方形静音狗牌"),
    DOG_TAG_OPEN_BONE_SILENT("开口骨头形静音狗牌"),
    DOG_TAG_HOLLOW_CLAW_BONE_SILENT("镂空爪子骨头形狗牌"),

    /**
     * 狗牌类 - 其他系列
     */
    DOG_TAG_ROUND("圆形狗牌"),
    DOG_TAG_STAR("星星狗牌"),
    DOG_TAG_MOON("月亮狗牌"),
    DOG_TAG_CAT_EAR("猫耳狗牌"),
    DOG_TAG_HEART("心形狗牌"),

    /**
     * 宠物用品类
     */
    HAIR_COLLECTOR("毛发收集器"),

    /**
     * 硅胶绑带
     */
    SILICONE_BAND("硅胶绑带"),

    /**
     * 骨灰罐
     */
    ASH_CAN("骨灰罐"),

    /**
     * 未知产品名称
     */
    UNKNOWN("");

    private final String displayName;

    ProductName(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 根据显示名称获取枚举值
     * @param displayName 显示名称
     * @return 对应的枚举值，如果未找到返回UNKNOWN
     */
    public static ProductName fromDisplayName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return UNKNOWN;
        }

        for (ProductName name : ProductName.values()) {
            if (name.displayName.equals(displayName) ||
                name.name().equalsIgnoreCase(displayName) ||
                name.name().replace("_", " ").equalsIgnoreCase(displayName)) {
                return name;
            }
        }

        // 尝试模糊匹配
        String lowerDisplayName = displayName.toLowerCase();
        for (ProductName name : ProductName.values()) {
            if (name.displayName.toLowerCase().equals(lowerDisplayName)) {
                return name;
            }
        }

        return UNKNOWN;
    }

    /**
     * 根据商品标题获取产品名称
     * @param title 商品标题
     * @return 对应的产品名称
     */
    public static ProductName fromTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return UNKNOWN;
        }

        String lowerTitle = title.toLowerCase();

        // 袖扣类
        if (lowerTitle.contains("cufflink") || lowerTitle.contains("cufflinks")) {
            if (lowerTitle.contains("单抛") && lowerTitle.contains("过渡")) {
                return CUFFLINK_SINGLE_POLISHED_TRANSITION;
            } else if (lowerTitle.contains("双抛")) {
                return CUFFLINK_DOUBLE_POLISHED;
            } else {
                return CUFFLINK_OLD;
            }
        }

        // 领带夹类
        if (lowerTitle.contains("tie clip") || lowerTitle.contains("领带夹")) {
            if (lowerTitle.contains("鸭嘴") && lowerTitle.contains("薄")) {
                return TIE_CLIP_DUCK_BILL_THIN;
            } else if (lowerTitle.contains("鸭嘴") && lowerTitle.contains("厚")) {
                return TIE_CLIP_DUCK_BILL_THICK;
            } else if (lowerTitle.contains("双面") && lowerTitle.contains("滑入")) {
                return TIE_CLIP_DOUBLE_SIDED_SLIDE_IN;
            } else if (lowerTitle.contains("圆片") && lowerTitle.contains("鸭嘴")) {
                return TIE_CLIP_ROUND_DUCK_BILL;
            }
        }

        // 相盒吊坠类
        if (lowerTitle.contains("birth flower") || lowerTitle.contains("flower heart")) {
            return FLOWER_HEART_BOX_PENDANT;
        } else if (lowerTitle.contains("花纹") && lowerTitle.contains("心形")) {
            return PATTERN_HEART_BOX_PENDANT;
        } else if (lowerTitle.contains("生日石")) {
            if (lowerTitle.contains("原石")) {
                return BIRTHSTONE_RAW;
            } else if (lowerTitle.contains("add-on")) {
                return BIRTHSTONE_ADDON;
            }
        } else if (lowerTitle.contains("猫眼石")) {
            return CATS_EYE_STONE_PENDANT;
        } else if (lowerTitle.contains("圆片") && lowerTitle.contains("吊坠")) {
            return ROUND_PENDANT;
        } else if (lowerTitle.contains("唐卡")) {
            return TANGCA_PHOTO_BOX_PENDANT;
        }

        // 胸针类
        if (lowerTitle.contains("蝴蝶结") && lowerTitle.contains("胸针")) {
            return BOWKNOT_BROOCH;
        }

        // 配件类
        if (lowerTitle.contains("翅膀") && lowerTitle.contains("add-on")) {
            return WINGS_ADDON;
        } else if (lowerTitle.contains("星星") && lowerTitle.contains("配件")) {
            return STAR_ACCESSORY;
        } else if (lowerTitle.contains("小鱼") && lowerTitle.contains("配件")) {
            return SMALL_FISH_ACCESSORY;
        }

        // 链饰类
        if (lowerTitle.contains("基础链") || lowerTitle.contains("base chain")) {
            return BASE_CHAIN;
        } else if (lowerTitle.contains("门字扣")) {
            return DOOR_CLASP;
        } else if (lowerTitle.contains("钥匙环")) {
            return KEY_RING;
        } else if (lowerTitle.contains("旋转螺丝") || lowerTitle.contains("钢丝环")) {
            return ROTATING_SCREW_WIRE_LOOP;
        }

        // 宠物玩具类
        if (lowerTitle.contains("逗猫棒")) {
            if (lowerTitle.contains("大哥大") || lowerTitle.contains("big brother big")) {
                return BIG_BROTHER_BIG_COMMA_CAT_WAND;
            } else if (lowerTitle.contains("字母配饰")) {
                return COMMA_CAT_WAND_LETTER_ACCESSORY;
            } else if (lowerTitle.contains("麦克风")) {
                return MICROPHONE_COMMA_CAT_WAND;
            }
        } else if (lowerTitle.contains("大拇哥丢球")) {
            return BIG_THUMB_BROTHER_LOSS_BALL_TOY;
        } else if (lowerTitle.contains("梨形") && lowerTitle.contains("拾便袋")) {
            return PEAR_SHAPED_POOP_BAG;
        } else if (lowerTitle.contains("宠物围嘴") || lowerTitle.contains("pet muzzle")) {
            return PET_MUZZLE;
        }

        // 包装类
        if (lowerTitle.contains("包装盒") || lowerTitle.contains("packaging box") || lowerTitle.contains("box")) {
            return PACKAGING_BOX;
        }

        // 狗牌类
        if (lowerTitle.contains("狗牌") || lowerTitle.contains("dog tag")) {
            // 哑光静音系列
            if (lowerTitle.contains("尼龙") && lowerTitle.contains("哑光") && lowerTitle.contains("静音")) {
                return DOG_TAG_NYLON_MATTE_SILENT;
            } else if (lowerTitle.contains("硅胶") && lowerTitle.contains("哑光") && lowerTitle.contains("静音")) {
                return DOG_TAG_SILICONE_MATTE_SILENT;
            } else if (lowerTitle.contains("硅胶") && lowerTitle.contains("亮光") && lowerTitle.contains("静音")) {
                return DOG_TAG_SILICONE_GLOSSY_SILENT;
            } else if (lowerTitle.contains("开口") && lowerTitle.contains("长方形") && lowerTitle.contains("静音")) {
                return DOG_TAG_OPEN_RECTANGLE_SILENT;
            } else if (lowerTitle.contains("开口") && lowerTitle.contains("骨头") && lowerTitle.contains("静音")) {
                return DOG_TAG_OPEN_BONE_SILENT;
            } else if (lowerTitle.contains("镂空") && lowerTitle.contains("爪子") && lowerTitle.contains("骨头")) {
                return DOG_TAG_HOLLOW_CLAW_BONE_SILENT;
            }
            // 其他狗牌
            else if (lowerTitle.contains("圆形")) {
                return DOG_TAG_ROUND;
            } else if (lowerTitle.contains("星星")) {
                return DOG_TAG_STAR;
            } else if (lowerTitle.contains("月亮")) {
                return DOG_TAG_MOON;
            } else if (lowerTitle.contains("猫耳")) {
                return DOG_TAG_CAT_EAR;
            } else if (lowerTitle.contains("心形")) {
                return DOG_TAG_HEART;
            }
        }

        // 硅胶绑带
        if (lowerTitle.contains("硅胶绑带") || lowerTitle.contains("silicone band") ||
            lowerTitle.contains("dog tag holder")) {
            return SILICONE_BAND;
        }

        // 宠物用品
        if (lowerTitle.contains("毛发收集器") || lowerTitle.contains("hair collector")) {
            return HAIR_COLLECTOR;
        }

        // 骨灰罐
        if (lowerTitle.contains("骨灰罐")) {
            return ASH_CAN;
        }

        return UNKNOWN;
    }

    /**
     * 根据订单类型获取默认产品名称
     * @param orderType 订单类型
     * @return 对应的默认产品名称
     */
    public static ProductName fromOrderType(OrderType orderType) {
        if (orderType == null) {
            return UNKNOWN;
        }

        switch (orderType) {
            case CUFFLINK_OLD:
                return CUFFLINK_OLD;
            case TIE_CLIP:
                return TIE_CLIP_DUCK_BILL_THIN;
            case BOX:
                return PACKAGING_BOX;
            case DOG_TAG:
                return DOG_TAG_NYLON_MATTE_SILENT;
            case DOG_TAG_HOLDER:
                return SILICONE_BAND;
            case FLOWER_HEART_BOX:
                return FLOWER_HEART_BOX_PENDANT;
            case BASE_CHAIN:
                return BASE_CHAIN;
            default:
                return UNKNOWN;
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
