package com.hongjie.pms.modules.content.petpost.application.converter;

import com.hongjie.pms.modules.content.petpost.api.dto.PetDetailDto;
import com.hongjie.pms.modules.content.petpost.api.dto.response.PetListResponseDto;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PetPost;
import com.hongjie.pms.modules.identity.user.api.dto.UserSimpleDto;
import com.hongjie.pms.shared.core.enums.biz.AuditStatus;
import com.hongjie.pms.shared.core.enums.biz.PetPostStatus;
import com.hongjie.pms.shared.core.enums.biz.PetPostType;
import com.hongjie.pms.modules.content.petpost.api.dto.PetPostWithAuthor;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PmsPetPost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定新模型到对外 DTO 的字段映射。
 *
 * <p>这是接口契约最关键的一环：实体侧把 {@code petType} 改名成了 {@code petBreed}、
 * {@code petAge} 改名成了 {@code petAgeText}，如果这里的映射写错，
 * 前端拿到的字段会<b>静默变空</b> —— 编译通过、接口 200、不报任何错。
 */
@DisplayName("PetPostDtoConverter 字段映射")
class PetPostDtoConverterTest {

    private final PetPostDtoConverter converter = new PetPostDtoConverter();

    private static final UserSimpleDto AUTHOR =
            UserSimpleDto.builder()
                    .userId(7L)
                    .username("zhangsan")
                    .nickname("张三")
                    .avatar("https://cdn/avatar.png")
                    .build();

    private static PmsPetPost samplePost() {
        PmsPetPost post = new PmsPetPost();
        post.setId(100L);
        post.setUserId(7L);
        post.setType(PetPostType.ADOPT.getCode());
        post.setTitle("橘猫找家");
        post.setContent("三个月大的橘猫，亲人");
        post.setImages(List.of("a.png", "b.png"));
        post.setPetName("大橘");
        post.setPetGender(1);
        post.setPetAgeText("3个月");
        post.setPetBreed("橘猫");
        post.setPetCategory(0);
        post.setAddress("杭州市西湖区");
        post.setContactPhone("13800000000");
        post.setContactWechat("wx_test");
        post.setStatus(PetPostStatus.PUBLISHED.getCode());
        post.setAuditStatus(AuditStatus.APPROVED.getCode());
        post.setViewCount(120);
        post.setLikeCount(9);
        post.setCommentCount(4);
        post.setShareCount(2);
        post.setCreateTime(LocalDateTime.of(2026, 10, 1, 10, 0));
        post.setUpdateTime(LocalDateTime.of(2026, 10, 2, 11, 30));
        return post;
    }

    @Test
    void publicCardPreviewHasLocationAndBoundedDescription() {
        var post = samplePost();
        post.setContent("长".repeat(300));
        var dto = converter.toView(new PetPostWithAuthor(post, AUTHOR, false, false));
        assertThat(dto.getContent()).hasSize(200);
        assertThat(dto.getAddress()).isEqualTo("杭州市西湖区");
        post.setContent(null);
        assertThat(converter.toView(new PetPostWithAuthor(post, AUTHOR, false, false)).getContent())
                .isNull();
    }

    @Nested
    @DisplayName("列表视图")
    class ListView {

        @Test
        @DisplayName("改过名的字段正确映射回对外字段")
        void renamedFieldsMapBack() {
            PetListResponseDto dto =
                    converter.toView(new PetPostWithAuthor(samplePost(), AUTHOR, true, false));

            assertThat(dto.getPetType())
                    .as("实体侧 petBreed 必须映射回 DTO 的 petType，否则前端品种显示为空")
                    .isEqualTo("橘猫");
            assertThat(dto.getPetAge()).as("实体侧 petAgeText 必须映射回 DTO 的 petAge").isEqualTo("3个月");
        }

        @Test
        @DisplayName("补齐老实现漏设的 petCategory 与 updateTime")
        void fillsFieldsOldImplOmitted() {
            PetListResponseDto dto =
                    converter.toView(new PetPostWithAuthor(samplePost(), AUTHOR, true, false));

            assertThat(dto.getPetCategory()).as("老实现没设置这个字段，列表里恒为 null").isEqualTo(0);
            assertThat(dto.getUpdateTime())
                    .as("老实现同样漏了 updateTime")
                    .isEqualTo(LocalDateTime.of(2026, 10, 2, 11, 30));
        }

