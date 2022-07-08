package io.github.kamitejp.platform;

public sealed interface MangaOCREvent
  permits MangaOCREvent.Started,
          MangaOCREvent.StartedDownloadingModel,
          MangaOCREvent.TimedOutAndRestarting,
          MangaOCREvent.Crashed {
  record Started() implements MangaOCREvent {}
  record StartedDownloadingModel() implements MangaOCREvent {}
  record TimedOutAndRestarting() implements MangaOCREvent {}
  record Crashed() implements MangaOCREvent {}
}
