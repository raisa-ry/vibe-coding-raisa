package com.hiking.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PackListResponse {
    private List<Category> categories;
    private List<WhatIfScenario> whatIfScenarios;
    private List<String> tips;

    public List<Category> getCategories() { return categories; }
    public void setCategories(List<Category> categories) { this.categories = categories; }

    public List<WhatIfScenario> getWhatIfScenarios() { return whatIfScenarios; }
    public void setWhatIfScenarios(List<WhatIfScenario> whatIfScenarios) { this.whatIfScenarios = whatIfScenarios; }

    public List<String> getTips() { return tips; }
    public void setTips(List<String> tips) { this.tips = tips; }
}
