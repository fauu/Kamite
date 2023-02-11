package io.github.kamitejp.controlgui;

import static javax.swing.SwingUtilities.invokeLater;
import static org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout;

import java.awt.Container;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import io.github.kamitejp.controlgui.ui.Message;

@Plugin(
  name = "MessageAppender",
  category = Core.CATEGORY_NAME,
  elementType = Appender.ELEMENT_TYPE,
  printObject = true // XXX
)
public final class MessageAppender extends AbstractAppender {
  private static final String MESSAGE_SEGMENT_SEPARATOR = "@@@";

  private static Container targetContainer;

  private MessageAppender(String name, Layout<?> layout, Filter filter, boolean ignoreExceptions) {
    super(name, filter, layout, ignoreExceptions, Property.EMPTY_ARRAY);
  }

  @SuppressWarnings("unused")
  @PluginFactory
  public static MessageAppender createAppender(
    @PluginAttribute("name") String name,
    @PluginAttribute("ignoreExceptions") boolean ignoreExceptions,
    @PluginElement("Layout") Layout<?> layout,
    @PluginElement("Filters") Filter filter
  ) {
    if (name == null) {
      LOGGER.error("No name provided for MessageAppender");
      return null;
    }
    if (layout == null) {
      layout = createDefaultLayout();
    }
    return new MessageAppender(name, layout, filter, ignoreExceptions);
  }

  public static void setTargetContainer(Container container) {
    MessageAppender.targetContainer = container;
  }

  @Override
  public void append(LogEvent event) {
    var message = new String(getLayout().toByteArray(event));

    invokeLater(() -> {
      if (targetContainer == null) {
        return;
      }
      var segs = message.split(MESSAGE_SEGMENT_SEPARATOR);

      if (segs.length != 2) {
        return;
      }
      var timeString = segs[0];
      var content = segs[1];

      var messageType = MessageType.fromLog4jStandardLevel(event.getLevel().getStandardLevel());
      messageType.ifPresent(type -> {
        var messageComponent = new Message(timeString, type, content);
        targetContainer.add(messageComponent);
        targetContainer.revalidate();
      });
    });
  }
}
