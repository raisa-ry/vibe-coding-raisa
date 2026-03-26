package com.hiking.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatIfScenario {
    private String scenario;
    private String icon;
    private String description;
    private List<String> items;

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getItems() { return items; }
    public void setItems(List<String> items) { this.items = items; }
}
