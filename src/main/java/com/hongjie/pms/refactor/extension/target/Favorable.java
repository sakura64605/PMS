package com.hongjie.pms.refactor.extension.target;

/**
 * 可被收藏的目标。
 *
 * <p>当前收藏只涉及「有关系记录」本身，不需要维护计数字段，因此除基础能力外无额外方法。
 * 保留这个空接口的意义在于：注册表可以用它做能力校验 ——
 * {@code registry.get(type, Favorable.class)} 会在目标不支持收藏时立刻失败，
 * 而不是让调用方以为收藏成功了。
 *
 * <p>将来若要加收藏数统计，方法加在这里即可，所有实现类会在编译期被强制补上。
 */
public interface Favorable extends TargetHandler {
}
