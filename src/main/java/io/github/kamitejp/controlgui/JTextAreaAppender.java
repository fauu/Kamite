package io.github.kamitejp.controlgui;

import static javax.swing.SwingUtilities.invokeLater;
import static org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

// https://stackoverflow.com/a/29736246/2498764 
@Plugin(name = "JTextAreaAppender", category = "Core", elementType = "appender", printObject = true)
public final class JTextAreaAppender extends AbstractAppender {
  private static final List<JTextArea> textAreas = new ArrayList<>();
  private final int maxLines;

  private JTextAreaAppender(
    String name,
    Layout<?> layout,
    Filter filter,
    int maxLines,
    boolean ignoreExceptions
  ) {
    super(name, filter, layout, ignoreExceptions, Property.EMPTY_ARRAY);
    this.maxLines = maxLines;
  }

  @SuppressWarnings("unused")
  @PluginFactory
  public static JTextAreaAppender createAppender(
    @PluginAttribute("name") String name,
    @PluginAttribute("maxLines") int maxLines,
    @PluginAttribute("ignoreExceptions") boolean ignoreExceptions,
    @PluginElement("Layout") Layout<?> layout,
    @PluginElement("Filters") Filter filter
  ) {
    if (name == null) {
      LOGGER.error("No name provided for JTextAreaAppender");
      return null;
    }
    if (layout == null) {
      layout = createDefaultLayout();
    }
    return new JTextAreaAppender(name, layout, filter, maxLines, ignoreExceptions);
  }

  // Add the target JTextArea to be populated and updated by the logging information
  public static void addLog4j2TextAreaAppender(final JTextArea textArea) {
    JTextAreaAppender.textAreas.add(textArea);
  }

  @Override
  public void append(LogEvent event) {
    var message = new String(this.getLayout().toByteArray(event));

    // Append formatted message to text area using the Thread
    try {
      invokeLater(() -> {
        for (JTextArea textArea : textAreas) {
          try {
            if (textArea != null) {
              if (textArea.getText().length() == 0) {
                textArea.setText(message);
              } else {
                textArea.append("\n" + message);
                if (maxLines > 0 & textArea.getLineCount() > maxLines + 1) {
                  var doc = textArea.getDocument();
                  var endIdx = doc.getText(0, doc.getLength()).indexOf("\n");
                  doc.remove(0, endIdx + 1);
                }
              }
              var content = textArea.getText();
              textArea.setText(content.substring(0, content.length() - 1));
            }
          } catch (BadLocationException e) {
            LOGGER.debug("Improper TextArea access. See stderr for the stack trace");
            e.printStackTrace();
          }
        }
      });
    } catch (IllegalStateException e) {
      e.printStackTrace();
    }
  }
}
