package org.homio.addon.tuya.service;

import static org.homio.addon.tuya.TuyaEntrypoint.TUYA_COLOR;
import static org.homio.addon.tuya.TuyaEntrypoint.TUYA_ICON;

import java.time.Duration;
import lombok.Getter;
import lombok.SneakyThrows;
import org.homio.addon.tuya.TuyaDeviceEntity;
import org.homio.addon.tuya.TuyaProjectEntity;
import org.homio.addon.tuya.internal.cloud.TuyaOpenAPI;
import org.homio.api.Context;
import org.homio.api.model.Icon;
import org.homio.api.service.EntityService.ServiceInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public class TuyaProjectService extends ServiceInstance<TuyaProjectEntity> {

    private final TuyaOpenAPI api;

    @SneakyThrows
    public TuyaProjectService(@NotNull Context context, TuyaProjectEntity entity) {
        super(context, entity, true);
        this.api = context.getBean(TuyaOpenAPI.class);
    }

    public void initialize() {
        TuyaOpenAPI.setProjectEntity(entity);
        try {
            testService();
            entity.setStatusOnline();
            // fire device discovery
            context.getBean(TuyaDiscoveryService.class).scan(context, null, null);
        } catch (TuyaOpenAPI.TuyaApiNotReadyException te) {
            scheduleInitialize();
        }
    }

    @Override
    public void updateNotificationBlock() {
        context.ui().notification().addBlock(entityID, "Tuya", new Icon(TUYA_ICON, TUYA_COLOR), builder -> {
            builder.setStatus(entity.getStatus()).linkToEntity(entity);
            builder.setDevices(context.db().findAll(TuyaDeviceEntity.class));
        });
    }

    @Override
    @SneakyThrows
    protected void testService() {
        if (!api.isConnected()) {
            api.login();
        }
    }

    @Override
    public void destroy(boolean forRestart, @Nullable Exception ex) {
        updateNotificationBlock();
    }

    private void scheduleInitialize() {
        context.event().runOnceOnInternetUp("tuya-project-init", () -> {
            if (!entity.getStatus().isOnline()) {
                context.bgp().builder("init-tuya-project-service").delay(Duration.ofSeconds(5))
                        .execute(this::initialize);
            }
        });
    }
}
