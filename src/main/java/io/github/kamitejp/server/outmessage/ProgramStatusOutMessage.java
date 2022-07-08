package io.github.kamitejp.server.outmessage;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.github.kamitejp.status.ProgramStatus;

public sealed class ProgramStatusOutMessage extends BaseOutMessage
  permits ProgramStatusOutMessage.CharacterCounter,
          ProgramStatusOutMessage.SessionTimer,
          ProgramStatusOutMessage.RecognizerStatus,
          ProgramStatusOutMessage.PlayerStatus,
          ProgramStatusOutMessage.Full {
  private String subKind;

  private ProgramStatusOutMessage(String subKind) {
    super("program-status");
    this.subKind = subKind;
  }

  public String getSubKind() {
    return this.subKind;
  }

  public static final class CharacterCounter extends ProgramStatusOutMessage {
    private io.github.kamitejp.status.CharacterCounter characterCounter;

    public CharacterCounter(ProgramStatus status) {
      super("character-counter");
      characterCounter = status.getCharacterCounter();
    }

    public io.github.kamitejp.status.CharacterCounter getCharacterCounter() {
      return characterCounter;
    }
  }

  public static final class SessionTimer extends ProgramStatusOutMessage {
    private io.github.kamitejp.status.SessionTimer sessionTimer;

    public SessionTimer(ProgramStatus status) {
      super("session-timer");
      sessionTimer = status.getSessionTimer();
    }

    public io.github.kamitejp.status.SessionTimer getSessionTimer() {
      return sessionTimer;
    }
  }

  public static final class RecognizerStatus extends ProgramStatusOutMessage {
    private io.github.kamitejp.recognition.RecognizerStatus recognizerStatus;

    public RecognizerStatus(ProgramStatus status) {
      super("recognizer-status");
      recognizerStatus = status.getRecognizerStatus();
    }

    public io.github.kamitejp.recognition.RecognizerStatus getRecognizerStatus() {
      return recognizerStatus;
    }
  }

  public static final class PlayerStatus extends ProgramStatusOutMessage {
    private io.github.kamitejp.status.PlayerStatus playerStatus;

    public PlayerStatus(ProgramStatus status) {
      super("player-status");
      playerStatus = status.getPlayerStatus();
    }

    public io.github.kamitejp.status.PlayerStatus getPlayerStatus() {
      return playerStatus;
    }
  }

  public static final class Full extends ProgramStatusOutMessage {
    @JsonUnwrapped
    private ProgramStatus status;

    public Full(ProgramStatus status) {
      super("full");
      this.status = status;
    }
  }
}
