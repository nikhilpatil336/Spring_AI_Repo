package com.example.springAIDemo.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.LocalDateTime;
import java.util.Locale;

public class DateTimeTools {

    @Tool(description = "Get the current Date and time in user's timezone")
    public String getCurrentDateTime()
    {
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
    }

    @Tool(description = "Get some personal information about nikhil")
    public String getInfo()
    {
        return """
                nikhil is in 12th great and do part time job in market.
                He is studying science in K.V.Pendharkar college.
                He lives in Dombivli and daily travels Dadar to go to college.
                He likes Drawing.
                """;
    }


    @Tool(description = "this will give you a number")
    public int getNumber()
    {
        return 17;
    }
}