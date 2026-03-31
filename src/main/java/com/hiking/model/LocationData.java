package com.hiking.model;

public class LocationData {
    private String displayName;
    private double latitude;
    private double longitude;
    private int elevationFt;
    private int tempMin;
    private int tempMax;
    private boolean hasRain;
    private boolean hasWind;
    private boolean hasSnow;
    private boolean hasHeat;
    private String terrain;
    private String weatherSummary;
    private String tripDate;
    private String weatherNote;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public int getElevationFt() { return elevationFt; }
    public void setElevationFt(int elevationFt) { this.elevationFt = elevationFt; }

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

    public String getWeatherSummary() { return weatherSummary; }
    public void setWeatherSummary(String weatherSummary) { this.weatherSummary = weatherSummary; }

    public String getTripDate() { return tripDate; }
    public void setTripDate(String tripDate) { this.tripDate = tripDate; }

    public String getWeatherNote() { return weatherNote; }
    public void setWeatherNote(String weatherNote) { this.weatherNote = weatherNote; }
}
