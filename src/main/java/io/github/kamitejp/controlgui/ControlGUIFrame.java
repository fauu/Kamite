package io.github.kamitejp.controlgui;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.lang.invoke.MethodHandles;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.Kamite;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.platform.linux.gnome.GnomePlatform;
import io.github.kamitejp.platform.linux.xorg.XorgDesktop;
import io.github.kamitejp.platform.linux.xorg.XorgPlatform;

class ControlGUIFrame extends JFrame {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final Dimension DEFAULT_WINDOW_SIZE = new Dimension(750, 600);

  private static final int DEF = GroupLayout.DEFAULT_SIZE;
  private static final int MAX = Short.MAX_VALUE;

  public void init(Platform platform) {
    var toolkit = Toolkit.getDefaultToolkit();

    setTitle(Kamite.APP_NAME_DISPLAY);
    setSize(DEFAULT_WINDOW_SIZE);
    setMinimumSize(DEFAULT_WINDOW_SIZE);
    setIconImages(ControlGUI.ICON_IMAGES);
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

    layout.setHorizontalGroup(
      layout.createParallelGroup()
        .addComponent(msgArea.getScrollPane(), 200, DEF, MAX)
        .addComponent(quitButton, DEF, DEF, MAX)
    );
    layout.setVerticalGroup(
      layout.createSequentialGroup()
        .addComponent(msgArea.getScrollPane())
        .addComponent(quitButton)
    );

    pack();
    msgArea.requestFocus();
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
