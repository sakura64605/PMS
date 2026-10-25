package com.hongjie.pms.refactor.statistics;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.modules.statistics.controller.StatisticsController;
import com.hongjie.pms.modules.statistics.service.StatisticsService;
import com.hongjie.pms.modules.statistics.dto.StatisticsResponseDto.DailyStatisticsDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDate;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StatisticsControllerTest {
    @Test void unchangedDailyEndpointUsesSharedCalendarAndOriginalResponseFields()throws Exception{
        var service=mock(StatisticsService.class);var calendar=mock(StatisticsCalendar.class);
        LocalDate date=LocalDate.of(2026,10,25);when(calendar.today()).thenReturn(date);
        when(service.getDailyStatistics(date)).thenReturn(DailyStatisticsDto.builder().statDate(date).dau(7).build());
        var json=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        var mvc=MockMvcBuilders.standaloneSetup(new StatisticsController(service,calendar))
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(json)).build();
        UserInfo admin=new UserInfo();admin.setRole(1);UserContext.setUser(admin);
        try{
            mvc.perform(get("/pet-system/admin/statistics/query").param("type","daily"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.statDate").value("2026-10-25"))
                    .andExpect(jsonPath("$.data.dau").value(7));
            verify(service).getDailyStatistics(date);
        }finally{UserContext.clear();}
    }
}
