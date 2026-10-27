package com.hongjie.pms.shared.core.strategy;

import com.hongjie.pms.platform.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StrategyRegistry 通用策略注册表")
class StrategyRegistryTest {

    interface Animal {
        Set<String> keys();
    }

    /** 能同时服务两个键的策略 */
    record MultiKeyAnimal(Set<String> keys, String label) implements Animal {}

    /** 能力接口必须自身也是 Animal，才符合 {@code get(key, Class<? extends S>)} 的约束 */
    interface Swimmable extends Animal {}

    record Duck(Set<String> keys) implements Animal, Swimmable {}

    record Cat(Set<String> keys) implements Animal {}

    private static StrategyRegistry<String, Animal> registryOf(Animal... animals) {
        return new StrategyRegistry<>("动物", List.of(animals), Animal::keys);
    }

    @Test
    @DisplayName("一个策略可注册多个键")
    void oneStrategyMayServeMultipleKeys() {
        StrategyRegistry<String, Animal> registry =
                registryOf(new MultiKeyAnimal(Set.of("duck", "goose"), "水禽"));

        assertThat(registry.get("duck")).isSameAs(registry.get("goose"));
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("重复键在构造期快速失败 —— 启动即暴露，而非线上某分支才炸")
    void duplicateKeyFailsFast() {
        assertThatThrownBy(() -> registryOf(new Cat(Set.of("same")), new Duck(Set.of("same"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("策略键重复")
                .hasMessageContaining("same");
    }

    @Test
    @DisplayName("get 未注册键抛业务异常，find 返回空")
    void missingKeyBehaviors() {
        StrategyRegistry<String, Animal> registry = registryOf(new Cat(Set.of("cat")));

        assertThat(registry.find("nope")).isEmpty();
        assertThatThrownBy(() -> registry.get("nope"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未注册的 动物 类型");
    }

    @Test
    @DisplayName("null 键返回空而非 NPE")
    void nullKeyIsSafe() {
        StrategyRegistry<String, Animal> registry = registryOf(new Cat(Set.of("cat")));
        assertThat(registry.find(null)).isEmpty();
    }

    @Test
    @DisplayName("能力校验：不支持该能力时立刻失败，而不是留到调用点 ClassCastException")
    void capabilityCheckRejectsUnsupported() {
        StrategyRegistry<String, Animal> registry =
                registryOf(new Duck(Set.of("duck")), new Cat(Set.of("cat")));

        assertThat(registry.get("duck", Swimmable.class)).isInstanceOf(Duck.class);
        assertThatThrownBy(() -> registry.get("cat", Swimmable.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持 Swimmable 能力");
    }

    @Test
    @DisplayName("find 带能力过滤时静默返回空")
    void findWithCapabilityFiltersQuietly() {
        StrategyRegistry<String, Animal> registry = registryOf(new Cat(Set.of("cat")));

        assertThat(registry.<Animal>find("cat", Swimmable.class)).isEmpty();
        assertThat(registry.find("cat", Animal.class)).isPresent();
    }

    @Test
    @DisplayName("all 去重且保持注册顺序 —— 保证按固定顺序写日志与断言")
    void allIsDeduplicatedAndOrdered() {
        MultiKeyAnimal shared = new MultiKeyAnimal(Set.of("a", "b"), "shared");
        StrategyRegistry<String, Animal> registry = registryOf(shared, new Cat(Set.of("c")));

        Collection<Animal> all = registry.all();

        assertThat(all).hasSize(2);
        assertThat(all).first().isSameAs(shared);
    }

    @Test
    @DisplayName("空策略集合可正常构造，查任何键都返回未注册")
    void emptyRegistryIsUsable() {
        StrategyRegistry<String, Animal> registry =
                new StrategyRegistry<>("空", List.of(), Animal::keys);

        assertThat(registry.size()).isZero();
        assertThat(registry.keys()).isEmpty();
        assertThat(registry.contains("x")).isFalse();
        assertThat(registry.find("x")).isEqualTo(Optional.empty());
    }
}
