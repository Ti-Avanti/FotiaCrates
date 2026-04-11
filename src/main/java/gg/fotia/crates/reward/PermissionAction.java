package gg.fotia.crates.reward;

/**
 * 权限检测匹配时的行为
 */
public enum PermissionAction {
    /**
     * 跳过该奖励，重新抽取
     */
    SKIP,

    /**
     * 给予替代奖励
     */
    ALTERNATIVE
}
