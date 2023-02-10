package io.github.kamitejp.controlgui;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.Kamite;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.platform.linux.gnome.GnomePlatform;
import io.github.kamitejp.platform.linux.xorg.XorgDesktop;
import io.github.kamitejp.platform.linux.xorg.XorgPlatform;

public class ControlGUI extends JFrame {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public static final List<Image> ICON_IMAGES =
    Stream.of(16, 32, 48, 128).map(size ->
      Toolkit.getDefaultToolkit().getImage(
        ControlGUI.class.getResource("/icon-%d.png".formatted(size))
      )
    ).toList();

  private static final Dimension DEFAULT_WINDOW_SIZE = new Dimension(750, 600);

  public void init(Platform platform) {
    SwingUtilities.invokeLater(() -> doInit(platform));
  }

  private void doInit(Platform platform) {
    try {
      UIManager.setLookAndFeel("com.bulenkov.darcula.DarculaLaf");
      SwingUtilities.updateComponentTreeUI(this);
    } catch (Exception e) {
      LOG.debug("Could not change Look and Feel", e);
    }

    var toolkit = Toolkit.getDefaultToolkit();

    setTitle(Kamite.APP_NAME_DISPLAY);
    setSize(DEFAULT_WINDOW_SIZE);
    setMinimumSize(DEFAULT_WINDOW_SIZE);
    setIconImages(ICON_IMAGES);
    setLocationRelativeTo(null);
    setDefaultCloseOperation(EXIT_ON_CLOSE);

    maybeApplyGnomeWindowNameFix(platform, toolkit);

    var pane = getContentPane();
    var layout = new GroupLayout(pane);
    pane.setLayout(layout);

    layout.setAutoCreateGaps(true);
    layout.setAutoCreateContainerGaps(true);

    var msgArea = new MessageArea();
    MessageAppender.setTargetContainer(msgArea);

    var quitButton = new JButton("Quit");
    quitButton.addActionListener(e -> System.exit(0));

    var def = GroupLayout.DEFAULT_SIZE;
    var max = Short.MAX_VALUE;
    layout.setHorizontalGroup(
      layout.createParallelGroup()
        .addComponent(msgArea.getScrollPane(), 200, def, max)
        .addComponent(quitButton, def, def, max)
    );
    layout.setVerticalGroup(
      layout.createSequentialGroup()
        .addComponent(msgArea.getScrollPane())
        .addComponent(quitButton)
    );

    pack();
    setVisible(true);

    LOG.debug("Initialized control GUI");
  }

  private static void maybeApplyGnomeWindowNameFix(Platform platform, Toolkit toolkit) {
    var isGnome = false;
    if (platform instanceof GnomePlatform) {
      isGnome = true;
    } else if (platform instanceof XorgPlatform xorgPlatform) {
      if (xorgPlatform.getDesktop() == XorgDesktop.GNOME) {
        isGnome = true;
      }
    }
    if (isGnome) {
      try {
        var awtAppClassNameField = toolkit.getClass().getDeclaredField("awtAppClassName");
        awtAppClassNameField.setAccessible(true); // NOPMD - necessary
        awtAppClassNameField.set(toolkit, Kamite.APP_NAME_DISPLAY);
        LOG.debug("Applied GNOME control GUI window name fix");
      } catch (NoSuchFieldException | SecurityException | IllegalAccessException e) {
        LOG.warn("Error while setting 'awtAppClassName'", () -> e.toString());
      }
    }
  }
}
