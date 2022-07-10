#pragma once

#include "TextractorExtension.h"

void LoadConfig();

bool ProcessSentence(std::wstring& sentence, SentenceInfo sentenceInfo);