        @Test
        @DisplayName("其余字段逐项映射")
        void mapsRemainingFields() {
            PetListResponseDto dto =
                    converter.toView(new PetPostWithAuthor(samplePost(), AUTHOR, true, false));

            assertThat(dto.getId()).isEqualTo(100L);
            assertThat(dto.getType()).isEqualTo(PetPostType.ADOPT.getCode());
            assertThat(dto.getTitle()).isEqualTo("橘猫找家");
            assertThat(dto.getPetName()).isEqualTo("大橘");
            assertThat(dto.getPetGender()).isEqualTo(1);
            assertThat(dto.getImages()).containsExactly("a.png", "b.png");
            assertThat(dto.getViewCount()).isEqualTo(120);
            assertThat(dto.getLikeCount()).isEqualTo(9);
            assertThat(dto.getCommentCount()).isEqualTo(4);
            assertThat(dto.getShareCount()).isEqualTo(2);
            assertThat(dto.getStatus()).isEqualTo(PetPostStatus.PUBLISHED.getCode());
            assertThat(dto.getAuditStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
            assertThat(dto.getUser()).isSameAs(AUTHOR);
        }

        @Test
        @DisplayName("作者缺失时正常转换")
        void toleratesMissingAuthor() {
            PetListResponseDto dto =
                    converter.toView(new PetPostWithAuthor(samplePost(), null, false, false));

            assertThat(dto.getUser()).isNull();
            assertThat(dto.getTitle()).isEqualTo("橘猫找家");
        }
    }

    @Nested
    @DisplayName("详情视图")
    class DetailView {

        @Test
        @DisplayName("比列表多出正文与联系方式，并带上当前用户的互动态")
        void mapsDetailOnlyFields() {
            PetDetailDto dto =
                    converter.toDetail(new PetPostWithAuthor(samplePost(), AUTHOR, true, false));

            assertThat(dto.getContent()).isEqualTo("三个月大的橘猫，亲人");
            assertThat(dto.getContactPhone()).isEqualTo("13800000000");
            assertThat(dto.getContactWechat()).isEqualTo("wx_test");
            assertThat(dto.getAddress()).isEqualTo("杭州市西湖区");
            assertThat(dto.getIsFavorite()).isTrue();
            assertThat(dto.getIsLiked()).isFalse();
        }

        @Test
        @DisplayName("互动态来自入参，不由转换器自行查询 —— 保证它是纯函数")
        void viewerStateComesFromInput() {
            PetDetailDto liked =
                    converter.toDetail(new PetPostWithAuthor(samplePost(), AUTHOR, false, true));

            assertThat(liked.getIsLiked()).isTrue();
            assertThat(liked.getIsFavorite()).isFalse();
        }
    }

    @Nested
    @DisplayName("迁移期适配：投影回老实体")
    class LegacyProjection {

        @Test
        @DisplayName("投影出完整的老实体，供 ES 同步事件继续使用")
        void projectsAllFields() {
            PetPost legacy = converter.toLegacyEntity(samplePost());

            assertThat(legacy.getId()).isEqualTo(100L);
            assertThat(legacy.getUserId()).isEqualTo(7L);
            assertThat(legacy.getTitle()).isEqualTo("橘猫找家");
            assertThat(legacy.getContent()).isEqualTo("三个月大的橘猫，亲人");
            assertThat(legacy.getPetType()).isEqualTo("橘猫");
            assertThat(legacy.getPetAge()).isEqualTo("3个月");
            assertThat(legacy.getPetCategory()).isEqualTo(0);
            assertThat(legacy.getStatus()).isEqualTo(PetPostStatus.PUBLISHED.getCode());
            assertThat(legacy.getAuditStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
            assertThat(legacy.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 0));
        }

        @Test
        @DisplayName("投影不丢图片列表，否则 ES 索引里没有封面")
        void keepsImages() {
            assertThat(converter.toLegacyEntity(samplePost()).getImages())
                    .containsExactly("a.png", "b.png");
        }
    }
}
