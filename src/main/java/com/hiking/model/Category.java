package com.hiking.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Category {
    private String name;
    private String icon;
    private List<String> items;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public List<String> getItems() { return items; }
    public void setItems(List<String> items) { this.items = items; }
}
