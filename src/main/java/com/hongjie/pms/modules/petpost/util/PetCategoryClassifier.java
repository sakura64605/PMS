package com.hongjie.pms.modules.petpost.util;

import java.util.Set;

/**
 * 宠物物种分类器：把自由文本的品种名（pet_type）归到粗粒度物种桶。
 *
 * <p>用途二选一：
 * 1) 发布/编辑时若前端未显式传 petCategory，用它兜底推导（理想情况应由用户在发布表单里显式选择物种）；
 * 2) 存量数据一次性回填。
 *
 * <p>枚举：0=猫 1=狗 2=兔 3=啮齿类 4=鸟类 5=鱼类 6=爬行/两栖 7=其他
 * 注意：顶层物种是有界的，品种才是无界的——本类只负责把常见品种对号入座，识别不了的归"其他"，
 * 永远不试图穷举品种。
 */
public final class PetCategoryClassifier {

    public static final int CAT = 0;
    public static final int DOG = 1;
    public static final int RABBIT = 2;
    public static final int RODENT = 3;
    public static final int BIRD = 4;
    public static final int FISH = 5;
    public static final int REPTILE = 6;
    public static final int OTHER = 7;

    private static final Set<String> DOG_BREEDS = Set.of(
            "柯基", "金毛", "泰迪", "比熊", "哈士奇", "萨摩耶", "边牧", "边境牧羊犬", "法斗", "法国斗牛",
            "巴哥", "柴犬", "吉娃娃", "拉布拉多", "拉多", "德牧", "德国牧羊", "博美", "贵宾", "雪纳瑞",
            "杜宾", "罗威纳", "藏獒", "约克夏", "蝴蝶犬", "腊肠", "沙皮", "松狮", "小鹿犬", "鹿犬",
            "田园犬", "土狗", "中华田园犬", "流浪犬", "史宾格", "金毛巡回", "金边", "银狐", "柯基犬",
            "比格", "灵缇", "惠比特", "阿富汗猎犬", "大丹", "圣伯纳", "拳师", "秋田", "阿拉斯加",
            "纽芬兰", "伯恩山", "寻回", "梗犬", "法老王猎犬", "牛头梗", "贝灵顿", "凯利蓝", "澳牧",
            "古牧", "古代牧羊", "苏格兰牧羊", "喜乐蒂", "波尔多", "卡斯罗", "比特", "中华田园", "四眼"
    );

    private static final Set<String> CAT_BREEDS = Set.of(
            "橘猫", "布偶", "英短", "英国短毛", "美短", "美国短毛", "暹罗", "金吉拉", "蓝白", "蓝猫",
            "银渐层", "孟买", "波斯", "无毛", "卷耳", "狸花", "加菲", "异国短毛", "缅因", "临清",
            "奶牛猫", "三花", "橘白", "渐层", "折耳", "豹猫", "缅因猫", "德文", "阿比", "索马里",
            "中华田园猫", "流浪猫", "白猫", "黑猫", "虎斑", "起司", "狮子猫"
    );

    private static final Set<String> RABBIT_KEYWORDS = Set.of("兔", "垂耳", "侏儒", "安哥拉", "狮子兔", "道奇兔");
    private static final Set<String> RODENT_KEYWORDS = Set.of("仓鼠", "豚鼠", "龙猫", "松鼠", "荷兰猪", "花枝鼠", "金丝熊", "布丁鼠", "三线鼠", "熊鼠");
    private static final Set<String> BIRD_KEYWORDS = Set.of("鹦鹉", "玄凤", "虎皮", "文鸟", "画眉", "牡丹", "和尚", "八哥", "百灵", "金丝雀", "鸟");
    private static final Set<String> FISH_KEYWORDS = Set.of("金鱼", "斗鱼", "孔雀鱼", "热带鱼", "锦鲤", "灯鱼", "鼠鱼", "异型鱼", "罗汉鱼", "地图鱼", "鱼");
    private static final Set<String> REPTILE_KEYWORDS = Set.of("龟", "蜥蜴", "蛇", "守宫", "蛙", "鬃狮", "壁虎", "角蛙", "陆龟", "水龟", "猪鼻", "玉米蛇", "变色龙");

    private PetCategoryClassifier() {
    }

    /**
     * 将品种自由文本归为物种桶。
     *
     * @param petType 用户填的品种文本，可能为空或乱填
     * @return 物种桶常量 0-7
     */
    public static int classify(String petType) {
        if (petType == null) {
            return OTHER;
        }
        String t = petType.trim().toLowerCase();
        if (t.isEmpty() || t.length() > 20) {
            return OTHER;
        }

        // 犬/猫同时出现（如"流浪猫狗""犬猫"）视为含糊 → 其他
        boolean hasCatWord = t.contains("猫") || t.contains("喵");
        boolean hasDogWord = t.contains("犬") || t.contains("狗") || t.contains("汪");
        if (hasCatWord && hasDogWord) {
            return OTHER;
        }

        // 狗：含犬/狗字，或命中狗品种
        if (hasDogWord || containsAny(t, DOG_BREEDS)) {
            return DOG;
        }
        // 猫：含猫字，或命中猫品种
        if (hasCatWord || containsAny(t, CAT_BREEDS)) {
            return CAT;
        }
        if (containsAny(t, RABBIT_KEYWORDS)) return RABBIT;
        if (containsAny(t, RODENT_KEYWORDS)) return RODENT;
        if (containsAny(t, BIRD_KEYWORDS)) return BIRD;
        if (containsAny(t, FISH_KEYWORDS)) return FISH;
        if (containsAny(t, REPTILE_KEYWORDS)) return REPTILE;

        return OTHER;
    }

    private static boolean containsAny(String text, Set<String> tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
