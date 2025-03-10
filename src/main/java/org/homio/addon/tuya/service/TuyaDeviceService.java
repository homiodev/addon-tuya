package org.homio.addon.tuya.service;

import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.tuya.TuyaDeviceEndpoint;
import org.homio.addon.tuya.TuyaDeviceEntity;
import org.homio.addon.tuya.TuyaProjectEntity;
import org.homio.addon.tuya.internal.cloud.TuyaOpenAPI;
import org.homio.addon.tuya.internal.cloud.dto.TuyaDeviceDTO;
import org.homio.addon.tuya.internal.local.DeviceInfoSubscriber;
import org.homio.addon.tuya.internal.local.DeviceStatusListener;
import org.homio.addon.tuya.internal.local.TuyaDeviceCommunicator;
import org.homio.addon.tuya.internal.local.dto.DeviceInfo;
import org.homio.addon.tuya.internal.util.SchemaDp;
import org.homio.api.Context;
import org.homio.api.ContextBGP;
import org.homio.api.ContextBGP.ThreadContext;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.model.device.ConfigDeviceDefinition;
import org.homio.api.model.device.ConfigDeviceDefinitionService;
import org.homio.api.model.device.ConfigDeviceEndpoint;
import org.homio.api.model.endpoint.DeviceEndpoint.EndpointType;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.api.state.DecimalType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.homio.addon.tuya.TuyaEntrypoint.TUYA_COLOR;
import static org.homio.addon.tuya.TuyaEntrypoint.eventLoopGroup;
import static org.homio.addon.tuya.TuyaEntrypoint.udpDiscoveryListener;
import static org.homio.addon.tuya.service.TuyaDiscoveryService.updateTuyaDeviceEntity;
import static org.homio.api.model.Status.ERROR;
import static org.homio.api.model.Status.INITIALIZE;
import static org.homio.api.model.Status.OFFLINE;
import static org.homio.api.model.Status.ONLINE;
import static org.homio.api.model.Status.UNKNOWN;
import static org.homio.api.model.Status.WAITING;
import static org.homio.api.model.endpoint.DeviceEndpoint.ENDPOINT_DEVICE_STATUS;
import static org.homio.api.model.endpoint.DeviceEndpoint.ENDPOINT_LAST_SEEN;

/**
 * Handles commands and state updates
 */
public class TuyaDeviceService extends ServiceInstance<TuyaDeviceEntity> implements DeviceInfoSubscriber, DeviceStatusListener {

  public static final ConfigDeviceDefinitionService CONFIG_DEVICE_SERVICE =
    new ConfigDeviceDefinitionService("tuya-devices.json");
  @Getter
  private final @NotNull Map<String, TuyaDeviceEndpoint> endpoints = new ConcurrentHashMap<>();
  private @NotNull Optional<TuyaDeviceCommunicator> tuyaDeviceCommunicator = Optional.empty();
  private @Nullable ThreadContext<Void> pollingJob;
  private @Nullable ThreadContext<Void> reconnectFuture;
  private @NotNull Map<String, SchemaDp> schemaDps = Map.of();
  private List<ConfigDeviceDefinition> models;

  public TuyaDeviceService(Context context, TuyaDeviceEntity entity) {
    super(context, entity, true, "Tuya device");
  }

  @Override
  public void processDeviceStatus(String cid, @NotNull Map<Integer, Object> deviceStatus) {
    log.debug("[{}]: received status message '{}'", entity.getEntityID(), deviceStatus);

    if (deviceStatus.isEmpty()) {
      // if status is empty -> need to use control method to request device status
      Map<Integer, @Nullable Object> commandRequest = new HashMap<>();
      endpoints.values().forEach(p -> {
        if (p.getDp() != 0) {
          commandRequest.put(p.getDp(), null);
        }
      });
      endpoints.values().stream().filter(p -> p.getDp2() != null).forEach(p -> commandRequest.put(p.getDp2(), null));

      tuyaDeviceCommunicator.ifPresent(c -> c.sendCommand(commandRequest));
    } else {
      deviceStatus.forEach(this::externalUpdate);
    }
  }

