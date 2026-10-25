package com.hongjie.pms.modules.ai.application;
import com.hongjie.pms.modules.ai.application.tool.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ToolRegistrationTest {
    @Test void duplicateToolNamesFailAtStartup(){
        var first=mock(BaseTool.class);var second=mock(BaseTool.class);
        when(first.getName()).thenReturn("search");when(second.getName()).thenReturn("search");
        assertThatThrownBy(()->new ToolRegistry(List.of(first,second))).isInstanceOf(IllegalStateException.class);
    }
}
