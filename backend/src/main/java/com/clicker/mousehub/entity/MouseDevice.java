package com.clicker.mousehub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("mice")
public class MouseDevice {
    @TableId(type = IdType.INPUT)
    private UUID id;
    private String brand;
    private String model;
    private String variant;
    private String slug;
    private String aliases;
    private String status;
    private String sizeCategory;
    private BigDecimal lengthMm;
    private BigDecimal widthMm;
    private BigDecimal heightMm;
    private BigDecimal weightG;
    private String shapeType;
    private String handCompatibility;
    private String sensorName;
    private Integer maxDpi;
    private Integer maxPollingRateHz;
    private Integer trackingSpeedIps;
    private BigDecimal accelerationG;
    private Integer buttonCount;
    private Integer sideButtonCount;
    private String switchName;
    private String encoderName;
    private String connectionModes;
    private String material;
    private String materialGeneral;
    private String materialSpecific;
    private String humpPlacement;
    private String frontFlare;
    private String sideCurvature;
    private Boolean thumbRest;
    private Boolean ringFingerRest;
    private String sensorType;
    private Boolean adjustableSensorPosition;
    private BigDecimal sensorPositionX;
    private BigDecimal sensorPositionY;
    private BigDecimal sensorPositionX2;
    private BigDecimal sensorPositionY2;
    private Boolean hotSwappableSwitches;
    private String switchType;
    private Integer switchLifeSpanM;
    private String encoderType;
    private Integer encoderSteps;
    private String purchaseChannels;
    private String imageUrl;
    private String primarySourceUrl;
    private String sourceNotes;
    private OffsetDateTime verifiedAt;
    private String verificationWorkflowStatus;
    private String verificationAssignee;
    private String verificationNote;
    private OffsetDateTime verificationDueAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public String displayName() { return brand + " " + model + (variant == null || variant.isBlank() ? "" : " " + variant); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getAliases() { return aliases; }
    public void setAliases(String aliases) { this.aliases = aliases; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSizeCategory() { return sizeCategory; }
    public void setSizeCategory(String sizeCategory) { this.sizeCategory = sizeCategory; }
    public BigDecimal getLengthMm() { return lengthMm; }
    public void setLengthMm(BigDecimal lengthMm) { this.lengthMm = lengthMm; }
    public BigDecimal getWidthMm() { return widthMm; }
    public void setWidthMm(BigDecimal widthMm) { this.widthMm = widthMm; }
    public BigDecimal getHeightMm() { return heightMm; }
    public void setHeightMm(BigDecimal heightMm) { this.heightMm = heightMm; }
    public BigDecimal getWeightG() { return weightG; }
    public void setWeightG(BigDecimal weightG) { this.weightG = weightG; }
    public String getShapeType() { return shapeType; }
    public void setShapeType(String shapeType) { this.shapeType = shapeType; }
    public String getHandCompatibility() { return handCompatibility; }
    public void setHandCompatibility(String handCompatibility) { this.handCompatibility = handCompatibility; }
    public String getSensorName() { return sensorName; }
    public void setSensorName(String sensorName) { this.sensorName = sensorName; }
    public Integer getMaxDpi() { return maxDpi; }
    public void setMaxDpi(Integer maxDpi) { this.maxDpi = maxDpi; }
    public Integer getMaxPollingRateHz() { return maxPollingRateHz; }
    public void setMaxPollingRateHz(Integer maxPollingRateHz) { this.maxPollingRateHz = maxPollingRateHz; }
    public Integer getTrackingSpeedIps() { return trackingSpeedIps; }
    public void setTrackingSpeedIps(Integer trackingSpeedIps) { this.trackingSpeedIps = trackingSpeedIps; }
    public BigDecimal getAccelerationG() { return accelerationG; }
    public void setAccelerationG(BigDecimal accelerationG) { this.accelerationG = accelerationG; }
    public Integer getButtonCount() { return buttonCount; }
    public void setButtonCount(Integer buttonCount) { this.buttonCount = buttonCount; }
    public Integer getSideButtonCount() { return sideButtonCount; }
    public void setSideButtonCount(Integer sideButtonCount) { this.sideButtonCount = sideButtonCount; }
    public String getSwitchName() { return switchName; }
    public void setSwitchName(String switchName) { this.switchName = switchName; }
    public String getEncoderName() { return encoderName; }
    public void setEncoderName(String encoderName) { this.encoderName = encoderName; }
    public String getConnectionModes() { return connectionModes; }
    public void setConnectionModes(String connectionModes) { this.connectionModes = connectionModes; }
    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
    public String getMaterialGeneral() { return materialGeneral; }
    public void setMaterialGeneral(String value) { this.materialGeneral = value; }
    public String getMaterialSpecific() { return materialSpecific; }
    public void setMaterialSpecific(String value) { this.materialSpecific = value; }
    public String getHumpPlacement() { return humpPlacement; }
    public void setHumpPlacement(String value) { this.humpPlacement = value; }
    public String getFrontFlare() { return frontFlare; }
    public void setFrontFlare(String value) { this.frontFlare = value; }
    public String getSideCurvature() { return sideCurvature; }
    public void setSideCurvature(String value) { this.sideCurvature = value; }
    public Boolean getThumbRest() { return thumbRest; }
    public void setThumbRest(Boolean value) { this.thumbRest = value; }
    public Boolean getRingFingerRest() { return ringFingerRest; }
    public void setRingFingerRest(Boolean value) { this.ringFingerRest = value; }
    public String getSensorType() { return sensorType; }
    public void setSensorType(String value) { this.sensorType = value; }
    public Boolean getAdjustableSensorPosition() { return adjustableSensorPosition; }
    public void setAdjustableSensorPosition(Boolean value) { this.adjustableSensorPosition = value; }
    public BigDecimal getSensorPositionX() { return sensorPositionX; }
    public void setSensorPositionX(BigDecimal value) { this.sensorPositionX = value; }
    public BigDecimal getSensorPositionY() { return sensorPositionY; }
    public void setSensorPositionY(BigDecimal value) { this.sensorPositionY = value; }
    public BigDecimal getSensorPositionX2() { return sensorPositionX2; }
    public void setSensorPositionX2(BigDecimal value) { this.sensorPositionX2 = value; }
    public BigDecimal getSensorPositionY2() { return sensorPositionY2; }
    public void setSensorPositionY2(BigDecimal value) { this.sensorPositionY2 = value; }
    public Boolean getHotSwappableSwitches() { return hotSwappableSwitches; }
    public void setHotSwappableSwitches(Boolean value) { this.hotSwappableSwitches = value; }
    public String getSwitchType() { return switchType; }
    public void setSwitchType(String value) { this.switchType = value; }
    public Integer getSwitchLifeSpanM() { return switchLifeSpanM; }
    public void setSwitchLifeSpanM(Integer value) { this.switchLifeSpanM = value; }
    public String getEncoderType() { return encoderType; }
    public void setEncoderType(String value) { this.encoderType = value; }
    public Integer getEncoderSteps() { return encoderSteps; }
    public void setEncoderSteps(Integer value) { this.encoderSteps = value; }
    public String getPurchaseChannels() { return purchaseChannels; }
    public void setPurchaseChannels(String value) { this.purchaseChannels = value; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String value) { this.imageUrl = value; }
    public String getPrimarySourceUrl() { return primarySourceUrl; }
    public void setPrimarySourceUrl(String primarySourceUrl) { this.primarySourceUrl = primarySourceUrl; }
    public String getSourceNotes() { return sourceNotes; }
    public void setSourceNotes(String sourceNotes) { this.sourceNotes = sourceNotes; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(OffsetDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getVerificationWorkflowStatus() { return verificationWorkflowStatus; }
    public void setVerificationWorkflowStatus(String value) { this.verificationWorkflowStatus = value; }
    public String getVerificationAssignee() { return verificationAssignee; }
    public void setVerificationAssignee(String value) { this.verificationAssignee = value; }
    public String getVerificationNote() { return verificationNote; }
    public void setVerificationNote(String value) { this.verificationNote = value; }
    public OffsetDateTime getVerificationDueAt() { return verificationDueAt; }
    public void setVerificationDueAt(OffsetDateTime value) { this.verificationDueAt = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
