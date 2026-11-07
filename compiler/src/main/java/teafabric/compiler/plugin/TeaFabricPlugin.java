package teafabric.compiler.plugin;

import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

/** TeaVM compiler plugin: installs the call-site redirection transformer. */
public class TeaFabricPlugin implements TeaVMPlugin {
    @Override
    public void install(TeaVMHost host) {
        host.add(new RedirectTransformer(host.getClassLoader()));
    }
}
