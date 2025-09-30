package com.example.springAIDemo.model;

import java.time.LocalDate;

public class DailyOHLCRecords {
    private String date;
    private double open;
    private double high;
    private double low;
    private double close;
    private double prevClose;
    private long volume;
    private double ltp;

    private double change;
    private double changePct;

    private Double ma7;
    private Double ma10;
    private Double ma20;
    private Double ma30;
    private Double ma50;

    private Double rsi14;

    private Double macd;
    private Double macdSignal;
    private Double macdHistogram;

    private Double bollUpper;
    private Double bollLower;

    // Getters and Setters

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public double getOpen() {
        return open;
    }

    public void setOpen(double open) {
        this.open = open;
    }

    public double getHigh() {
        return high;
    }

    public void setHigh(double high) {
        this.high = high;
    }

    public double getLow() {
        return low;
    }

    public void setLow(double low) {
        this.low = low;
    }

    public double getClose() {
        return close;
    }

    public void setClose(double close) {
        this.close = close;
    }

    public long getVolume() {
        return volume;
    }

    public void setVolume(long volume) {
        this.volume = volume;
    }

    public double getChange() {
        return change;
    }

    public void setChange(double change) {
        this.change = change;
    }

    public double getChangePct() {
        return changePct;
    }

    public void setChangePct(double changePct) {
        this.changePct = changePct;
    }

    public Double getMa7() {
        return ma7;
    }

    public void setMa7(Double ma7) {
        this.ma7 = ma7;
    }

    public Double getMa10() {
        return ma10;
    }

    public void setMa10(Double ma10) {
        this.ma10 = ma10;
    }

    public Double getMa20() {
        return ma20;
    }

    public void setMa20(Double ma20) {
        this.ma20 = ma20;
    }

    public Double getMa30() {
        return ma30;
    }

    public void setMa30(Double ma30) {
        this.ma30 = ma30;
    }

    public Double getMa50() {
        return ma50;
    }

    public void setMa50(Double ma50) {
        this.ma50 = ma50;
    }

    public Double getRsi14() {
        return rsi14;
    }

    public void setRsi14(Double rsi14) {
        this.rsi14 = rsi14;
    }

    public Double getMacd() {
        return macd;
    }

    public void setMacd(Double macd) {
        this.macd = macd;
    }

    public Double getMacdSignal() {
        return macdSignal;
    }

    public void setMacdSignal(Double macdSignal) {
        this.macdSignal = macdSignal;
    }

    public Double getMacdHistogram() {
        return macdHistogram;
    }

    public void setMacdHistogram(Double macdHistogram) {
        this.macdHistogram = macdHistogram;
    }

    public Double getBollUpper() {
        return bollUpper;
    }

    public void setBollUpper(Double bollUpper) {
        this.bollUpper = bollUpper;
    }

    public Double getBollLower() {
        return bollLower;
    }

    public void setBollLower(Double bollLower) {
        this.bollLower = bollLower;
    }

    public double getPrevClose() {
        return prevClose;
    }

    public void setPrevClose(double prevClose) {
        this.prevClose = prevClose;
    }

    public double getLtp() {
        return ltp;
    }

    public void setLtp(double ltp) {
        this.ltp = ltp;
    }

    @Override
    public String toString() {
        return "DailyOHLCRecords{" +
                "date='" + date + '\'' +
                ", open=" + open +
                ", high=" + high +
                ", low=" + low +
                ", close=" + close +
                ", volume=" + volume +
                ", change=" + change +
                ", changePct=" + changePct +
                ", ma7=" + ma7 +
                ", ma10=" + ma10 +
                ", ma20=" + ma20 +
                ", ma30=" + ma30 +
                ", ma50=" + ma50 +
                ", rsi14=" + rsi14 +
                ", macd=" + macd +
                ", macdSignal=" + macdSignal +
                ", macdHistogram=" + macdHistogram +
                ", bollUpper=" + bollUpper +
                ", bollLower=" + bollLower +
                '}';
    }
}
