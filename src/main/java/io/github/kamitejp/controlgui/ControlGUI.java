package io.github.kamitejp.controlgui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.awt.Toolkit;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import com.github.weisj.darklaf.LafManager;
import com.github.weisj.darklaf.theme.DarculaTheme;
import com.github.weisj.darklaf.theme.spec.AccentColorRule;
import com.github.weisj.darklaf.theme.spec.FontPrototype;
import com.github.weisj.darklaf.theme.spec.FontSizeRule;

import io.github.kamitejp.controlgui.ui.Frame;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.platform.windows.WindowsPlatform;

@SuppressWarnings("PMD.UseUtilityClass")
public class ControlGUI {
  public static final Color COLOR_FG = new Color(0xFFFFFF);
  public static final Color COLOR_BG = new Color(0x383532);
  public static final Color COLOR_BG2 = new Color(0x484542);
  public static final Color COLOR_BG2_HL = new Color(0x504D4A);
  public static final Color COLOR_BG3 = new Color(0x585552);
  public static final Color COLOR_ACCA = new Color(0xFFFD96);
  public static final Color COLOR_ACCB = new Color(0xF5C4E4);
  public static final Color COLOR_WARNING = new Color(0xC29F48);
  public static final Color COLOR_ERROR2 = new Color(0xC25048);

  public static final int DEFAULT_FONT_SIZE_RELATIVE = 133;
  public static final Border MESSAGE_AREA_BORDER = new EmptyBorder(6, 6, 6, 6);
  public static final int MESSAGE_HORIZONTAL_GAP = 10;
  public static final int MESSAGE_VERTICAL_GAP = 20;

  private static Font fontMonospacedDefault;

  public static final List<Image> ICON_IMAGES = Stream.of(16, 32, 48, 128)
      .map(size -> Toolkit.getDefaultToolkit().getImage(
          ControlGUI.class.getResource("/icon-%d.png".formatted(size))))
      .toList();

  public ControlGUI(Platform platform) {
    LafManager.registerDefaultsAdjustmentTask((theme, p) -> {
      p.put("background", COLOR_BG);
      p.put("backgroundContainer", COLOR_BG2_HL);
      p.put("Label.foreground", COLOR_FG);
      p.put("Button.foreground", COLOR_FG);
      p.put("Button.background", COLOR_BG2);
    });

    LafManager.registerInitTask((theme, d) -> {
      d.put("Button.activeFillColorClick", COLOR_BG3);
    });

    var theme = new DarculaTheme().derive(
        FontSizeRule.relativeAdjustment(DEFAULT_FONT_SIZE_RELATIVE),
        FontPrototype.getDefault(),
        AccentColorRule.fromColor(COLOR_ACCA, COLOR_ACCB));

    if (platform instanceof WindowsPlatform) {
      // The program icon is distorted in the custom Windows titlebar, so we disable
      // the custom titlebar
      LafManager.setDecorationsEnabled(false);
    }

    LafManager.install(theme);

    // QUAL: There's likely a way to handle this better
    fontMonospacedDefault = new Font( // NOPMD (assignment to non-final static)
        "Monospaced",
        Font.PLAIN,
        UIManager.getDefaults().getFont("Label.font").getSize());

    SwingUtilities.invokeLater(() -> new Frame().init(platform));
  }

  public static Font getFontMonospacedDefault() {
    return fontMonospacedDefault;
  }
}
