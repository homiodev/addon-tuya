package org.homio.addon.tuya;

import lombok.Getter;
import org.homio.api.EntityContext;
import org.homio.api.workspace.scratch.Scratch3BaseDeviceBlocks;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3TuyaBlocks extends Scratch3BaseDeviceBlocks {

    public Scratch3TuyaBlocks(EntityContext entityContext, TuyaEntrypoint tuyaEntrypoint) {
        super("#BF2A63", entityContext, tuyaEntrypoint);
    }
}
