package com.hongjie.pms.modules.analytics.application;
/** SQL identifiers are selected only from this closed enum, never from HTTP input. */
public enum StatisticsSource {
    USERS("user"),PETS("pet_post"),ACTIVITIES("activity"),DAILY("daily_post"),COMMENTS("comment"),
    LIKES("like_record"),FOLLOWS("follow"),FAVORITES("favorite_record"),SIGNUPS("activity_signup"),
    AUDITS("audit_record"),REPORTS("report_record"),MESSAGES("private_message");
    final String table;
    StatisticsSource(String table){this.table=table;}
}