  @Override
  public void destroy(boolean forRestart, Exception ex) {
    closeAll();
    udpDiscoveryListener.unregisterListener(entity.getIeeeAddress());
  }

  @Override
  @SneakyThrows
  public void initialize() {
    this.closeAll(); // stop all before initialize
    createOrUpdateDeviceGroup();
    if (endpoints.isEmpty()) {
      addDeviceStatusEndpoint();
    }
    if (StringUtils.isEmpty(entity.getIeeeAddress())) {
      setEntityStatus(ERROR, "Empty device id");
      return;
    }
    if (isRequireFetchDeviceInfoFromCloud()) {
      context.event().runOnceOnInternetUp("tuya-service-init-" + entity.getEntityID(), this::fetchDeviceInfo);
      return;
    }
    // check if we have endpoints and add them if available
    if (endpoints.size() == 1) {
      this.schemaDps = entity.getSchema();
      if (!schemaDps.isEmpty()) {
        // fallback to retrieved schema
        for (Entry<String, SchemaDp> entry : schemaDps.entrySet()) {
          if (CONFIG_DEVICE_SERVICE.isIgnoreEndpoint(entry.getKey())) {
            log.info("[{}]: ({}): Skip endpoint: {}", entityID, entity.getTitle(), entry.getKey());
          } else {
            ConfigDeviceEndpoint endpoint = CONFIG_DEVICE_SERVICE.getDeviceEndpoints().get(entry.getKey());
            endpoints.put(entry.getKey(), new TuyaDeviceEndpoint(entry.getValue(), entity, endpoint));
          }
        }
        addLastSeenEndpoint();
      } else {
        setEntityStatus(OFFLINE, "No endpoints found");
        return;
      }
    }

    if (!entity.getIp().isBlank()) {
      deviceInfoChanged(new DeviceInfo(entity.getIp(), entity.getProtocolVersion().getVersionString()));
    } else {
      setEntityStatus(WAITING, "Waiting for IP address");
      udpDiscoveryListener.registerListener(entity.getIeeeAddress(), this);
    }
  }

  @Override
  public void onDisconnected(@NotNull String message) {
    setEntityStatus(ERROR, message);
    if (ContextBGP.cancel(pollingJob)) {
      pollingJob = null;
    }
    scheduleReconnect();
  }

  public @NotNull List<ConfigDeviceDefinition> findDevices() {
    if (models == null && !schemaDps.isEmpty()) {
      Set<String> endpoints = schemaDps.values().stream().map(SchemaDp::getCode).collect(Collectors.toSet());
      models = CONFIG_DEVICE_SERVICE.findDeviceDefinitionModels(entity.getModel(), endpoints);
    }
    return models == null ? List.of() : models;
  }

  private void setEntityStatus(@NotNull Status status, @Nullable String message) {
    if (entity.getStatus() != status || !Objects.equals(entity.getStatusMessage(), message)) {
      entity.setStatus(status, message);
      getEndpoints().get(ENDPOINT_DEVICE_STATUS).setValue(new StringType(toString()), true);
      TuyaProjectEntity projectEntity = TuyaOpenAPI.getProjectEntity();
      if (projectEntity != null) {
        projectEntity.getService().updateNotificationBlock();
      }
    }
  }

  public String getGroupDescription() {
    if (StringUtils.isEmpty(entity.getName()) || entity.getName().equals(entity.getIeeeAddress())) {
      return entity.getIeeeAddress();
    }
    return "${%s} [%s]".formatted(entity.getName(), entity.getIeeeAddress());
  }

