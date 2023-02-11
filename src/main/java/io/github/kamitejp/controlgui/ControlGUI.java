package io.github.kamitejp.controlgui;

import java.awt.Color;
import java.awt.Image;
import java.awt.Toolkit;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.SwingUtilities;

import com.github.weisj.darklaf.LafManager;
import com.github.weisj.darklaf.theme.DarculaTheme;
import com.github.weisj.darklaf.theme.spec.AccentColorRule;
import com.github.weisj.darklaf.theme.spec.FontPrototype;
import com.github.weisj.darklaf.theme.spec.FontSizeRule;

import io.github.kamitejp.platform.Platform;

public class ControlGUI {
  public static final List<Image> ICON_IMAGES =
    Stream.of(16, 32, 48, 128).map(size ->
      Toolkit.getDefaultToolkit().getImage(
        ControlGUI.class.getResource("/icon-%d.png".formatted(size))
      )
    ).toList();

  public ControlGUI(Platform platform) {
    LafManager.registerDefaultsAdjustmentTask((theme, p) -> {
      p.put("background", new Color(0x383532));
      p.put("backgroundContainer", new Color(0x504D4A));
      p.put("Label.foreground", new Color(0xFFFFFF));
      p.put("Button.foreground", new Color(0xFFFFFF));
      p.put("Button.background", new Color(0x383532));
    });

    var theme = (new DarculaTheme()).derive(
      FontSizeRule.relativeAdjustment(133),
      FontPrototype.getDefault(),
      AccentColorRule.fromColor(new Color(0xF5C4E4), new Color(0xF5C4E4))
    );

    LafManager.install(theme);

    SwingUtilities.invokeLater(() -> (new ControlGUIFrame()).init(platform));
  }
}
