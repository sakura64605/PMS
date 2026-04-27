package com.hongjie.pms.modules.search.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "unified_search", createIndex = false)
@Setting(settingPath = "/elasticsearch/settings.json")
public class UnifiedDoc {

    @Id
    private String id;  // 格式: {type}_{businessId}

    @Field(type = FieldType.Keyword)
    private String type;  // daily, activity, pet

    @Field(type = FieldType.Long)
    private Long businessId;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private List<String> topics;

    @Field(type = FieldType.Text, analyzer = "ik_smart")
    private String location;

    @Field(type = FieldType.Text)
    private List<String> images;

    @Field(type = FieldType.Long)
    private Long userId;

    @Field(type = FieldType.Text, analyzer = "ik_smart")
    private String userName;

    @Field(type = FieldType.Text)
    private String userAvatar;

    @Field(type = FieldType.Integer)
    private Integer likeCount;

    @Field(type = FieldType.Integer)
    private Integer commentCount;

    @Field(type = FieldType.Integer)
    private Integer viewCount;

    @Field(type = FieldType.Integer)
    private Integer status;  // 1-正常 0-删除

    @Field(type = FieldType.Integer)
    private Integer auditStatus;  // 1-审核通过

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createTime;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updateTime;

    // ========== 日记特有字段 ==========
    @Field(type = FieldType.Text)
    private String videoUrl;

    // ========== 宠物特有字段 ==========
    @Field(type = FieldType.Text, analyzer = "ik_smart")
    private String petName;

    @Field(type = FieldType.Text, analyzer = "ik_smart")
    private String petType;

    @Field(type = FieldType.Text)
    private String petAge;

    @Field(type = FieldType.Integer)
    private Integer petGender;

    @Field(type = FieldType.Text)
    private String address;

    // ========== 活动特有字段 ==========
    @Field(type = FieldType.Integer)
    private Integer maxPeople;

    @Field(type = FieldType.Integer)
    private Integer currentPeople;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}