package org.homio.addon.tuya;

import lombok.Getter;
import org.homio.api.Context;
import org.homio.api.workspace.scratch.Scratch3BaseDeviceBlocks;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3TuyaBlocks extends Scratch3BaseDeviceBlocks {

  public Scratch3TuyaBlocks(Context context, TuyaEntrypoint tuyaEntrypoint) {
    super("#BF2A63", context, tuyaEntrypoint, TuyaDeviceEntity.PREFIX);
  }
}
