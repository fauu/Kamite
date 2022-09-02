package io.github.kamitejp.platform;

import javax.swing.KeyStroke;

import com.tulskiy.keymaster.common.Provider;

public interface GlobalKeybindingProvider {
  Provider getKeymasterProvider();

  void setKeymasterProvider(Provider provider);

  default void registerKeybinding(
    String keyStrokeStr, Runnable cb
  ) throws InvalidKeyStrokeException {
    if (getKeymasterProvider() == null) {
      setKeymasterProvider(Provider.getCurrentProvider(false));
    }

    var keyStroke = KeyStroke.getKeyStroke(keyStrokeStr);
    if (keyStroke == null) {
      throw new InvalidKeyStrokeException();
    }

    getKeymasterProvider().register(keyStroke, keybinding -> cb.run());
  }

  default void destroyKeybindings() {
    if (getKeymasterProvider() != null) {
      getKeymasterProvider().reset();
      getKeymasterProvider().stop();
    }
  }
}
