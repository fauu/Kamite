package io.github.kamitejp.controlgui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.lang.invoke.MethodHandles;

import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.Kamite;
import io.github.kamitejp.platform.GenericPlatform;
import io.github.kamitejp.platform.OS;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.platform.linux.gnome.GnomePlatform;
import io.github.kamitejp.platform.linux.xorg.XorgDesktop;
import io.github.kamitejp.platform.linux.xorg.XorgPlatform;

public class ControlGUI extends JFrame {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String LINUX_LAF_CLASSNAME = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";
  private static final String DEFAULT_LAF_CLASSNAME = UIManager.getSystemLookAndFeelClassName();
  private static final Dimension DEFAULT_WINDOW_SIZE = new Dimension(750, 600);
  private static final String ICON_RESOURCE_PATH = "/icon-48.png";
  private static final Font APP_NAME_LABEL_FONT = new Font("Sans Serif", Font.BOLD, 32);
  private static final Font MSG_AREA_FONT = new Font("Courier", Font.PLAIN, 13);

  public void init(Platform platform) {
    OS os = null;
    if (platform != null) {
      os = platform.getOS();
    } else {
      os = GenericPlatform.detectOS();
    }
    try {
      UIManager.setLookAndFeel(os == OS.LINUX ? LINUX_LAF_CLASSNAME : DEFAULT_LAF_CLASSNAME);
      SwingUtilities.updateComponentTreeUI(this);
    } catch (Exception e) {
      LOG.debug("Could not change Look and Feel", e);
    }

    var toolkit = Toolkit.getDefaultToolkit();

    setTitle(Kamite.APP_NAME_DISPLAY);
    setSize(DEFAULT_WINDOW_SIZE);
    setMinimumSize(DEFAULT_WINDOW_SIZE);
    setIconImage(toolkit.getImage(getClass().getResource(ICON_RESOURCE_PATH)));
    setLocationRelativeTo(null);
    setDefaultCloseOperation(EXIT_ON_CLOSE);

    maybeApplyGnomeWindowNameFix(platform, toolkit);

    var pane = getContentPane();
    var layout = new GroupLayout(pane);
    pane.setLayout(layout);

    layout.setAutoCreateGaps(true);
    layout.setAutoCreateContainerGaps(true);

    var appNameLabel = new JLabel(Kamite.APP_NAME_DISPLAY);
    appNameLabel.setFont(APP_NAME_LABEL_FONT);

    var msgArea = new JTextArea(8, 0);
    msgArea.setLineWrap(true);
    msgArea.setWrapStyleWord(true);
    msgArea.setEditable(false);
    msgArea.setFont(MSG_AREA_FONT);
    JTextAreaAppender.addLog4j2TextAreaAppender(msgArea);

    var msgAreaScrollPane = new JScrollPane(msgArea);
    msgAreaScrollPane.setBorder(
      BorderFactory.createLineBorder(UIManager.getDefaults().getColor("Table.background"))
    );
    msgAreaScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

    var quitButton = new JButton("Quit");
    quitButton.addActionListener(e -> System.exit(0));

    layout.setHorizontalGroup(
      layout.createParallelGroup()
        .addComponent(appNameLabel)
        .addComponent(msgAreaScrollPane, 200, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addComponent(
          quitButton,
          GroupLayout.DEFAULT_SIZE,
          GroupLayout.DEFAULT_SIZE,
          Short.MAX_VALUE
        )
    );
    layout.setVerticalGroup(
      layout.createSequentialGroup()
        .addComponent(appNameLabel)
        .addComponent(msgAreaScrollPane)
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
        LOG.warn("Error while setting 'awtAppClassName'", e.toString()); // NOPMD
      }
    }
  }
}
