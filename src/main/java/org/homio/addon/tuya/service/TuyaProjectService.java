package org.homio.addon.tuya.service;

import static org.homio.addon.tuya.TuyaEntrypoint.TUYA_COLOR;
import static org.homio.addon.tuya.TuyaEntrypoint.TUYA_ICON;

import java.time.Duration;
import lombok.Getter;
import lombok.SneakyThrows;
import org.homio.addon.tuya.TuyaDeviceEntity;
import org.homio.addon.tuya.TuyaProjectEntity;
import org.homio.addon.tuya.internal.cloud.TuyaOpenAPI;
import org.homio.api.EntityContext;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService.ServiceInstance;
import org.jetbrains.annotations.NotNull;

@Getter
public class TuyaProjectService extends ServiceInstance<TuyaProjectEntity> {

    private final TuyaOpenAPI api;

    @SneakyThrows
    public TuyaProjectService(@NotNull EntityContext entityContext, TuyaProjectEntity entity) {
        super(entityContext, entity, true);
        this.api = entityContext.getBean(TuyaOpenAPI.class);
    }

    public void initialize() {
        TuyaOpenAPI.setProjectEntity(entity);
        entity.setStatus(Status.INITIALIZE);
        try {
            testService();
            entity.setStatusOnline();
            // fire device discovery
            entityContext.getBean(TuyaDiscoveryService.class).scan(entityContext, null, null);
        } catch (TuyaOpenAPI.TuyaApiNotReadyException te) {
            scheduleInitialize();
        } catch (Exception ex) {
            entity.setStatusError(ex);
        } finally {
            updateNotificationBlock();
        }
    }

    private void scheduleInitialize() {
        entityContext.event().runOnceOnInternetUp("tuya-project-init", () -> {
            if (!entity.getStatus().isOnline()) {
                entityContext.bgp().builder("init-tuya-project-service").delay(Duration.ofSeconds(5))
                        .execute(this::initialize);
            }
        });
    }

    @Override
    @SneakyThrows
    protected void testService() {
        if (!entity.isValid()) {
            throw new IllegalStateException("Not valid configuration");
        }
        if (!api.isConnected()) {
            api.login();
        }
    }

    @Override
    public void destroy() {
        updateNotificationBlock();
    }

    public void updateNotificationBlock() {
        entityContext.ui().addNotificationBlock(entityID, "Tuya", new Icon(TUYA_ICON, TUYA_COLOR), builder -> {
            builder.setStatus(entity.getStatus()).linkToEntity(entity);
            builder.setDevices(entityContext.findAll(TuyaDeviceEntity.class));
        });
    }
}
