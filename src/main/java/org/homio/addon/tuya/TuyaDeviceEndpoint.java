package org.homio.addon.tuya;

import static org.homio.addon.tuya.service.TuyaDeviceService.CONFIG_DEVICE_SERVICE;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.tuya.internal.util.SchemaDp;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.device.ConfigDeviceEndpoint;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@SuppressWarnings("CommentedOutCode")
@Log4j2
public class TuyaDeviceEndpoint extends BaseDeviceEndpoint<TuyaDeviceEntity> {

    private static final List<String> COLOUR_CHANNEL_CODES = List.of("colour_data");
    private static final List<String> DIMMER_CHANNEL_CODES = List.of("bright_value", "bright_value_1", "bright_value_2",
        "temp_value");

    private final int dp;
    @Getter
    private final @Nullable Integer dp2;
    private final @NotNull SchemaDp schemaDp;
    @Getter
    private boolean oldColorMode = false; // for color endpoint only

    public TuyaDeviceEndpoint(
        @NotNull SchemaDp schemaDp,
        @NotNull TuyaDeviceEntity device,
        @Nullable ConfigDeviceEndpoint configEndpoint) {
        super("TUYA", device.getEntityContext());
        setIcon(new Icon(
            "fa fa-fw " + (configEndpoint == null ? "fa-tablet-screen-button" : configEndpoint.getIcon()),
            configEndpoint == null ? "#3894B5" : configEndpoint.getIconColor()));
        this.setRange(OptionModel.list(schemaDp.getRange()));
        setMin(schemaDp.getMin());
        setMax(schemaDp.getMax());
        this.schemaDp = schemaDp;
        this.dp = schemaDp.dp;
        this.dp2 = schemaDp.getDp2();

        init(
            CONFIG_DEVICE_SERVICE,
            schemaDp.getCode(),
            device,
            schemaDp.getUnit(),
            Boolean.TRUE.equals(schemaDp.getReadable()),
            Boolean.TRUE.equals(schemaDp.getWritable()),
            schemaDp.getCode(),
            evaluateEndpointType());
    }

    private static int hexColorToBrightness(String hexColor) {
        Color color = Color.decode(hexColor);
        // Calculate brightness using the average of RGB components
        return (int) ((color.getRed() + color.getGreen() + color.getBlue()) / 7.65);
    }

    /**
     * Convert a Tuya color string in hexadecimal notation to hex string
     *
     * @param hexColor the input string
     * @return the corresponding state
     */
    private static String hexColorDecode(String hexColor) {
        if (hexColor.length() == 12) {
            // 2 bytes H: 0-360, 2 bytes each S,B, 0-1000
            float h = Integer.parseInt(hexColor.substring(0, 4), 16);
            float s = Integer.parseInt(hexColor.substring(4, 8), 16) / 10F;
            float b = Integer.parseInt(hexColor.substring(8, 12), 16) / 10F;
            if (h == 360) {
                h = 0;
            }
            int rgb = Color.HSBtoRGB(h, s, b);
            return String.format("#%06X", (rgb & 0xFFFFFF));
        } else if (hexColor.length() == 14) {
            // 1 byte each RGB: 0-255, 2 byte H: 0-360, 1 byte each SB: 0-255
            int r = Integer.parseInt(hexColor.substring(0, 2), 16);
            int g = Integer.parseInt(hexColor.substring(2, 4), 16);
            int b = Integer.parseInt(hexColor.substring(4, 6), 16);

            return String.format("#%02X%02X%02X", r, g, b);
        } else {
            throw new IllegalArgumentException("Unknown color format");
        }
    }

    @Override
    public void writeValue(@NotNull State state) {
        Object targetValue;
        State targetState;
        switch (getEndpointType()) {
            case bool -> {
                targetState = OnOffType.of(state.boolValue());
                targetValue = targetState.boolValue();
            }
            case number, dimmer -> {
                targetState = state instanceof DecimalType ? (DecimalType) state : new DecimalType(state.intValue());
                targetValue = targetState.intValue();
            }
            default -> {
                targetState = state;
                targetValue = state.toString();
            }
        }
        setValue(targetState, true);
        getDevice().getService().send(Map.of(dp, targetValue));
    }

