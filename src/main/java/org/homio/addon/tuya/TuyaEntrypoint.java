package org.homio.addon.tuya;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.tuya.internal.cloud.TuyaOpenAPI;
import org.homio.addon.tuya.internal.local.UdpDiscoveryListener;
import org.homio.api.AddonConfiguration;
import org.homio.api.AddonEntrypoint;
import org.homio.api.Context;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.time.Duration;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

@Log4j2
@Component
@AddonConfiguration
@RequiredArgsConstructor
public class TuyaEntrypoint implements AddonEntrypoint {

  public static final String TUYA_ICON = "fac fa-bitfocus";
  public static final String TUYA_COLOR = "#D68C38";

  public static final EventLoopGroup eventLoopGroup = new NioEventLoopGroup();

  public static final @NotNull UdpDiscoveryListener udpDiscoveryListener = new UdpDiscoveryListener(eventLoopGroup);

  private final Context context;

  @Override
  public void init() {
    context.setting().listenValue(TuyaEntityCompactModeSetting.class, "tuya-compact-mode",
      (value) -> context.ui().updateItems(TuyaDeviceEntity.class));
    TuyaProjectEntity tuyaProjectEntity = ensureEntityExists(context);
    udpDiscoveryListener.setProjectEntityID(tuyaProjectEntity.getEntityID());
    try {
      udpDiscoveryListener.activate();
    } catch (Exception ex) {
      tuyaProjectEntity.setUdpMessage("Unable to start tuya udp discovery");
      log.error("Unable to start tuya udp discovery", ex);
      context.bgp().builder("tuya-udp-restart")
        .interval(Duration.ofSeconds(60))
        .execute(context -> {
          udpDiscoveryListener.activate();
          context.cancel();
          return null;
        });
    }
    context.setting().listenValue(ScanTuyaDevicesSetting.class, "scan-tuya", () ->
      tuyaProjectEntity.scanDevices(context));

    TuyaOpenAPI.setProjectEntity(tuyaProjectEntity);
    udpDiscoveryListener.setProjectEntityID(tuyaProjectEntity.getEntityID());
  }

  @Override
  public void destroy() {
    udpDiscoveryListener.deactivate();
    eventLoopGroup.shutdownGracefully();
  }

  public @NotNull TuyaProjectEntity ensureEntityExists(Context context) {
    TuyaProjectEntity entity = context.db().get(TuyaProjectEntity.class, PRIMARY_DEVICE);
    if (entity == null) {
      entity = new TuyaProjectEntity();
      entity.setEntityID(PRIMARY_DEVICE);
      entity.setName("Tuya primary project");
      if (context.event().isInternetUp()) {
        Integer countryCode = getCountryCode(context);
        if (countryCode != null) {
          entity.setCountryCode(countryCode);
        }
      }
      context.db().save(entity, false);
    }
    if (entity.getCountryCode() == null) {
      scheduleUpdateTuyaProjectOnInternetUp(context);
    }
    return entity;
  }

  private void scheduleUpdateTuyaProjectOnInternetUp(Context context) {
    context.event().runOnceOnInternetUp("create-tuya-project", () -> {
      Integer countryCode = getCountryCode(context);
      if (countryCode != null) {
        TuyaProjectEntity projectEntity = context.db().getRequire(TuyaProjectEntity.class, PRIMARY_DEVICE);
        context.db().save(projectEntity.setCountryCode(countryCode));
      }
    });
  }

  private Integer getCountryCode(Context context) {
    NetworkHardwareRepository networkHardwareRepository = context.getBean(NetworkHardwareRepository.class);
    String ipAddress = networkHardwareRepository.getOuterIpAddress();
    var ipGeoLocation = context.network().getIpGeoLocation(ipAddress);
    String country = ipGeoLocation.getCountry();
        /*if (StringUtils.isNotEmpty(country)) {
            var countryInfo = context.network().fetchCached("https://restcountries.com/v3.1/name/%s".formatted(country), ArrayNode.class);
            countryInfo.get(0).get("cca2").asText();
            return countryInfo.withArray("callingCodes").path(0).asInt();
        }*/
    return null;
  }
}
