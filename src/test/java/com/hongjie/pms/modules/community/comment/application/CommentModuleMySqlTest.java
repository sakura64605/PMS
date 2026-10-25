package com.hongjie.pms.modules.community.comment.application;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.community.comment.api.dto.request.CommentCreateRequest;
import com.hongjie.pms.modules.community.comment.infrastructure.persistence.mapper.CommentMapper;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.modules.community.like.infrastructure.persistence.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.community.comment.application.service.PmsCommentServiceImpl;
import com.hongjie.pms.modules.community.comment.application.converter.CommentDtoConverter;
import com.hongjie.pms.modules.community.comment.api.event.CommentNotificationEvent;
import com.hongjie.pms.shared.core.event.DomainEventPublisher;
import com.hongjie.pms.shared.extension.target.*;
import com.hongjie.pms.modules.content.petpost.application.handler.PetPostTargetHandler;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PmsPetPostMapper;
import com.hongjie.pms.architecture.support.MyBatisTestSupport;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
class CommentModuleMySqlTest {
    static SqlSessionFactory factory;
    SqlSession session;Connection db;long owner,actor,post;PmsCommentServiceImpl service;
    DomainEventPublisher events;
    @BeforeAll static void factory(){
        assumeTrue(MyBatisTestSupport.configured(),MyBatisTestSupport.skipReason());
        assertThat(MyBatisTestSupport.jdbcUrl()).matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):3306/pet_system_test(?:\\?.*)?");
        factory=MyBatisTestSupport.createFactory(CommentMapper.class,UserMapper.class,LikeRecordMapper.class,PmsPetPostMapper.class);
    }
    @BeforeEach void fixture() throws Exception {
        session=factory.openSession(false);db=session.getConnection();
        try(var q=db.createStatement();var r=q.executeQuery("SELECT DATABASE()")){r.next();assertThat(r.getString(1)).isEqualTo("pet_system_test");}
        var marker="__comment_"+UUID.randomUUID().toString().substring(0,16);
        owner=insert("INSERT INTO user(user_name,password,status) VALUES(?,?,1)",marker+"o","fixture");
        actor=insert("INSERT INTO user(user_name,password,status) VALUES(?,?,1)",marker+"a","fixture");
        post=insert("INSERT INTO pet_post(user_id,type,title,content,contact_phone,status,audit_status,comment_count) VALUES(?,0,?,?,?,1,1,0)",owner,marker,"content","13800000000");
        events=mock(DomainEventPublisher.class);
        service=new PmsCommentServiceImpl(session.getMapper(CommentMapper.class),session.getMapper(UserMapper.class),session.getMapper(LikeRecordMapper.class),
            new TargetHandlerRegistry(List.of(new PetPostTargetHandler(session.getMapper(PmsPetPostMapper.class)))),new TargetTypeCodec(),events,new CommentDtoConverter());
        UserContext.setUser(new UserInfo(actor,"actor",0,null,null));
    }
    @AfterEach void rollback(){UserContext.clear();if(session!=null){session.rollback(true);session.close();}}
    long insert(String sql,Object... args) throws SQLException {
        try(var q=db.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);q.executeUpdate();try(var r=q.getGeneratedKeys()){assertThat(r.next()).isTrue();return r.getLong(1);}
        }
    }
    long value(String sql) throws SQLException {try(var q=db.createStatement();var r=q.executeQuery(sql)){r.next();return r.getLong(1);}}
    CommentCreateRequest request(String type,Long parent){var r=new CommentCreateRequest();r.setTargetType(type);r.setTargetId(post);r.setContent("regression comment");r.setParentId(parent);return r;}
    @Test void creationNormalizesParentAndIncrementsCountOnce() throws Exception {
        service.createComment(request("pet",null));
        assertThat(value("SELECT comment_count FROM pet_post WHERE id="+post)).isEqualTo(1);
        assertThat(value("SELECT COUNT(*) FROM comment WHERE target_id="+post+" AND target_type='pet_post' AND parent_id=0 AND user_id="+actor)).isEqualTo(1);
        var notice=org.mockito.ArgumentCaptor.forClass(CommentNotificationEvent.class);verify(events).publish(notice.capture());
        assertThat(notice.getValue().getLink()).isEqualTo("/pet/"+post);
    }
    @Test void replyPersistsAndNotifiesParentWithNewAnchor() throws Exception {
        long parent=insert("INSERT INTO comment(user_id,target_type,target_id,content,parent_id,status) VALUES(?,'pet_post',?,'parent',0,1)",owner,post);
        service.createComment(request("pet_post",parent));
        long reply=value("SELECT id FROM comment WHERE target_id="+post+" AND parent_id="+parent);
        var notices=org.mockito.ArgumentCaptor.forClass(CommentNotificationEvent.class);verify(events,times(2)).publish(notices.capture());
        assertThat(notices.getAllValues().get(1).getLink()).isEqualTo("/pet/"+post+"?commentId="+reply);
        assertThat(value("SELECT comment_count FROM pet_post WHERE id="+post)).isEqualTo(1);
    }
    @Test void listAcceptsBothStoredDialectsAndPreservesEmptyPageBounds() throws Exception {
        insert("INSERT INTO comment(user_id,target_type,target_id,content,parent_id,status) VALUES(?,'pet',?,'legacy',0,1)",owner,post);
        insert("INSERT INTO comment(user_id,target_type,target_id,content,parent_id,status) VALUES(?,'pet_post',?,'canonical',0,1)",actor,post);
        var page=service.getCommentList("pet",post,1,20);assertThat(page.getTotal()).isEqualTo(2);assertThat(page.getRecords()).hasSize(2);
        var empty=service.getCommentList("pet_post",post,3,7);assertThat(empty.getCurrent()).isEqualTo(3);assertThat(empty.getSize()).isEqualTo(7);assertThat(empty.getRecords()).isEmpty();
    }
    @Test void unsupportedTypeDoesNotInsertOrIncrement() throws Exception {
        assertThatThrownBy(()->service.createComment(request("unsupported",null))).isInstanceOf(BusinessException.class);
        assertThat(value("SELECT COUNT(*) FROM comment WHERE target_id="+post)).isZero();
        assertThat(value("SELECT comment_count FROM pet_post WHERE id="+post)).isZero();verifyNoInteractions(events);
    }
}
