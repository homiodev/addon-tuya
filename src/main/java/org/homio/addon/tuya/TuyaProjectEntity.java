package org.homio.addon.tuya;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.tuya.internal.cloud.TuyaOpenAPI;
import org.homio.addon.tuya.service.TuyaDiscoveryService;
import org.homio.addon.tuya.service.TuyaProjectService;
import org.homio.api.Context;
import org.homio.api.entity.HasStatusAndMsg;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.types.MicroControllerBaseEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.OptionModel.KeyValueEnum;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldLinkToEntity;
import org.homio.api.ui.field.UIFieldLinkToEntity.NavEntityTitle;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.color.UIFieldColorRef;
import org.homio.api.ui.field.inline.UIFieldInlineEntities;
import org.homio.api.ui.field.inline.UIFieldInlineEntityWidth;
import org.homio.api.util.Lang;
import org.homio.api.util.SecureString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.homio.api.ContextSetting.getMemValue;
import static org.homio.api.ContextSetting.setMemValue;

@SuppressWarnings({"JpaAttributeTypeInspection", "JpaAttributeMemberSignatureInspection", "unused"})
@Getter
@Setter
@Entity
@Accessors(chain = true)
@UISidebarChildren(icon = "fas fa-diagram-project", color = "#0088CC", allowCreateItem = false)
public final class TuyaProjectEntity extends MicroControllerBaseEntity
  implements EntityService<TuyaProjectService>,
  HasStatusAndMsg, HasEntityLog {

  @UIField(order = 9999, disableEdit = true, hideInEdit = true)
  @UIFieldInlineEntities(bg = "#27FF000D")
  public List<TuyaDeviceInlineEntity> getCoordinatorDevices() {
    return context().db().findAll(TuyaDeviceEntity.class)
      .stream()
      .sorted()
      .map(TuyaDeviceInlineEntity::new)
      .collect(Collectors.toList());
  }

  @Override
  public void setStatus(@Nullable Status status, @Nullable String msg) {
    boolean reloadItem = !getStatus().equals(status) || (status == Status.ERROR && !Objects.equals(getStatusMessage(), msg));
    super.setStatus(status, msg);
    if (reloadItem) {
      context().ui().updateItem(this);
    }
  }

  @UIField(order = 1, hideInEdit = true, hideOnEmpty = true, fullWidth = true, bg = "#334842C2", type = UIFieldType.HTML)
  public String getDescription() {
    String message = getStatusMessage();
    if (message != null && message.contains(":")) {
      return Lang.getServerMessage("TUYA.DESCRIPTION_" + message.split(":")[0]);
    }
    return Lang.getServerMessage("TUYA.DESCRIPTION");
  }

  @UIField(order = 1, required = true, inlineEditWhenEmpty = true, descriptionLabel = "tuyaAppUID")
  @UIFieldGroup(value = "AUTH", order = 10, borderColor = "#9C1A9C")
  public String getAppUID() {
    return getJsonData("appUID");
  }

  public void setAppUID(String value) {
    setJsonData("appUID", value);
  }

  @UIField(order = 4, required = true, inlineEditWhenEmpty = true, descriptionLabel = "tuyaAccessID")
  @UIFieldGroup("AUTH")
  public String getAccessID() {
    return getJsonData("accessId");
  }

  public void setAccessID(String value) {
    setJsonData("accessId", value);
  }

  @UIField(order = 5, required = true, inlineEditWhenEmpty = true, descriptionLabel = "tuyaAccessSecret")
  @UIFieldGroup("AUTH")
  public SecureString getAccessSecret() {
    return getJsonSecure("accessSecret");
  }

  public void setAccessSecret(String value) {
    setJsonDataSecure("accessSecret", value);
  }

  @UIField(order = 6, required = true, inlineEditWhenEmpty = true, descriptionLabel = "tuyaCountryCode")
  @UIFieldGroup("AUTH")
  public Integer getCountryCode() {
    if (getJsonData().has("cc")) {
      return getJsonData().getInt("cc");
    }
    return null;
  }

  public TuyaProjectEntity setCountryCode(Integer value) {
    setJsonData("cc", value);
    return this;
  }

  @UIField(order = 8, descriptionLabel = "tuyaDataCenter")
  @UIFieldGroup("AUTH")
  public DataCenter getDataCenter() {
    return getJsonDataEnum("dataCenter", DataCenter.CentralEurope);
  }

  public void setDataCenter(DataCenter value) {
    setJsonData("dataCenter", value);
  }

  @Override
  public String getDefaultName() {
    return "Tuya project";
  }

  @UIField(order = 10, hideOnEmpty = true, color = Color.RED)
  public String getUdpMessage() {
    return getMemValue(this, "udp", null);
  }

  public void setUdpMessage(String value) {
    setMemValue(this, "udp", "udp", value);
  }

  @Override
  protected @NotNull String getDevicePrefix() {
    return "tuya-project";
  }

  @Override
  public @NotNull Class<TuyaProjectService> getEntityServiceItemClass() {
    return TuyaProjectService.class;
  }

  @Override
  public @NotNull TuyaProjectService createService(@NotNull Context context) {
    return new TuyaProjectService(context, this);
  }

  @Override
  public void logBuilder(@NotNull EntityLogBuilder entityLogBuilder) {
    entityLogBuilder.addTopicFilterByEntityID("org.homio");
  }

  @Override
  public @NotNull Set<String> getConfigurationErrors() {
    Set<String> errors = new HashSet<>();
    if (getAppUID().isEmpty()) {
      errors.add("ERROR.NO_APP_UID");
    }
    if (getAccessID().isEmpty()) {
      errors.add("ERROR.NO_ACCESS_ID");
    }
    if (getAccessSecret().isEmpty()) {
      errors.add("ERROR.NO_ACCESS_SECRET");
    }
    if (getCountryCode() == null || getCountryCode() == 0) {
      errors.add("ERROR.NO_COUNTRY_CODE");
    }
    return errors;
  }

  @Override
  public long getEntityServiceHashCode() {
    return getJsonDataHashCode("appUID", "accessId", "accessSecret", "cc");
  }

  @UIContextMenuAction(value = "TUYA.SCAN_DEVICES", icon = "fas fa-barcode", iconColor = Color.PRIMARY_COLOR)
  public ActionResponseModel scanDevices(Context context) {
    context.bgp().runWithProgress("tuya-scan-devices").execute(progressBar -> {
      context.getBean(TuyaDiscoveryService.class).scan(context, progressBar);
    });
    return ActionResponseModel.fired();
  }

  @UIContextMenuAction(value = "TUYA.GET_DEVICE_LIST", icon = "fas fa-tape")
  public ActionResponseModel getDevicesList(Context context) {
    return ActionResponseModel.showJson("Tuya device list",
      context.getBean(TuyaDiscoveryService.class).getDeviceList(context));
  }

  @UIContextMenuAction(value = "TUYA.GET_USER_INFO", icon = "fas fa-tape")
  public ActionResponseModel getUserInfo(Context context) {
    return ActionResponseModel.showJson("Tuya device list",
      context.getBean(TuyaOpenAPI.class).getUserInfo());
  }

  @Override
  public boolean isDisableDelete() {
    return true;
  }

  @RequiredArgsConstructor
  public enum DataCenter implements KeyValueEnum {
    China("China", "https://openapi.tuyacn.com"),
    WesternAmerica("Western America", "https://openapi.tuyaus.com"),
    EasternAmerica("Eastern America (Azure/MS)", "https://openapi-ueaz.tuyaus.com"),
    CentralEurope("Central Europe", "https://openapi.tuyaeu.com"),
    WesternEurope("Western Europe (Azure/MS)", "https://openapi-weaz.tuyaeu.com"),
    India("China", "https://openapi.tuyain.com");

    private final String title;
    @Getter
    private final String url;

    @Override
    public @NotNull String getValue() {
      return title;
    }
  }

  @Getter
  @NoArgsConstructor
  private static class TuyaDeviceInlineEntity {

    @UIField(order = 1)
    @UIFieldInlineEntityWidth(35)
    @UIFieldLinkToEntity(TuyaDeviceEntity.class)
    private NavEntityTitle ieeeAddress;

    @UIField(order = 2)
    @UIFieldColorRef("color")
    private String name;

    @UIField(order = 4)
    @UIFieldInlineEntityWidth(10)
    private int endpointsCount;

    private String color;

    public TuyaDeviceInlineEntity(TuyaDeviceEntity entity) {
      color = entity.getStatus().getColor();
      name = entity.getName();
      if (StringUtils.isEmpty(name) || name.equalsIgnoreCase(entity.getIeeeAddress())) {
        name = entity.getDescription();
      }
      ieeeAddress = new NavEntityTitle(entity.getEntityID(), entity.getIeeeAddress());
      endpointsCount = entity.getService().getEndpoints().size();
    }
  }
}
