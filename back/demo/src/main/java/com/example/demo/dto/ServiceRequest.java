package com.example.demo.dto;

import java.math.BigDecimal;
import java.util.List;

public class ServiceRequest {
    private String title;
    private String description;
    private BigDecimal price;
    private String priceUnit;
    private String serviceType;
    private String location;
    private Integer isNegotiable;
    private List<String> tags;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getPriceUnit() { return priceUnit; }
    public void setPriceUnit(String priceUnit) { this.priceUnit = priceUnit; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Integer getIsNegotiable() { return isNegotiable; }
    public void setIsNegotiable(Integer isNegotiable) { this.isNegotiable = isNegotiable; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
