#include "TextractorExtension.h"
#include "HTTPRequest.h"
#include "common.h"
#include "json.hpp"
#include "KamiteSend.h"

#include <fstream>

constexpr auto CONFIG_FILENAME = "KamiteSend.txt";

std::string host = "localhost:4110";

BOOL WINAPI DllMain(HMODULE hModule, DWORD ul_reason_for_call, LPVOID lpReserved) {
  LoadConfig();
	return TRUE;
}

void LoadConfig() {
  std::ifstream config_file(CONFIG_FILENAME);
  if (config_file.is_open()) {
    std::string line;

    std::getline(config_file, line);
    if (line.rfind("host=") == 0 & line.length() > 5) {
      host = line.substr(5);
    }

    config_file.close();
  }
}

bool ProcessSentence(std::wstring& sentence, SentenceInfo sentenceInfo) {
	if (!sentenceInfo["current select"]) return false;
	try {
		http::Request request{ "http://" + host + "/cmd/chunk/show" };
		request.send(
      "POST",
      "params={\"chunk\": \"" + escape_string(WideStringToString(sentence)) + "\"}",
      { "Content-Type: application/x-www-form-urlencoded" }
    );
	} catch (const std::exception& e) {}
	return false;
}
