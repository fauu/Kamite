package io.github.kamitejp.server.outmessage;

import io.github.kamitejp.server.UserNotificationKind;

public class UserNotificationOutMessage extends BaseOutMessage {
  private final UserNotificationKind userNotificationKind;
  private final String content;

  public UserNotificationOutMessage(UserNotificationKind userNotificationKind, String content) {
    super("user-notification");
    this.userNotificationKind = userNotificationKind;
    this.content = content;
  }

  public UserNotificationKind getUserNotificationKind() {
    return this.userNotificationKind;
  }

  public String getContent() {
    return this.content;
  }
}
