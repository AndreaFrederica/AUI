package cc.sirrus.apricityui.compat.cleanroom;

import com.sighs.apricityui.startup.StartupContext;
import dev.latvian.kubejs.event.EventJS;
import dev.latvian.kubejs.event.EventsJS;

public final class LegacyKubeStartupEvent extends EventJS {
    private final StartupContext context;

    private LegacyKubeStartupEvent(StartupContext context) {
        this.context = context;
    }

    public StartupContext getContext() {
        return context;
    }

    static void post(StartupContext context) {
        EventsJS.post("apricityui.startup", new LegacyKubeStartupEvent(context));
    }
}