  @Override
  public void deviceInfoChanged(DeviceInfo deviceInfo) {
    log.info("[{}]: Configuring IP address '{}' for thing '{}'.", entity.getEntityID(), deviceInfo, entity.getTitle());

    tuyaDeviceCommunicator.ifPresent(TuyaDeviceCommunicator::dispose);
    if (!deviceInfo.ip().equals(entity.getIp())) {
      context.db().save(entity.setIp(deviceInfo.ip()));
    }
    setEntityStatus(WAITING, null);

    tuyaDeviceCommunicator = Optional.of(new TuyaDeviceCommunicator(this, eventLoopGroup,
      deviceInfo.ip(), deviceInfo.protocolVersion(), entity));
  }

  private boolean isRequireFetchDeviceInfoFromCloud() {
    return isNotEmpty(entity.getIeeeAddress()) && (isEmpty(entity.getLocalKey()) || !entity.getJsonData().has("schema"));
  }

  @SneakyThrows
  public void tryFetchDeviceInfo() {
    log.info("[{}]: Fetching device {} info", entity.getEntityID(), entity);
    TuyaOpenAPI api = context.getBean(TuyaOpenAPI.class);
    setEntityStatus(INITIALIZE, null);
    try {
      TuyaDeviceDTO tuyaDevice = api.getDevice(entity.getIeeeAddress(), entity);
      log.info("[{}]: Fetched device {} info successfully", entity.getEntityID(), entity);
      if (updateTuyaDeviceEntity(tuyaDevice, api, entity)) {
        context.db().save(entity);
      }
    } catch (Exception ex) {
      log.error("[{}]: Error fetched device {} info", entity.getEntityID(), entity);
      setEntityStatus(ERROR, CommonUtils.getErrorMessage(ex));
    }
  }

  @Override
  public void onConnected() {
    if (!entity.getStatus().isOnline()) {
      setEntityStatus(ONLINE, null);
      scheduleRefreshDeviceStatus();
    }
  }

  private void closeAll() {
    tuyaDeviceCommunicator.ifPresent(TuyaDeviceCommunicator::dispose);
    tuyaDeviceCommunicator = Optional.empty();
    if (ContextBGP.cancel(reconnectFuture)) {
      reconnectFuture = null;
    }
    if (ContextBGP.cancel(pollingJob)) {
      pollingJob = null;
    }
  }

  private void fetchDeviceInfo() {
    setEntityStatus(INITIALIZE, null);
    // delay to able Tuya api get project
    context.bgp().builder("tuya-init-" + entity.getIeeeAddress())
      .delay(Duration.ofSeconds(1))
      .onError(e -> setEntityStatus(ERROR, CommonUtils.getErrorMessage(e)))
      .execute(this::tryFetchDeviceInfo);
  }

  private void externalUpdate(Integer dp, Object rawValue) {
    endpoints.get(ENDPOINT_LAST_SEEN).setValue(new DecimalType(System.currentTimeMillis()), true);
    TuyaDeviceEndpoint endpoint = endpoints.values().stream().filter(p -> p.getDp() == dp).findAny().orElse(null);
    if (endpoint != null) {
      State state = endpoint.rawValueToState(rawValue);
      if (state == null) {
        log.warn("[{}]: Could not update endpoint '{}' with value '{}'. Datatype incompatible.",
          entity.getEntityID(), endpoint.getDeviceID(), rawValue);
      } else {
        endpoint.setValue(state, true);
      }
    } else {
      List<TuyaDeviceEndpoint> dp2Endpoints = endpoints.values().stream().filter(p -> Objects.equals(p.getDp2(), dp)).toList();
      if (dp2Endpoints.isEmpty()) {
        log.debug("[{}]: Could not find endpoint for dp '{}' in thing '{}'", entity.getEntityID(), dp, entity.getTitle());
      } else {
        if (Boolean.class.isAssignableFrom(rawValue.getClass())) {
          for (TuyaDeviceEndpoint dp2Endpoint : dp2Endpoints) {
            dp2Endpoint.setValue(State.of(rawValue), true);
          }
          return;
        }
        log.warn("[{}]: Could not update endpoint '{}' with value {}. Datatype incompatible.",
          entity.getEntityID(), dp2Endpoints, rawValue);
      }
    }
  }

