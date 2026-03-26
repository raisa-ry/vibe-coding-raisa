package com.hiking.model;

public class TripInput {
    private String durationType;   // "hours" or "days"
    private int durationValue;
    private int tempMin;
    private int tempMax;
    private boolean hasRain;
    private boolean hasWind;
    private boolean hasSnow;
    private boolean hasHeat;
    private String terrain;
    private String fitnessLevel;

    public String getDurationType() { return durationType; }
    public void setDurationType(String durationType) { this.durationType = durationType; }

    public int getDurationValue() { return durationValue; }
    public void setDurationValue(int durationValue) { this.durationValue = durationValue; }

    public int getTempMin() { return tempMin; }
    public void setTempMin(int tempMin) { this.tempMin = tempMin; }

    public int getTempMax() { return tempMax; }
    public void setTempMax(int tempMax) { this.tempMax = tempMax; }

    public boolean isHasRain() { return hasRain; }
    public void setHasRain(boolean hasRain) { this.hasRain = hasRain; }

    public boolean isHasWind() { return hasWind; }
    public void setHasWind(boolean hasWind) { this.hasWind = hasWind; }

    public boolean isHasSnow() { return hasSnow; }
    public void setHasSnow(boolean hasSnow) { this.hasSnow = hasSnow; }

    public boolean isHasHeat() { return hasHeat; }
    public void setHasHeat(boolean hasHeat) { this.hasHeat = hasHeat; }

    public String getTerrain() { return terrain; }
    public void setTerrain(String terrain) { this.terrain = terrain; }

    public String getFitnessLevel() { return fitnessLevel; }
    public void setFitnessLevel(String fitnessLevel) { this.fitnessLevel = fitnessLevel; }
}
