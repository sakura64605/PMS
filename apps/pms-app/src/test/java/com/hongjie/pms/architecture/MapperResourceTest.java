package com.hongjie.pms.architecture;

import org.junit.jupiter.api.Test;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import com.hongjie.pms.modules.community.feed.infrastructure.persistence.mapper.UserInboxMapper;
import java.util.HashMap;
import static org.assertj.core.api.Assertions.*;

class MapperResourceTest {
    @Test
    void relocatedMapperXmlBindsEveryCustomStatement() throws Exception {
        String resource = "mapper/community/feed/UserInboxMapper.xml";
        var config = new MybatisConfiguration();
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, config, resource, new HashMap<>()).parse();
        }
        for (var method : UserInboxMapper.class.getDeclaredMethods())
            assertThat(
                            config.hasStatement(
                                    UserInboxMapper.class.getName() + "." + method.getName()))
                    .as(method.getName())
                    .isTrue();
    }
}
