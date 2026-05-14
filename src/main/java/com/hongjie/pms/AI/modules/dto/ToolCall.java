package com.hongjie.pms.AI.modules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolCall {
    private String id;
    private String type;
    private String name;
    private String arguments;
    private String result;
}