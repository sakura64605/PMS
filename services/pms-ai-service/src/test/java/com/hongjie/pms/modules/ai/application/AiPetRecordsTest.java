package com.hongjie.pms.modules.ai.application;
import com.hongjie.pms.modules.ai.infrastructure.persistence.entity.*;
import com.hongjie.pms.modules.ai.infrastructure.persistence.mapper.*;
import com.hongjie.pms.platform.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class AiPetRecordsTest {
 AiPetProfiles pets=mock(AiPetProfiles.class);
 AiPetRecordMapper records=mock(AiPetRecordMapper.class);
 AiChatMessageMapper messages=mock(AiChatMessageMapper.class);
 AiChatSessionMapper sessions=mock(AiChatSessionMapper.class);
 AiPetRecords service=new AiPetRecords(pets,records,messages,sessions);
 AiPetRecord input(){var r=new AiPetRecord();r.setKind("event");r.setEventState("unknown");r.setContent("昨天呕吐，今天用户反馈恢复");return r;}
 @Test void foreignPetCannotReadOrWrite(){when(pets.require(1L,2L)).thenThrow(new BusinessException(404,"missing"));assertThatThrownBy(()->service.list(1L,2L,null)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->service.save(1L,2L,null,input())).isInstanceOf(BusinessException.class);verifyNoInteractions(records,messages);}
 @Test void assistantMessageCannotBecomeFact(){var r=input();r.setSourceMessageId("msg");var m=new AiChatMessage();m.setRole("assistant");when(messages.selectOne(any())).thenReturn(m);assertThatThrownBy(()->service.save(1L,2L,null,r)).isInstanceOf(BusinessException.class);verifyNoInteractions(records);}
 @Test void sourceMustBelongToPetSession(){var r=input();r.setSourceMessageId("msg");var m=new AiChatMessage();m.setRole("user");m.setSessionId("other");when(messages.selectOne(any())).thenReturn(m);assertThatThrownBy(()->service.save(1L,2L,null,r)).isInstanceOf(BusinessException.class);verifyNoInteractions(records);}
 @Test void futureDateRejectedButUnknownAllowed(){var r=input();AiPetRecords.validate(r);r.setOccurredOn(LocalDate.now().plusDays(3));assertThatThrownBy(()->AiPetRecords.validate(r)).isInstanceOf(BusinessException.class);}
 @Test void manualSaveIgnoresForgedOwnerAndIdentifier(){var r=input();r.setId(999L);r.setPetId(88L);r.setUserId(44L);var result=service.save(1L,2L,null,r);assertThat(result.getId()).isNull();assertThat(result.getUserId()).isEqualTo(1L);assertThat(result.getPetId()).isEqualTo(2L);assertThat(result.getOccurredOn()).isNull();}
 @Test void deletedSourceCannotResurrect(){var r=input();r.setSourceMessageId("msg");var m=new AiChatMessage();m.setRole("user");when(messages.selectOne(any())).thenReturn(m);when(sessions.selectOne(any())).thenReturn(new AiChatSession());var old=input();old.setStatus(0);when(records.selectOne(any())).thenReturn(old);assertThatThrownBy(()->service.save(1L,2L,null,r)).isInstanceOf(BusinessException.class);verify(records,never()).insert(any(AiPetRecord.class));}
 @Test void generalConsultationDoesNotReadPetRecords(){assertThat(service.context(1L,null,"hi")).isEmpty();verifyNoInteractions(pets,records);}
 @Test void recordsDescribeTimeAndConfirmationBoundaries(){assertThat(AiPetRecords.render(List.of(input()))).contains("不是完整病史","发生日期","不等于医疗诊断","当前宠物");}
}
