package com.agentframework.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownstreamPolicy {
    private String type;
    private String name;
    private Map<String, Object> config;
}
