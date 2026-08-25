package com.hongjie.pms.modules.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.daily.entity.DailyPost;
import com.hongjie.pms.modules.daily.entity.Topic;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.daily.mapper.DailyTopicRelMapper;
import com.hongjie.pms.modules.daily.mapper.TopicMapper;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.search.document.UnifiedDoc;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchDataSyncService {

    private final ElasticsearchClient esClient;
    private final UserMapper userMapper;
    private final TopicMapper topicMapper;
    private final DailyTopicRelMapper dailyTopicRelMapper;
    private final PetPostMapper petPostMapper;
    private final ActivityMapper activityMapper;
    private final DailyPostMapper dailyPostMapper;

    private static final String INDEX_NAME = "unified_search";

    /**
     * 同步日记到ES
     */
    public void syncDailyPost(DailyPost post, List<Long> topicIds) {
        if (post == null || post.getStatus() == 0) {
            deleteById("daily_" + post.getId());
            return;
        }

        try {
            User user = userMapper.selectById(post.getUserId());
            List<String> topicNames = getTopicNames(topicIds);

            UnifiedDoc doc = UnifiedDoc.builder()
                    .id("daily_" + post.getId())
                    .type("daily")
                    .businessId(post.getId())
                    .title(extractTitleFromContent(post.getContent()))
                    .content(post.getContent())
                    .topics(topicNames)
                    .location(post.getLocation())
                    .images(post.getImages())
                    .userId(post.getUserId())
                    .userName(user != null ? user.getUserName() : "未知用户")
                    .userAvatar(user != null ? user.getAvatar() : null)
                    .likeCount(post.getLikeCount())
                    .commentCount(post.getCommentCount())
                    .viewCount(post.getViewCount())
                    .status(post.getStatus())
                    .auditStatus(post.getAuditStatus())
                    .createTime(post.getCreateTime())
                    .updateTime(post.getCreateTime())
                    .videoUrl(post.getVideoUrl())
                    .build();

            esClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(doc.getId())
                    .document(doc)
            );
            log.info("同步日记到ES成功: id={}", post.getId());
        } catch (Exception e) {
            log.error("同步日记到ES失败: id={}", post.getId(), e);
        }
    }

    /**
     * 同步活动到ES
     */
    public void syncActivity(Activity activity) {
        if (activity == null || Integer.valueOf(1).equals(activity.getDeleted())) {
            if (activity != null) deleteById("activity_" + activity.getId());
            return;
        }

        try {
            User user = userMapper.selectById(activity.getUserId());

            UnifiedDoc doc = UnifiedDoc.builder()
                    .id("activity_" + activity.getId())
                    .type("activity")
                    .businessId(activity.getId())
                    .title(activity.getTitle())
                    .content(activity.getContent())
                    .location(activity.getLocation())
                    .images(activity.getImages())
                    .userId(activity.getUserId())
                    .userName(user != null ? user.getUserName() : "未知用户")
                    .userAvatar(user != null ? user.getAvatar() : null)
                    .likeCount(activity.getLikeCount())
                    .commentCount(activity.getCommentCount())
                    .viewCount(activity.getViewCount())
                    .status(activity.getStatus())
                    .auditStatus(activity.getAuditStatus())
                    .createTime(activity.getCreateTime())
                    .updateTime(activity.getUpdateTime())
                    .maxPeople(activity.getMaxPeople())
                    .currentPeople(activity.getCurrentPeople())
                    .startTime(activity.getStartTime())
                    .endTime(activity.getEndTime())
                    .build();

            esClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(doc.getId())
                    .document(doc)
            );
            log.info("同步活动到ES成功: id={}", activity.getId());
        } catch (Exception e) {
            log.error("同步活动到ES失败: id={}", activity.getId(), e);
        }
    }

    /**
     * 同步宠物信息到ES
     */
    public void syncPetPost(PetPost petPost) {
        if (petPost == null || petPost.getStatus() == -1) {
            deleteById("pet_" + petPost.getId());
            return;
        }

        try {
            User user = userMapper.selectById(petPost.getUserId());

            UnifiedDoc doc = UnifiedDoc.builder()
                    .id("pet_" + petPost.getId())
                    .type("pet")
                    .businessId(petPost.getId())
                    .title(petPost.getTitle())
                    .content(petPost.getContent())
                    .images(petPost.getImages())
                    .userId(petPost.getUserId())
                    .userName(user != null ? user.getUserName() : "未知用户")
                    .userAvatar(user != null ? user.getAvatar() : null)
                    .likeCount(petPost.getLikeCount())
                    .commentCount(petPost.getCommentCount())
                    .viewCount(petPost.getViewCount())
                    .status(petPost.getStatus())
                    .auditStatus(petPost.getAuditStatus())
                    .createTime(petPost.getCreateTime())
                    .updateTime(petPost.getUpdateTime())
                    .petName(petPost.getPetName())
                    .petType(petPost.getPetType())
                    .petAge(petPost.getPetAge())
                    .petGender(petPost.getPetGender())
                    .address(petPost.getAddress())
                    .build();

            esClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(doc.getId())
                    .document(doc)
            );
            log.info("同步宠物信息到ES成功: id={}", petPost.getId());
        } catch (Exception e) {
            log.error("同步宠物信息到ES失败: id={}", petPost.getId(), e);
        }
    }

    /**
     * 从ES删除文档
     */
    public void deleteById(String id) {
        try {
            esClient.delete(d -> d
                    .index(INDEX_NAME)
                    .id(id)
            );
            log.info("从ES删除文档成功: id={}", id);
        } catch (Exception e) {
            log.error("从ES删除文档失败: id={}", id, e);
        }
    }

    /**
     * 批量同步（全量重建索引时使用）
     */
    public void batchSync(List<UnifiedDoc> docs) {
        try {
            var bulkRequest = new co.elastic.clients.elasticsearch.core.BulkRequest.Builder();
            for (UnifiedDoc doc : docs) {
                bulkRequest.operations(op -> op
                        .index(idx -> idx
                                .index(INDEX_NAME)
                                .id(doc.getId())
                                .document(doc)
                        )
                );
            }
            esClient.bulk(bulkRequest.build());
            log.info("批量同步ES成功: count={}", docs.size());
        } catch (Exception e) {
            log.error("批量同步ES失败", e);
        }
    }

    /**
     * 根据ID重新同步宠物信息到ES（审核状态变更后使用）
     */
    public void syncPetPostById(Long petId) {
        syncById(petId, "pet_", petPostMapper::selectById, this::syncPetPost);
    }

    /**
     * 根据ID重新同步活动到ES（审核状态变更后使用）
     */
    public void syncActivityById(Long activityId) {
        syncById(activityId, "activity_", activityMapper::selectById, this::syncActivity);
    }

    /**
     * 根据ID重新同步日记到ES（审核状态变更后使用）
     */
    public void syncDailyPostById(Long dailyId) {
        DailyPost dailyPost = dailyPostMapper.selectById(dailyId);
        if (dailyPost == null) {
            deleteById("daily_" + dailyId);
            return;
        }
        List<Long> topicIds = dailyTopicRelMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.hongjie.pms.modules.daily.entity.DailyTopicRel>()
                        .eq(com.hongjie.pms.modules.daily.entity.DailyTopicRel::getDailyId, dailyId)
        ).stream().map(com.hongjie.pms.modules.daily.entity.DailyTopicRel::getTopicId).collect(Collectors.toList());
        syncDailyPost(dailyPost, topicIds);
    }

    /**
     * 通用方法：根据ID查询实体，若不存在则删除ES文档，否则同步到ES
     */
    private <T> void syncById(Long id, String docPrefix, Function<Long, T> fetcher, Consumer<T> syncer) {
        T entity = fetcher.apply(id);
        if (entity == null) {
            deleteById(docPrefix + id);
            return;
        }
        syncer.accept(entity);
    }

    private String extractTitleFromContent(String content) {
        if (content == null) return "";
        // 取第一行或者前20个字作为标题
        String firstLine = content.split("\\n")[0];
        if (firstLine.length() > 50) {
            return firstLine.substring(0, 50);
        }
        return firstLine;
    }

    private List<String> getTopicNames(List<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) return List.of();
        List<Topic> topics = topicMapper.selectBatchIds(topicIds);
        return topics.stream()
                .map(Topic::getName)
                .collect(Collectors.toList());
    }

    /**
     * 删除索引
     */
    public void deleteIndex() {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(INDEX_NAME)).value();
            if (exists) {
                esClient.indices().delete(d -> d.index(INDEX_NAME));
                log.info("删除索引成功: {}", INDEX_NAME);
            }
        } catch (Exception e) {
            log.error("删除索引失败: {}", INDEX_NAME, e);
        }
    }

    /**
     * 重建索引（删除 -> 创建 -> 全量同步）
     */
    public void rebuildIndex() {
        try {
            // 1. 检查索引是否存在
            boolean exists = esClient.indices().exists(e -> e.index(INDEX_NAME)).value();

            if (exists) {
                log.info("索引 {} 已存在，准备删除...", INDEX_NAME);
                // 2. 删除旧索引
                esClient.indices().delete(d -> d.index(INDEX_NAME));
                log.info("索引 {} 删除成功", INDEX_NAME);

                // 等待删除完成
                Thread.sleep(1000);
            }

            // 3. 创建新索引
            createIndex();
            log.info("索引 {} 创建成功", INDEX_NAME);

            // 等待索引创建完成
            Thread.sleep(1000);

        } catch (Exception e) {
            log.error("重建索引失败", e);
        }
    }

    /**
     * 创建索引（带正确的日期格式）
     */
    public void createIndex() {
        try {
            CreateIndexRequest request = CreateIndexRequest.of(i -> i
                    .index(INDEX_NAME)
                    .settings(s -> s
                            .numberOfShards("3")
                            .numberOfReplicas("1")
                            .analysis(a -> a
                                    .analyzer("ik_max_word", an -> an
                                            .custom(c -> c.tokenizer("ik_max_word"))
                                    )
                                    .analyzer("ik_smart", an -> an
                                            .custom(c -> c.tokenizer("ik_smart"))
                                    )
                            )
                    )
                    .mappings(m -> m
                            .properties("id", p -> p.keyword(k -> k))
                            .properties("type", p -> p.keyword(k -> k))
                            .properties("businessId", p -> p.long_(l -> l))
                            .properties("title", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("content", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("topics", p -> p.text(t -> t.analyzer("ik_max_word")))
                            .properties("location", p -> p.text(t -> t.analyzer("ik_smart")))
                            .properties("images", p -> p.keyword(k -> k))
                            .properties("userId", p -> p.long_(l -> l))
                            .properties("userName", p -> p.text(t -> t.analyzer("ik_smart")))
                            .properties("userAvatar", p -> p.keyword(k -> k))
                            .properties("likeCount", p -> p.integer(i2 -> i2))
                            .properties("commentCount", p -> p.integer(i2 -> i2))
                            .properties("viewCount", p -> p.integer(i2 -> i2))
                            .properties("status", p -> p.integer(i2 -> i2))
                            .properties("auditStatus", p -> p.integer(i2 -> i2))
                            .properties("createTime", p -> p.date(d -> d.format(
                                    "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'T'HH:mm:ss||yyyy-MM-dd'T'HH:mm:ss.SSSZ||epoch_millis"
                            )))
                            .properties("updateTime", p -> p.date(d -> d.format(
                                    "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'T'HH:mm:ss||yyyy-MM-dd'T'HH:mm:ss.SSSZ||epoch_millis"
                            )))
                            .properties("startTime", p -> p.date(d -> d.format(
                                    "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'T'HH:mm:ss"
                            )))
                            .properties("endTime", p -> p.date(d -> d.format(
                                    "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'T'HH:mm:ss"
                            )))
                            .properties("videoUrl", p -> p.keyword(k -> k))
                            .properties("petName", p -> p.text(t -> t.analyzer("ik_smart")))
                            .properties("petType", p -> p.text(t -> t.analyzer("ik_smart")))
                            .properties("petAge", p -> p.keyword(k -> k))
                            .properties("petGender", p -> p.integer(i2 -> i2))
                            .properties("address", p -> p.text(t -> t.analyzer("ik_smart")))
                            .properties("maxPeople", p -> p.integer(i2 -> i2))
                            .properties("currentPeople", p -> p.integer(i2 -> i2))
                    )
            );

            esClient.indices().create(request);
            log.info("创建索引成功: {}", INDEX_NAME);

        } catch (Exception e) {
            log.error("创建索引失败: {}", INDEX_NAME, e);
            throw new RuntimeException("创建索引失败", e);
        }
    }
}