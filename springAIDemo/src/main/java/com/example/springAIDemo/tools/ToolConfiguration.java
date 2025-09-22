package com.example.springAIDemo.tools;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.List;

@Configuration
public class ToolConfiguration {

    @Bean
    public DateTimeTools dateTimeTools() {
        return new DateTimeTools();
    }

    @Bean
    public List<ToolCallback> dateTimeToolCallbacks(DateTimeTools dateTimeTools) {

        // Define tool for getCurrentDateTime()
        Method getCurrentDateTimeMethod = ReflectionUtils.findMethod(DateTimeTools.class, "getCurrentDateTime");
        MethodToolCallback getCurrentDateTimeCallback = MethodToolCallback.builder()
                .toolDefinition(ToolDefinitions.builder(getCurrentDateTimeMethod)
                        .description("Get the current Date and time in user's timezone")
                        .build())
                .toolMethod(getCurrentDateTimeMethod)
                .toolObject(dateTimeTools)
                .build();

        // Define tool for getInfo()
        Method getInfoMethod = ReflectionUtils.findMethod(DateTimeTools.class, "getInfo");
        MethodToolCallback getInfoCallback = MethodToolCallback.builder()
                .toolDefinition(ToolDefinitions.builder(getInfoMethod)
                        .description("Get some personal information about nikhil")
                        .build())
                .toolMethod(getInfoMethod)
                .toolObject(dateTimeTools)
                .build();

        // Define tool for getNumber()
        Method getNumberMethod = ReflectionUtils.findMethod(DateTimeTools.class, "getNumber");
        MethodToolCallback getNumberCallback = MethodToolCallback.builder()
                .toolDefinition(ToolDefinitions.builder(getNumberMethod)
                        .description("this will give you a number")
                        .build())
                .toolMethod(getNumberMethod)
                .toolObject(dateTimeTools)
                .build();

        return List.of(getCurrentDateTimeCallback, getInfoCallback, getNumberCallback);
    }
}
