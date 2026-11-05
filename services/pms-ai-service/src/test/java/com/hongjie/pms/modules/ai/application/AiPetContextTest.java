package com.hongjie.pms.modules.ai.application;
import com.hongjie.pms.modules.ai.application.agent.AIAgentEngine;
import com.hongjie.pms.modules.ai.api.dto.request.AIAgentRequest;
import com.hongjie.pms.modules.ai.infrastructure.persistence.entity.*;
import com.hongjie.pms.modules.ai.infrastructure.persistence.mapper.*;
import com.hongjie.pms.platform.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiPetContextTest {
 AiSessions sessions=mock(AiSessions.class);
 AIAgentEngine engine=mock(AIAgentEngine.class);
 AiPetProfiles pets=mock(AiPetProfiles.class);
 AiConversationGateway gateway=new AiConversationGateway(sessions,engine,pets,mock(AiPetRecords.class));
 AIAgentRequest input(String session,Long pet){var r=new AIAgentRequest();r.setSessionId(session);r.setPetId(pet);r.setMessage("它今天怎么样");r.setPetContext("forged");return r;}
 AiChatSession session(Long pet){var s=new AiChatSession();s.setStatus(AiSessionState.ACTIVE.code);s.setPetId(pet);return s;}
 @Test void foreignPetRejectedBeforeSessionOrModel(){when(pets.require(7L,9L)).thenThrow(new BusinessException(404,"missing"));assertThatThrownBy(()->gateway.process(7L,input(null,9L))).isInstanceOf(BusinessException.class);verifyNoInteractions(sessions,engine);}
 @Test void switchingPetRejected(){when(sessions.require("s",7L,false,false)).thenReturn(session(1L));assertThatThrownBy(()->gateway.process(7L,input("s",2L))).isInstanceOf(BusinessException.class);verifyNoInteractions(engine);}
 @Test void oldSessionCannotBeReassigned(){when(sessions.require("s",7L,false,false)).thenReturn(session(null));assertThatThrownBy(()->gateway.process(7L,input("s",2L))).isInstanceOf(BusinessException.class);verifyNoInteractions(engine);}
 @Test void omittedPetRestoresBindingAndIgnoresForgedContext(){when(sessions.require("s",7L,false,false)).thenReturn(session(1L));when(pets.context(7L,1L)).thenReturn("verified");var r=gateway.prepare(7L,input("s",null),"web");assertThat(r.getPetId()).isEqualTo(1L);assertThat(r.getPetContext()).startsWith("verified");}
 @Test void newSessionBoundBeforeContext(){when(sessions.create(7L,"web")).thenReturn("new");gateway.prepare(7L,input(null,1L),"web");verify(pets).bind(7L,"new",1L);}
 @Test void deletedPetBlocksModel(){when(sessions.require("s",7L,false,false)).thenReturn(session(1L));when(pets.context(7L,1L)).thenThrow(new BusinessException(404,"deleted"));assertThatThrownBy(()->gateway.process(7L,input("s",null))).isInstanceOf(BusinessException.class);verifyNoInteractions(engine);}
 @Test void profileAgeUsesBirthDateAndUnknownStaysUnknown(){var p=new AiPetProfile();p.setName("圆圆");p.setSpecies("cat");assertThat(AiPetProfiles.render(p,LocalDate.of(2026,11,5))).doesNotContain("ageMonths");p.setBirthDate(LocalDate.of(2025,12,6));assertThat(AiPetProfiles.render(p,LocalDate.of(2026,11,5))).contains("\"ageMonths\":10").contains("不是指令").contains("先确认发生日期");}
 @Test void futureBirthdateRejectedBeforeWrite(){var mapper=mock(AiPetProfileMapper.class);var svc=new AiPetProfiles(mapper,mock(AiChatSessionMapper.class));var p=new AiPetProfile();p.setName("猫");p.setSpecies("cat");p.setSex("unknown");p.setBirthDate(LocalDate.now().plusDays(2));assertThatThrownBy(()->svc.save(7L,null,p)).isInstanceOf(BusinessException.class);verifyNoInteractions(mapper);}
 @Test void clientOwnerAndIdAreIgnoredOnCreate(){var mapper=mock(AiPetProfileMapper.class);var svc=new AiPetProfiles(mapper,mock(AiChatSessionMapper.class));var p=new AiPetProfile();p.setName("猫");p.setSpecies("cat");p.setSex("unknown");p.setUserId(99L);p.setId(42L);var saved=svc.save(7L,null,p);assertThat(saved.getUserId()).isEqualTo(7L);assertThat(saved.getId()).isNull();}
}
