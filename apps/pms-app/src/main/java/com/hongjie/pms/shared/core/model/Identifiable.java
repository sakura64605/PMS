package com.hongjie.pms.shared.core.model;

import java.io.Serializable;

/**
 * 可标识实体契约 —— 泛型算法的下界。
 *
 * <p>老实体（未继承 {@link BaseEntity}）天然满足此契约（都已有 {@code getId()}），
 * 因此无需改动即可参与 {@code AbstractBaseService}、转换器、事件发布等泛型抽象。
 * 这是「薄接口当下界、厚抽象类当复用载体」折中的一半，另一半见 {@link BaseEntity}。
 */
public interface Identifiable extends Serializable {

    Long getId();
}
