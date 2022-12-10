package io.github.kamitejp.status;

import java.util.List;

import io.github.kamitejp.recognition.RecognizerStatus;
import io.github.kamitejp.universalfeature.UnavailableUniversalFeature;

public final class ProgramStatus {
  private final boolean debug;
  private final List<String> profileNames;
  private final SessionTimer sessionTimer;
  private final CharacterCounter characterCounter;
  private final List<UnavailableUniversalFeature> unavailableUniversalFeatures;
  private final RecognizerStatus recognizerStatus;
  private PlayerStatus playerStatus;
  private List<String> subscribedEvents;

  public ProgramStatus(
    boolean debug,
    List<String> profileNames,
    SessionTimer sessionTimer,
    CharacterCounter characterCounter,
    List<UnavailableUniversalFeature> unavailableUniversalFeatures,
    RecognizerStatus.Kind recognizerStatusKind,
    PlayerStatus playerStatus,
    List<String> subscribedEvents
  ) {
    this.debug = debug;
    this.profileNames = profileNames;
    this.sessionTimer = sessionTimer;
    this.characterCounter = characterCounter;
    this.unavailableUniversalFeatures = unavailableUniversalFeatures;
    this.recognizerStatus = new RecognizerStatus(recognizerStatusKind, null);
    this.playerStatus = playerStatus;
    this.subscribedEvents = subscribedEvents;
  }

  public boolean isDebug() {
    return debug;
  }

  public List<String> getProfileNames() {
    return profileNames;
  }

  public SessionTimer getSessionTimer() {
    return sessionTimer;
  }

  public CharacterCounter getCharacterCounter() {
    return characterCounter;
  }

  public List<UnavailableUniversalFeature> getUnavailableUniversalFeatures() {
    return unavailableUniversalFeatures;
  }

  public RecognizerStatus getRecognizerStatus() {
    return recognizerStatus;
  }

  public PlayerStatus getPlayerStatus() {
    return playerStatus;
  }

  public List<String> getSubscribedEvents() {
    return subscribedEvents;
  }

  public void updateRecognizerStatus(RecognizerStatus.Kind kind, List<String> availableCommands) {
    updateRecognizerStatus(kind);
    updateRecognizerStatusAvailableCommands(availableCommands);
  }

  public void updateRecognizerStatus(RecognizerStatus.Kind kind) {
    recognizerStatus.setKind(kind);
  }

  public void updateRecognizerStatusAvailableCommands(List<String> availableCommands) {
    recognizerStatus.setAvailableCommands(availableCommands);
  }

  public void setPlayerStatus(PlayerStatus playerStatus) {
    this.playerStatus = playerStatus;
  }

  public void setSubscribedEvents(List<String> subscribedEvents) {
    this.subscribedEvents = subscribedEvents;
  }
}
