package io.github.kamitejp.status;

import java.util.List;

import io.github.kamitejp.config.Config;
import io.github.kamitejp.recognition.RecognizerStatus;
import io.github.kamitejp.universalfeature.UnavailableUniversalFeature;

public final class ProgramStatus {
  private final boolean debug;
  private final List<String> profileNames;
  private final List<Config.Lookup.Target> lookupTargets;
  private final SessionTimer sessionTimer;
  private final CharacterCounter characterCounter;
  private final List<UnavailableUniversalFeature> unavailableUniversalFeatures;
  private final RecognizerStatus recognizerStatus;
  private PlayerStatus playerStatus;

  public ProgramStatus(
    boolean debug,
    List<String> profileNames,
    List<Config.Lookup.Target> lookupTargets,
    SessionTimer sessionTimer,
    CharacterCounter characterCounter,
    List<UnavailableUniversalFeature> unavailableUniversalFeatures,
    RecognizerStatus.Kind recognizerStatusKind,
    PlayerStatus playerStatus
  ) {
    this.debug = debug;
    this.profileNames = profileNames;
    this.lookupTargets = lookupTargets;
    this.sessionTimer = sessionTimer;
    this.characterCounter = characterCounter;
    this.unavailableUniversalFeatures = unavailableUniversalFeatures;
    this.recognizerStatus = new RecognizerStatus(recognizerStatusKind, null);
    this.playerStatus = playerStatus;
  }

  public boolean isDebug() {
    return debug;
  }

  public List<String> getProfileNames() {
    return profileNames;
  }

  public List<Config.Lookup.Target> getLookupTargets() {
    return lookupTargets;
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

  public PlayerStatus getPlayerStatus() {
    return playerStatus;
  }

  public void setPlayerStatus(PlayerStatus playerStatus) {
    this.playerStatus = playerStatus;
  }
}