    @Override
    public UIInputBuilder createDimmerActionBuilder(@NotNull UIInputBuilder uiInputBuilder) {
        State value = getValue();
        TuyaDeviceEntity device = getDevice();
        if (dp2 != null) {
            uiInputBuilder.addCheckbox(getEntityID(), value.boolValue(), (entityContext, params) -> {
                setValue(OnOffType.of(params.getBoolean("value")), false);
                return device.getService().send(Map.of(dp2, value.boolValue()));
            }).setDisabled(!device.getStatus().isOnline());
        }
        uiInputBuilder.addSlider(getEntityID(), value.floatValue(0), 0F, 100F,
            (entityContext, params) -> {
                Map<Integer, Object> commandRequest = new HashMap<>();
                int brightness = (int) Math.round(params.getInt("value") * schemaDp.getMax() / 100.0);
                setValue(new DecimalType(brightness), false);
                if (brightness >= schemaDp.getMin()) {
                    commandRequest.put(dp, value);
                }
                if (dp2 != null) {
                    commandRequest.put(dp2, brightness >= schemaDp.getMin());
                }
                                /* ChannelConfiguration workModeConfig = channelIdToConfiguration.get("work_mode");
                                if (workModeConfig != null) {
                                    commandRequest.put(workModeConfig.dp, "white");
                                }*/
                return device.getService().send(commandRequest);
            }).setDisabled(!device.getStatus().isOnline());
        return uiInputBuilder;
    }

    @Override
    public ActionResponseModel onExternalUpdated() {
        return getDevice().getService().send(Map.of(dp, getValue().rawValue()));
    }

    @Override
    public UIInputBuilder createColorActionBuilder(@NotNull UIInputBuilder uiInputBuilder) {
        State value = getValue();
        TuyaDeviceEntity device = getDevice();
        if (dp2 != null) {
            uiInputBuilder.addCheckbox(getEntityID(), value.boolValue(), (entityContext, params) -> {
                setValue(OnOffType.of(params.getBoolean("value")), false);
                return device.getService().send(Map.of(dp2, value.boolValue()));
            }).setDisabled(!device.getStatus().isOnline());
        }
        uiInputBuilder.addColorPicker(getEntityID(), value.stringValue(), (entityContext, params) -> {
            Map<Integer, Object> commandRequest = new HashMap<>();
            setValue(new StringType(params.getString("value")), false);
            commandRequest.put(dp, hexColorEncode(value.stringValue()));
                        /* ChannelConfiguration workModeConfig = channelIdToConfiguration.get("work_mode");
                        if (workModeConfig != null) {
                            commandRequest.put(workModeConfig.dp, "colour");
                        } */
            if (dp2 != null) {
                commandRequest.put(dp2, hexColorToBrightness(value.stringValue()) > 0.0);
            }
            return device.getService().send(commandRequest);
        }).setDisabled(!device.getStatus().isOnline());
                    /* if (command instanceof PercentType) {
                        State oldState = channelStateCache.get(channelUID.getId());
                        if (!(oldState instanceof HSBType)) {
                            logger.debug("Discarding command '{}' to channel '{}', cannot determine old state", command,
                                    channelUID);
                            return;
                        }
                        HSBType newState = new HSBType(((HSBType) oldState).getHue(), ((HSBType) oldState).getSaturation(),
                                (PercentType) command);
                        commandRequest.put(configuration.dp, ConversionUtil.hexColorEncode(newState, oldColorMode));
                        ChannelConfiguration workModeConfig = channelIdToConfiguration.get("work_mode");
                        if (workModeConfig != null) {
                            commandRequest.put(workModeConfig.dp, "colour");
                        }
                        if (configuration.dp2 != 0) {
                            commandRequest.put(configuration.dp2, ((PercentType) command).doubleValue() > 0.0);
                        }
                    }*/
        return uiInputBuilder;
    }

    private EndpointType evaluateEndpointType() {
        if (COLOUR_CHANNEL_CODES.contains(schemaDp.getCode())) {
            return EndpointType.color;
        } else if (DIMMER_CHANNEL_CODES.contains(schemaDp.getCode())) {
            return EndpointType.dimmer;
        } else {
            return schemaDp.getType();
        }
    }

    private String hexColorEncode(String hexColor) {
        // Convert the hex color to RGB components using java.awt.Color
        Color color = Color.decode(hexColor);
        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();

        if (!oldColorMode) {
            int hue = 0;
            int saturation = 0;
            int brightness = (int) (Math.max(red, Math.max(green, blue)) / 2.55);

            return String.format("%04x%04x%04x", hue, saturation, brightness);
        } else {
            // Old color mode
            int hue = 0; // Hue is not directly available in pure RGB representation
            int saturation = 0; // Saturation is not directly available in pure RGB representation

            // Calculate brightness using the average of RGB components
            int brightness = (int) ((red + green + blue) / 7.65); // 7.65 = 255 * 2.55 / 100

            return String.format("%02x%02x%02x%04x%02x%02x", red, green, blue, hue, saturation, brightness);
        }
    }

   /* protected String decodeColor(String value) {
        oldColorMode = value.length() == 14;
        return hexColorDecode(value);
    }*/

    @Override
    public @NotNull Set<String> getHiddenEndpoints() {
        return Set.of();
    }
}
