package com.hongjie.pms.refactor.petpost.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.refactor.petpost.entity.PmsPetPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 宠物帖子 Mapper。
 *
 * <p><b>当前指向老表 {@code pet_post}</b>（本轮先重写实现、不换物理表）。
 * 切表时把下面每条 SQL 的表名换成 {@code pms_pet_post} 并补上
 * {@code AND deleted = 0} 即可 —— 这是切表改动中最机械的一部分。
 *
 * <p>计数类操作用<b>原子自增 SQL</b>，而不是「查出来 +1 + 存回去」：
 * 后者在并发下会丢更新。这也意味着这些 UPDATE 不参与乐观锁版本校验 ——
 * 这是有意的，{@code version} 保护的是「读-改-写」式的业务状态变更，
 * 而不是单调累加的计数器。
 *
 * <p>注意这里<b>没有</b>像老实现那样在 SQL 里忽略删除态：老表的删除是
 * {@code status = -1}，而计数只对存在的帖子有意义，加上这个条件可以避免
 * 给已删除的帖子累加计数。
 */
@Mapper
public interface PmsPetPostMapper extends BaseMapper<PmsPetPost> {

    @Update("UPDATE pet_post SET like_count = like_count + 1 WHERE id = #{id} AND status <> -1")
    void incrementLikeCount(@Param("id") Long id);

    @Update("UPDATE pet_post SET like_count = like_count - 1 "
            + "WHERE id = #{id} AND like_count > 0 AND status <> -1")
    void decrementLikeCount(@Param("id") Long id);

    @Update("UPDATE pet_post SET comment_count = comment_count + 1 WHERE id = #{id} AND status <> -1")
    void incrementCommentCount(@Param("id") Long id);

    @Update("UPDATE pet_post SET comment_count = comment_count - 1 "
            + "WHERE id = #{id} AND comment_count > 0 AND status <> -1")
    void decrementCommentCount(@Param("id") Long id);

    @Update("UPDATE pet_post SET view_count = view_count + 1 WHERE id = #{id} AND status <> -1")
    void incrementViewCount(@Param("id") Long id);
}
