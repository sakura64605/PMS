package com.hongjie.pms.refactor.feed;

/** One visibility predicate shared by paging, total and unread count. Values remain bound parameters. */
public final class FeedSql {
    private FeedSql() {}
    static final String PET_TYPES="('pet','pet_post','adopt','help')";
    static final String ACTIVITY_TYPES="('activity','pet_activity','pet_event')";

    private static String source(String table,String alias,String type,String types,boolean unread){
        String inbox="SELECT 1 FROM user_inbox i WHERE i.user_id=#{actor} AND i.poster_id="+alias+".user_id AND i.post_id="+alias+".id AND LOWER(i.post_type) IN "+types;
        return "SELECT '"+type+"' post_type,"+alias+".id post_id,"+alias+".user_id poster_id,"+alias+".create_time FROM "+table+" "+alias
                +" WHERE "+alias+".status=1 AND "+alias+".audit_status=1"
                +(type.equals("activity")?" AND "+alias+".deleted=0":"")
                +" AND EXISTS (SELECT 1 FROM follow f WHERE f.follower_id=#{actor} AND f.following_id="+alias+".user_id)"
                +" AND (EXISTS (SELECT 1 FROM big_v_config b WHERE b.user_id="+alias+".user_id AND b.use_pull_mode=1) OR EXISTS ("+inbox+"))"
                +(unread?" AND EXISTS ("+inbox+" AND i.is_read=0)":"");
    }
    private static String union(boolean unread){return source("pet_post","p","pet",PET_TYPES,unread)+" UNION ALL "+source("activity","a","activity",ACTIVITY_TYPES,unread);}
    public static String page(){return "SELECT * FROM ("+union(false)+") feed ORDER BY create_time DESC,post_type ASC,post_id DESC LIMIT #{size} OFFSET #{offset}";}
    public static String count(){return "SELECT COUNT(*) FROM ("+union(false)+") feed";}
    public static String unread(){return "SELECT COUNT(*) FROM ("+union(true)+") feed";}
}