  public ActionResponseModel send(@NotNull Map<Integer, @Nullable Object> commands) {
    return tuyaDeviceCommunicator.map(communicator -> communicator.sendCommand(commands)).orElse(null);
  }

  private void createOrUpdateDeviceGroup() {
    Icon icon = entity.getEntityIcon();
    context.var().createGroup("tuya", "Tuya", builder ->
      builder.setIcon(new Icon("fas fa-fish-fins", TUYA_COLOR)).setLocked(true));
    context.var().createSubGroup("tuya", requireNonNull(entity.getIeeeAddress()), entity.getDeviceFullName(), builder ->
      builder.setIcon(icon).setDescription(getGroupDescription()).setLocked(true));
  }

  private void addDeviceStatusEndpoint() {
    SchemaDp schemaDp = new SchemaDp().setDp(0).setCode(ENDPOINT_DEVICE_STATUS).setType(EndpointType.select)
      .setRange(Status.set(ERROR, INITIALIZE, OFFLINE, ONLINE, UNKNOWN, WAITING));
    ConfigDeviceEndpoint endpoint = CONFIG_DEVICE_SERVICE.getDeviceEndpoints().get(schemaDp.getCode());
    TuyaDeviceEndpoint tuyaDeviceEndpoint = new TuyaDeviceEndpoint(schemaDp, entity, endpoint);
    endpoints.put(schemaDp.getCode(), tuyaDeviceEndpoint);
  }

  private void addLastSeenEndpoint() {
    SchemaDp schemaDp = new SchemaDp().setDp(0).setCode(ENDPOINT_LAST_SEEN).setType(EndpointType.number);
    ConfigDeviceEndpoint endpoint = CONFIG_DEVICE_SERVICE.getDeviceEndpoints().get(schemaDp.getCode());
    TuyaDeviceEndpoint tuyaDeviceEndpoint = new TuyaDeviceEndpoint(schemaDp, entity, endpoint);
    tuyaDeviceEndpoint.setInitialValue(new DecimalType(System.currentTimeMillis()));
    endpoints.put(schemaDp.getCode(), tuyaDeviceEndpoint);
  }

  private void scheduleRefreshDeviceStatus() {
    if (entity.getStatus().isOnline()) {
      // request all statuses
      //   tuyaDeviceCommunicator.ifPresent(TuyaDeviceCommunicator::requestStatus);

      tuyaDeviceCommunicator.ifPresent(communicator -> {
        context.bgp().builder("tuya-pull-all-%s".formatted(entity.getIeeeAddress()))
          .delay(Duration.ofSeconds(5))
          .execute(communicator::requestStatus);
        pollingJob = context.bgp().builder("tuya-pull-%s".formatted(entity.getIeeeAddress()))
          .intervalWithDelay(Duration.ofSeconds(entity.getPollingInterval()))
          .execute(communicator::refreshStatus);
      });
    }
  }

  private void scheduleReconnect() {
    tuyaDeviceCommunicator.ifPresent(communicator -> {
      ThreadContext<Void> reconnectFuture = this.reconnectFuture;
      // only re-connect if a device is present, we are not disposing the thing and either the reconnectFuture is
      // empty or already done
      if (reconnectFuture == null || reconnectFuture.isStopped()) {
        this.reconnectFuture =
          context.bgp().builder("tuya-connect-%s".formatted(entity.getIeeeAddress()))
            .delay(Duration.ofSeconds(entity.getReconnectInterval()))
            .execute(() -> {
              if (!entity.getStatus().isOnline()) {
                setEntityStatus(INITIALIZE, null);
                communicator.connect();
              }
            });
      }
    });
  }
}
