package com.example.springAIDemo.model;

import java.util.ArrayList;
import java.util.List;

public class SupportResistanceLevel {
    public double priceLevel;
    public String levelType; // "Support" or "Resistance"
    public List<String> touchedDates = new ArrayList<>();
}
