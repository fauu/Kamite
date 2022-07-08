package io.github.kamitejp.platform;

import javax.swing.KeyStroke;

import com.tulskiy.keymaster.common.Provider;

public interface GlobalKeybindingProvider {
  Provider getKeymasterProvider();

  void setKeymasterProvider(Provider provider);

  default void registerKeybinding(String keyStroke, Runnable cb) {
    if (getKeymasterProvider() == null) {
      setKeymasterProvider(Provider.getCurrentProvider(false));
    }
    getKeymasterProvider().register(KeyStroke.getKeyStroke(keyStroke), keybinding -> cb.run());
  }
  
  default void destroyKeybindings() {
    if (getKeymasterProvider() != null) {
      getKeymasterProvider().reset();
      getKeymasterProvider().stop();
    }
  }
}
