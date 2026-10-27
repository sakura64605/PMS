package com.hongjie.pms.modules.content.petpost.application;

import com.hongjie.pms.modules.content.petpost.application.classification.PetCategories;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PetCategoryMapper;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PetCategory;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.platform.exception.BusinessException;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetCategoriesTest {
    final PetCategoryMapper mapper = mock(PetCategoryMapper.class);
    final PetCategories categories = new PetCategories(mapper);

    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    @Test
    void nonAdminCannotReadOrWriteDictionary() {
        UserContext.setUser(new UserInfo(1L, "reader", 0, null, null));
        assertThatThrownBy(categories::list).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> categories.create(new PetCategory()))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void createsNextCategoryWithExistingDefaults() {
        UserContext.setUser(new UserInfo(1L, "admin", 1, null, null));
        when(mapper.selectObjs(any())).thenReturn(List.of(8));
        var request = new PetCategory();
        request.setName("  custom  ");
        var saved = categories.create(request);
        assertThat(saved.getId()).isEqualTo(8);
        assertThat(saved.getName()).isEqualTo("custom");
        assertThat(saved.getSort()).isZero();
        assertThat(saved.getStatus()).isEqualTo(1);
        verify(mapper).insert(saved);
    }
}
