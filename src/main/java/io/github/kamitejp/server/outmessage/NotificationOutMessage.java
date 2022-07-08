package io.github.kamitejp.server.outmessage;

import io.github.kamitejp.server.NotificationKind;

public class NotificationOutMessage extends BaseOutMessage {
  private final NotificationKind notificationKind;
  private final String content;

  public NotificationOutMessage(NotificationKind notificationKind, String content) {
    super("notification");
    this.notificationKind = notificationKind;
    this.content = content;
  }

  public NotificationKind getNotificationKind() {
    return this.notificationKind;
  }

  public String getContent() {
    return this.content;
  }
}
